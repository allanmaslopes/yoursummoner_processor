package com.your.summoner.processor.infrastructure.adapter.out.collector;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.your.summoner.processor.application.port.out.MatchSource;
import com.your.summoner.processor.domain.model.MatchRecord;
import com.your.summoner.processor.domain.model.MatchRecord.*;
import com.your.summoner.processor.domain.model.PatchVersion;
import jakarta.annotation.PreDestroy;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.ArrayList;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Sorts.ascending;

@Component
public final class MongoMatchSource implements MatchSource, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MongoMatchSource.class);
    private static final List<String> FIELDS = List.of(
            "participants.slot", "participants.team", "participants.championId", "participants.position",
            "participants.win", "participants.items", "events.t", "events.slot", "events.type",
            "events.itemId", "events.skillSlot", "events.frame", "frames.minute", "frames.blueKills",
            "frames.redKills", "frames.slots.slot", "frames.slots.gold", "frames.slots.totalGold",
            "frames.slots.level", "frames.slots.xp", "frames.slots.cs");
    private final MongoCollection<Document> collection;
    private final MongoClient ownedClient;

    @Autowired
    public MongoMatchSource(@Value("${processor.source.uri}") String uri) {
        var connection = new ConnectionString(uri);
        if (connection.getDatabase() == null) throw new IllegalArgumentException("Collector URI must specify a database");
        ownedClient = MongoClients.create(connection);
        collection = ownedClient.getDatabase(connection.getDatabase()).getCollection("matches");
    }

    public MongoMatchSource(MongoCollection<Document> collection) {
        this.collection = collection;
        this.ownedClient = null;
    }

    @Override public Stream<MatchRecord> byPatch(PatchVersion patch) {
        rejectPlayerIdentifiers(patch);
        var cursor = collection.find(and(eq("patch", patch.toString()), eq("schemaVersion", 2)))
                .projection(fields(include(FIELDS), excludeId()))
                .sort(ascending("collectedAt", "_id")).batchSize(500).iterator();
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(cursor, Spliterator.ORDERED), false)
                .map(MongoMatchSource::map).onClose(cursor::close);
    }

    private void rejectPlayerIdentifiers(PatchVersion patch) {
        var identifiers = new ArrayList<org.bson.conversions.Bson>();
        for (String field : List.of("puuid", "riotId", "summonerName", "summonerId", "gameName", "tagLine")) {
            identifiers.add(exists(field));
            identifiers.add(exists("participants." + field));
        }
        // Verifica somente a presença das chaves, sem carregar valores identificadores.
        var invalid = collection.find(and(eq("patch", patch.toString()), eq("schemaVersion", 2), or(identifiers)))
                .projection(fields(include("schemaVersion"), excludeId())).limit(1).first();
        if (invalid != null) throw new IllegalStateException("Collector document contains player identifier fields");
    }

    @Override public List<PatchVersion> patches() {
        var patches = new TreeSet<PatchVersion>();
        try (var cursor = collection.aggregate(List.of(new Document("$match", new Document("schemaVersion", 2)),
                new Document("$group", new Document("_id", "$patch")))).batchSize(500).iterator()) {
            while (cursor.hasNext()) PatchVersion.parse(cursor.next().getString("_id")).ifPresent(patches::add);
        }
        return List.copyOf(patches);
    }

    private static MatchRecord map(Document document) {
        var participants = documents(document, "participants").stream().map(p -> new Participant(
                number(p, "slot"), number(p, "team"), number(p, "championId"), p.getString("position") == null ? "" : p.getString("position"),
                p.getBoolean("win", false), p.getList("items", Integer.class, List.of()))).toList();
        var events = documents(document, "events").stream().filter(MongoMatchSource::hasEventType)
                .map(e -> new TimelineEvent(number(e, "t"), number(e, "slot"),
                EventType.valueOf(e.getString("type")), e.getInteger("itemId"), e.getInteger("skillSlot"), e.getInteger("frame"))).toList();
        var frames = documents(document, "frames").stream().map(f -> new Frame(number(f, "minute"),
                documents(f, "slots").stream().map(s -> new SlotState(number(s, "slot"), number(s, "gold"),
                        number(s, "totalGold"), number(s, "level"), number(s, "xp"), number(s, "cs"))).toList(),
                number(f, "blueKills"), number(f, "redKills"))).toList();
        return new MatchRecord(participants, events, frames);
    }

    private static boolean hasEventType(Document event) {
        if (event.getString("type") != null) return true;
        LOG.warn("Ignoring collector event without type");
        return false;
    }

    private static List<Document> documents(Document document, String field) { return document.getList(field, Document.class, List.of()); }
    private static int number(Document document, String field) { return document.getInteger(field, 0); }

    @PreDestroy
    @Override public void close() { if (ownedClient != null) ownedClient.close(); }
}
