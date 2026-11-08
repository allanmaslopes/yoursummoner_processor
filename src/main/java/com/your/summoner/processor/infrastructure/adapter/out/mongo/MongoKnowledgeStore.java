package com.your.summoner.processor.infrastructure.adapter.out.mongo;

import com.your.summoner.processor.application.port.out.KnowledgeStore;
import com.your.summoner.processor.domain.model.ChampionKnowledge;
import com.your.summoner.processor.domain.model.PatchVersion;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Component
public final class MongoKnowledgeStore implements KnowledgeStore {
    private static final Logger LOG = LoggerFactory.getLogger(MongoKnowledgeStore.class);
    private static final String META = "knowledge_meta";
    private final MongoTemplate mongo;

    public MongoKnowledgeStore(MongoTemplate mongo) { this.mongo = mongo; }

    @Override public void publish(Snapshot snapshot) {
        String version = UUID.randomUUID().toString().replace("-", "");
        String knowledge = "knowledge_" + snapshot.patch() + "_" + version;
        String decisions = "decision_samples_" + snapshot.patch() + "_" + version;
        try {
            mongo.createCollection(knowledge);
            mongo.createCollection(decisions);
            if (!snapshot.entries().isEmpty()) mongo.insert(snapshot.entries(), knowledge);
            if (!snapshot.decisions().isEmpty()) mongo.insert(snapshot.decisions(), decisions);
        } catch (RuntimeException failure) {
            discardStaging(knowledge, decisions, failure);
            throw failure;
        }
        var update = new Update()
                .set("activeCollection", knowledge)
                .set("activeDecisionCollection", decisions)
                .set("computedAt", snapshot.computedAt().toString())
                .set("matches", snapshot.matches())
                .set("ignoredBySchema", snapshot.ignoredBySchema())
                .set("durationMillis", snapshot.durationMillis())
                .set("formatVersion", 2);
        // A failed swap can have reached Mongo; retain staging rather than risk dropping active data.
        var previous = mongo.findAndModify(patchQuery(snapshot.patch()), update,
                FindAndModifyOptions.options().upsert(true).returnNew(false), Document.class, META);
        dropCollections(previous);
    }

    @Override public Optional<Snapshot> find(PatchVersion patch) {
        return read(patch, true);
    }

    @Override public Optional<Snapshot> findPublished(PatchVersion patch) {
        return read(patch, false);
    }

    @Override public Optional<Summary> summary(PatchVersion patch) {
        return consistent(patch, meta -> new Summary(patch, Instant.parse(meta.getString("computedAt")),
                number(meta, "matches"), number(meta, "ignoredBySchema"), number(meta, "durationMillis"),
                (int) mongo.getCollection(meta.getString("activeCollection")).countDocuments()));
    }

    @Override public Optional<ChampionKnowledge> findEntry(PatchVersion patch, int championId, String position) {
        var query = Query.query(Criteria.where("championId").is(championId).and("position").is(position));
        return consistent(patch, meta -> mongo.findOne(query, ChampionKnowledge.class, meta.getString("activeCollection")));
    }

    private Optional<Snapshot> read(PatchVersion patch, boolean includeDecisions) {
        return consistent(patch, before -> {
            var entries = mongo.findAll(ChampionKnowledge.class, before.getString("activeCollection"));
            var decisions = includeDecisions
                    ? mongo.findAll(Decision.class, before.getString("activeDecisionCollection")) : List.<Decision>of();
            return new Snapshot(patch, Instant.parse(before.getString("computedAt")),
                    number(before, "matches"), number(before, "ignoredBySchema"),
                    number(before, "durationMillis"), entries, decisions);
        });
    }

    /** Runs the read against the active metadata and retries if a concurrent swap changed it meanwhile. */
    private <T> Optional<T> consistent(PatchVersion patch, Function<Document, T> reader) {
        while (true) {
            var before = metadata(patch);
            if (before == null) return Optional.empty();
            try {
                var result = reader.apply(before);
                // A concurrent swap may already have dropped one of the collections just read.
                if (!Objects.equals(before, metadata(patch))) continue;
                return Optional.ofNullable(result);
            } catch (RuntimeException failure) {
                if (!Objects.equals(before, metadata(patch))) continue;
                throw failure;
            }
        }
    }

    /** Drops knowledge_* / decision_samples_* collections no metadata points to (leftovers of a crash mid-publish). */
    @PostConstruct void dropOrphans() {
        var referenced = new HashSet<String>();
        for (var meta : mongo.findAll(Document.class, META)) {
            referenced.add(meta.getString("activeCollection"));
            referenced.add(meta.getString("activeDecisionCollection"));
        }
        for (var name : mongo.getCollectionNames()) {
            boolean ours = name.startsWith("knowledge_") || name.startsWith("decision_samples_");
            if (!ours || name.equals(META) || referenced.contains(name)) continue;
            try {
                mongo.dropCollection(name);
                LOG.warn("Dropped orphan collection {}", name);
            } catch (RuntimeException failure) {
                LOG.warn("Could not drop orphan collection {}: {}", name, failure.toString());
            }
        }
    }

    @Override public List<PatchVersion> patches() {
        return mongo.findAll(Document.class, META).stream()
                .flatMap(meta -> PatchVersion.parse(meta.getString("_id")).stream()).sorted().toList();
    }

    @Override public void remove(PatchVersion patch) {
        var previous = mongo.findAndRemove(patchQuery(patch), Document.class, META);
        dropCollections(previous);
    }

    private Document metadata(PatchVersion patch) {
        return mongo.findById(patch.toString(), Document.class, META);
    }

    private void dropCollections(Document metadata) {
        if (metadata == null) return;
        // Best effort: the swap or removal already succeeded, so a failed drop only leaves an orphan collection.
        for (var field : List.of("activeCollection", "activeDecisionCollection")) {
            try {
                mongo.dropCollection(metadata.getString(field));
            } catch (RuntimeException failure) {
                LOG.warn("Could not drop old collection {}: {}", metadata.getString(field), failure.toString());
            }
        }
    }

    private void discardStaging(String knowledge, String decisions, RuntimeException failure) {
        for (var collection : List.of(knowledge, decisions)) {
            try {
                mongo.dropCollection(collection);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private static Query patchQuery(PatchVersion patch) {
        return Query.query(Criteria.where("_id").is(patch.toString()));
    }

    private static long number(Document document, String field) {
        return ((Number) document.get(field)).longValue();
    }
}
