package com.your.summoner.processor.infrastructure.adapter.out.mongo;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import com.your.summoner.processor.application.port.out.KnowledgeStore.Decision;
import com.your.summoner.processor.application.port.out.KnowledgeStore.Snapshot;
import com.your.summoner.processor.application.service.ParticipantExtractor.DecisionSample;
import com.your.summoner.processor.application.service.RecomputeService;
import com.your.summoner.processor.domain.model.ChampionKnowledge;
import com.your.summoner.processor.domain.model.FullBuildPathStat;
import com.your.summoner.processor.domain.model.KeystoneStat;
import com.your.summoner.processor.domain.model.MatchRecord;
import com.your.summoner.processor.domain.model.PatchVersion;
import com.your.summoner.processor.domain.model.PrimaryRecommendation;
import com.your.summoner.processor.domain.model.RunePageStat;
import com.your.summoner.processor.domain.model.SequenceStat;
import com.your.summoner.processor.domain.model.SpellPairStat;
import com.your.summoner.processor.infrastructure.adapter.out.collector.MongoMatchSource;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static com.mongodb.client.model.Filters.eq;
import static com.your.summoner.processor.support.RecomputeFixtures.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MongoKnowledgeStoreTest {
    @Autowired MongoClient embeddedClient;
    private MongoClient client;
    private MongoTemplate mongo;
    private MongoKnowledgeStore store;
    private final SwapGate gate = new SwapGate();
    private String sourceDatabase;

    @BeforeEach void setup() {
        var address = embeddedClient.getClusterDescription().getServerDescriptions().iterator().next().getAddress();
        client = MongoClients.create(MongoClientSettings.builder()
                .applyToClusterSettings(settings -> settings.hosts(List.of(address)))
                .addCommandListener(gate).build());
        String database = "processor_task5_" + UUID.randomUUID().toString().replace("-", "");
        mongo = new MongoTemplate(client, database);
        sourceDatabase = database + "_collector";
        store = new MongoKnowledgeStore(mongo);
    }

    @AfterEach void cleanup() {
        gate.release.countDown();
        gate.readRelease.countDown();
        if (client != null) {
            if (mongo != null) mongo.getDb().drop();
            if (sourceDatabase != null) client.getDatabase(sourceDatabase).drop();
            client.close();
        }
    }

    @Test void readersSeeOldPackageUntilSwap() throws Exception {
        var oldPackage = snapshot(PATCH, 1);
        var first = snapshot(PATCH, 2);
        var second = snapshot(PATCH, 3);
        var newPackage = new Snapshot(PATCH, first.computedAt(), 60, 4, 50,
                List.of(first.entries().getFirst(), second.entries().getFirst()),
                List.of(first.decisions().getFirst(), second.decisions().getFirst()));
        store.publish(oldPackage);
        var oldMeta = metadata();
        gate.armed = true;
        var publication = new FutureTask<Void>(() -> { store.publish(newPackage); return null; });
        var writer = Thread.ofVirtual().start(publication);
        try {
            assertThat(gate.reached.await(10, TimeUnit.SECONDS)).as("publication reaches atomic metadata swap").isTrue();
            assertThat(metadata()).isEqualTo(oldMeta);
            for (int read = 0; read < 3; read++) {
                assertThat(store.find(PATCH)).contains(oldPackage);
            }
            assertThat(versionedCollections("knowledge_")).hasSize(2);
            assertThat(versionedCollections("decision_samples_")).hasSize(2);
            var stagedKnowledge = versionedCollections("knowledge_").stream()
                    .filter(name -> !name.equals(oldMeta.getString("activeCollection"))).findFirst().orElseThrow();
            var stagedDecisions = versionedCollections("decision_samples_").stream()
                    .filter(name -> !name.equals(oldMeta.getString("activeDecisionCollection"))).findFirst().orElseThrow();
            assertThat(mongo.getCollection(stagedKnowledge).countDocuments()).isEqualTo(2);
            assertThat(mongo.getCollection(stagedDecisions).countDocuments()).isEqualTo(2);
        } finally {
            gate.release.countDown();
            writer.join(TimeUnit.SECONDS.toMillis(10));
        }
        publication.get(10, TimeUnit.SECONDS);
        assertThat(store.find(PATCH)).contains(newPackage);
        assertThat(metadata().getString("activeCollection")).isNotEqualTo(oldMeta.getString("activeCollection"));
        assertThat(metadata().getString("activeDecisionCollection"))
                .isNotEqualTo(oldMeta.getString("activeDecisionCollection"));
    }

    @Test void v2RoundTrip() {
        var sequence = new SequenceStat(List.of(3153, 3078, 3053), 2, 1);
        var fullBuild = new FullBuildPathStat(List.of(3153, 3078, 3053, 3031, 3089), 2, 1, true);
        var perks = List.of(8010, 9111, 9104, 8299, 8444, 8242);
        var full = new ChampionKnowledge(PATCH.toString(), 1, "TOP", 5, 3,
                List.of(), List.of(), List.of(), List.of(fullBuild), List.of(), Map.of(), List.of(), "R > Q > W > E", List.of(),
                true, List.of(new KeystoneStat(8010, 5, 3, true)),
                List.of(new RunePageStat(8010, 8000, 8400, perks, new MatchRecord.StatPerks(5005, 5008, 5011),
                                5, 3, true, sequence, true, fullBuild),
                        new RunePageStat(8437, 8400, 8000, perks, null, 1, 0, true, null, false, null)),
                List.of(new SpellPairStat(List.of(4, 12), 5, 3, true)),
                new PrimaryRecommendation(8010, List.of(4, 12), true));
        var empty = new ChampionKnowledge(PATCH.toString(), 2, "TOP", 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), List.of(), "R > Q > W > E", List.of(),
                false, List.of(), List.of(), List.of(), null);
        var noSpells = new ChampionKnowledge(PATCH.toString(), 3, "TOP", 1, 1,
                List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), List.of(), "R > Q > W > E", List.of(),
                true, List.of(new KeystoneStat(8437, 1, 1, true)), List.of(), List.of(),
                new PrimaryRecommendation(8437, List.of(), true));
        var base = snapshot(PATCH, 1);
        var snapshot = new Snapshot(PATCH, base.computedAt(), 5, 0, 10, List.of(full, empty, noSpells),
                base.decisions());

        store.publish(snapshot);

        assertThat(store.find(PATCH)).contains(snapshot);
        assertThat(metadata().get("formatVersion")).isEqualTo(2);
    }

    @Test void roundTripKeepsComponentsBeforeSequencesAndDecisions() {
        var sequence = new SequenceStat(List.of(1055, 2003), 4, 3);
        var other = new SequenceStat(List.of(3031), 2, 1);
        var entry = new ChampionKnowledge(PATCH.toString(), 5, "MID", 6, 4,
                List.of(sequence), List.of(other), List.of(sequence, other), List.of(), List.of(sequence),
                Map.of(3031, List.of(sequence, other), 3153, List.of(other)), List.of(sequence),
                "R > Q > W > E", List.of(), false, List.of(), List.of(), List.of(), null);
        var decisions = List.of(
                new Decision(5, "MID", new DecisionSample(10, 3031, 500, 1, 3, 2, true)),
                new Decision(5, "MID", new DecisionSample(1, 1055, 2000, 0, 1, 0, false)));
        var expected = new Snapshot(PATCH, snapshot(PATCH, 1).computedAt(), 6, 1, 12, List.of(entry), decisions);

        store.publish(expected);

        var result = store.find(PATCH).orElseThrow();
        assertThat(result).isEqualTo(expected);
        assertThat(result.entries().getFirst().componentsBefore()).containsOnlyKeys(3031, 3153);
        assertThat(result.decisions()).isEqualTo(decisions);
    }

    @Test void orphanCollectionsAreDroppedAtStartup() {
        store.publish(snapshot(PATCH, 1));
        var meta = metadata();
        for (var orphan : List.of("knowledge_16.20_dead", "decision_samples_16.20_dead")) mongo.createCollection(orphan);
        mongo.createCollection("unrelated");

        store.dropOrphans();

        assertThat(mongo.getCollectionNames()).contains("unrelated", "knowledge_meta",
                meta.getString("activeCollection"), meta.getString("activeDecisionCollection"))
                .doesNotContain("knowledge_16.20_dead", "decision_samples_16.20_dead");
        assertThat(store.find(PATCH)).contains(snapshot(PATCH, 1));
    }

    @Test void summaryAndSingleEntryReadWithoutLoadingThePackage() {
        var published = snapshot(PATCH, 7);
        store.publish(published);

        var summary = store.summary(PATCH).orElseThrow();
        assertThat(summary.computedAt()).isEqualTo(published.computedAt());
        assertThat(summary.matches()).isEqualTo(30);
        assertThat(summary.ignoredBySchema()).isEqualTo(2);
        assertThat(summary.durationMillis()).isEqualTo(25);
        assertThat(summary.entries()).isEqualTo(1);
        assertThat(store.findEntry(PATCH, 7, "TOP")).contains(published.entries().getFirst());
        assertThat(store.findEntry(PATCH, 7, "MID")).isEmpty();
        assertThat(store.findEntry(PATCH, 8, "TOP")).isEmpty();
        assertThat(store.summary(new PatchVersion(1, 1))).isEmpty();
    }

    @Test void readsV1DocumentWithoutV2Fields() {
        var base = snapshot(PATCH, 1);
        store.publish(new Snapshot(PATCH, base.computedAt(), 5, 0, 10, List.of(), List.of()));
        mongo.getCollection(metadata().getString("activeCollection")).insertOne(new Document()
                .append("patch", PATCH.toString()).append("championId", 7).append("position", "TOP")
                .append("games", 30).append("wins", 20)
                .append("starting", List.of()).append("boots", List.of()).append("buildPaths", List.of())
                .append("purchaseTree", List.of()).append("componentsBefore", new Document())
                .append("skillOrders", List.of()).append("skillPriority", "R > Q > W > E")
                .append("itemWinRates", List.of()));

        var entry = store.find(PATCH).orElseThrow().entries().getFirst();

        assertThat(entry.championId()).isEqualTo(7);
        assertThat(entry.keystones()).isEmpty();
        assertThat(entry.runePages()).isEmpty();
        assertThat(entry.summonerSpells()).isEmpty();
        assertThat(entry.primary()).isNull();
        assertThat(entry.lowSample()).isFalse();
    }

    @Test void oldDocumentWithoutFullBuildStillReads() {
        var base = snapshot(PATCH, 1);
        store.publish(new Snapshot(PATCH, base.computedAt(), 5, 0, 10, List.of(), List.of()));
        mongo.getCollection(metadata().getString("activeCollection")).insertOne(new Document()
                .append("patch", PATCH.toString()).append("championId", 8).append("position", "TOP")
                .append("games", 5).append("wins", 3)
                .append("starting", List.of()).append("boots", List.of()).append("buildPaths", List.of())
                .append("purchaseTree", List.of()).append("componentsBefore", new Document())
                .append("skillOrders", List.of()).append("skillPriority", "R > Q > W > E")
                .append("itemWinRates", List.of()).append("lowSample", true)
                .append("keystones", List.of(new Document("perk", 8010).append("games", 5)
                        .append("wins", 3).append("lowSample", true)))
                .append("runePages", List.of(new Document("keystone", 8010)
                        .append("primaryStyle", 8000).append("subStyle", 8400)
                        .append("perks", List.of(8010, 9111, 9104, 8299, 8444, 8242))
                        .append("games", 5).append("wins", 3).append("lowSample", true)
                        .append("buildPath", null).append("buildPathLowSample", false)))
                .append("summonerSpells", List.of()).append("primary", null));

        var entry = store.find(PATCH).orElseThrow().entries().getFirst();

        assertThat(entry.fullBuildPaths()).isEmpty();
        assertThat(entry.runePages()).singleElement().satisfies(page -> assertThat(page.fullBuildPath()).isNull());
    }

    @Test void oldCollectionsAreDropped() {
        store.publish(snapshot(PATCH, 1));
        var oldMeta = metadata();
        String oldKnowledge = oldMeta.getString("activeCollection");
        String oldDecisions = oldMeta.getString("activeDecisionCollection");
        assertThat(oldKnowledge).startsWith("knowledge_");
        assertThat(oldDecisions).startsWith("decision_samples_");
        assertThat(mongo.collectionExists(oldKnowledge)).isTrue();
        assertThat(mongo.collectionExists(oldDecisions)).isTrue();

        var replacement = snapshot(PATCH, 2);
        store.publish(replacement);

        assertThat(store.find(PATCH)).contains(replacement);
        assertThat(mongo.collectionExists(oldKnowledge)).isFalse();
        assertThat(mongo.collectionExists(oldDecisions)).isFalse();
        assertThat(versionedCollections("knowledge_")).containsExactly(metadata().getString("activeCollection"));
        assertThat(versionedCollections("decision_samples_"))
                .containsExactly(metadata().getString("activeDecisionCollection"));
        assertThat(mongo.getCollection("knowledge_meta").countDocuments()).isEqualTo(1);
    }

    @Test void readerRetriesWhenSwapDropsCollectionsAfterReadingOldMetadata() throws Exception {
        var oldPackage = snapshot(PATCH, 1);
        var newPackage = snapshot(PATCH, 2);
        store.publish(oldPackage);
        var oldMeta = metadata();
        gate.pauseRead = true;
        var read = new FutureTask<Optional<Snapshot>>(() -> store.find(PATCH));
        var reader = Thread.ofVirtual().start(read);
        try {
            assertThat(gate.readReached.await(10, TimeUnit.SECONDS))
                    .as("reader obtains old metadata before publication").isTrue();
            store.publish(newPackage);
            assertThat(mongo.collectionExists(oldMeta.getString("activeCollection"))).isFalse();
            assertThat(mongo.collectionExists(oldMeta.getString("activeDecisionCollection"))).isFalse();
            assertThat(read.isDone()).isFalse();
        } finally {
            gate.readRelease.countDown();
            reader.join(TimeUnit.SECONDS.toMillis(10));
        }

        var result = read.get(10, TimeUnit.SECONDS).orElseThrow();
        assertThat(result).isEqualTo(newPackage);
        assertThat(result.entries()).extracting(entry -> entry.championId()).containsExactly(2);
        assertThat(result.decisions()).extracting(decision -> decision.championId()).containsExactly(2);
    }

    @Test void dropFailureAfterSwapKeepsNewPackage() {
        store.publish(snapshot(PATCH, 1));
        var failing = new MongoTemplate(client, mongo.getDb().getName()) {
            @Override public void dropCollection(String name) { throw new IllegalStateException("drop failed"); }
        };
        var newPackage = snapshot(PATCH, 2);

        assertThatCode(() -> new MongoKnowledgeStore(failing).publish(newPackage)).doesNotThrowAnyException();

        assertThat(store.find(PATCH)).contains(newPackage);
        assertThatCode(() -> new MongoKnowledgeStore(failing).remove(PATCH)).doesNotThrowAnyException();
        assertThat(store.find(PATCH)).isEmpty();
    }

    @Test void removingPatchDropsMetadataAndBothCollections() {
        store.publish(snapshot(PATCH, 1));

        store.remove(PATCH);

        assertThat(store.find(PATCH)).isEmpty();
        assertThat(store.patches()).isEmpty();
        assertThat(versionedCollections("knowledge_")).isEmpty();
        assertThat(versionedCollections("decision_samples_")).isEmpty();
        assertThat(mongo.getCollection("knowledge_meta").countDocuments()).isZero();
    }

    @Test void recomputeNeverWritesCollector() throws Exception {
        String json;
        try (var input = getClass().getResourceAsStream("/fixtures/match-v2.json")) {
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var matches = client.getDatabase(sourceDatabase).getCollection("matches");
        matches.insertMany(List.of(
                Document.parse(json).append("_id", "v2").append("patch", PATCH.toString()).append("schemaVersion", 2),
                Document.parse(json).append("_id", "v1").append("patch", PATCH.toString()).append("schemaVersion", 1)));
        var before = matches.find().sort(new Document("_id", 1)).into(new ArrayList<>());
        try (var source = new MongoMatchSource(matches)) {
            var service = new RecomputeService(source, source, patch -> catalog(), store, 1, 1, 5);
            service.recompute();
            service.recompute();
        }

        assertThat(store.find(PATCH).orElseThrow().matches()).isEqualTo(1);
        assertThat(store.find(PATCH).orElseThrow().ignoredBySchema()).isEqualTo(1);
        assertThat(matches.countDocuments()).isEqualTo(2);
        assertThat(matches.find().sort(new Document("_id", 1)).into(new ArrayList<>())).isEqualTo(before);
        assertThat(client.getDatabase(sourceDatabase).listCollectionNames().into(new ArrayList<>()))
                .containsExactly("matches");
    }

    @Test void patchWithOnlyUnsupportedSchemaPublishesIgnoredCount() {
        var unsupportedPatch = new PatchVersion(16, 23);
        var matches = client.getDatabase(sourceDatabase).getCollection("matches");
        matches.insertMany(List.of(
                new Document("_id", "old").append("patch", unsupportedPatch.toString()).append("schemaVersion", 1),
                new Document("_id", "future").append("patch", unsupportedPatch.toString()).append("schemaVersion", 3)));
        var before = matches.find().sort(new Document("_id", 1)).into(new ArrayList<>());
        store.publish(snapshot(unsupportedPatch, 99));

        try (var source = new MongoMatchSource(matches)) {
            new RecomputeService(source, source, patch -> catalog(), store, 1, 1, 5).recompute();
        }

        var result = store.find(unsupportedPatch).orElseThrow();
        assertThat(store.patches()).containsExactly(unsupportedPatch);
        assertThat(result.matches()).isZero();
        assertThat(result.ignoredBySchema()).isEqualTo(2);
        assertThat(result.entries()).isEmpty();
        assertThat(result.decisions()).isEmpty();
        assertThat(matches.find().sort(new Document("_id", 1)).into(new ArrayList<>())).isEqualTo(before);
    }

    private Document metadata() {
        var meta = mongo.getCollection("knowledge_meta").find(eq("_id", PATCH.toString())).first();
        assertThat(meta).isNotNull();
        return meta;
    }

    private List<String> versionedCollections(String prefix) {
        return mongo.getCollectionNames().stream()
                .filter(name -> name.startsWith(prefix) && !name.equals("knowledge_meta")).sorted().toList();
    }

    private static final class SwapGate implements CommandListener {
        final CountDownLatch reached = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch readReached = new CountDownLatch(1);
        final CountDownLatch readRelease = new CountDownLatch(1);
        volatile boolean armed;
        volatile boolean pauseRead;

        @Override public void commandStarted(CommandStartedEvent event) {
            if (!armed || !"findAndModify".equals(event.getCommandName())
                    || !"knowledge_meta".equals(event.getCommand().getString("findAndModify").getValue())) return;
            armed = false;
            reached.countDown();
            try {
                if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("metadata swap was not released");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("metadata swap interrupted", exception);
            }
        }

        @Override public void commandSucceeded(CommandSucceededEvent event) {
            if (!pauseRead || !"find".equals(event.getCommandName())) return;
            var cursor = event.getResponse().getDocument("cursor", null);
            if (cursor == null || !cursor.containsKey("firstBatch")) return;
            var batch = cursor.getArray("firstBatch");
            if (batch.isEmpty() || !batch.get(0).asDocument().containsKey("activeCollection")) return;
            pauseRead = false;
            readReached.countDown();
            try {
                if (!readRelease.await(15, TimeUnit.SECONDS)) throw new AssertionError("metadata reader was not released");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("metadata reader interrupted", exception);
            }
        }
    }
}
