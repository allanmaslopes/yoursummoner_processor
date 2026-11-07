package com.your.summoner.processor.infrastructure.adapter.out.collector;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.your.summoner.processor.domain.model.MatchRecord;
import com.your.summoner.processor.domain.model.PatchVersion;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MongoMatchSourceUnitTest {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void patchDiscoveryDoesNotFilterBySchema() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        AggregateIterable<Document> aggregate = mock(AggregateIterable.class, RETURNS_SELF);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(collection.aggregate(anyList())).thenReturn(aggregate);
        when(aggregate.iterator()).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(new Document("_id", "16.23"));
        var pipeline = ArgumentCaptor.forClass(List.class);

        assertThat(new MongoMatchSource(collection).patches()).containsExactly(new PatchVersion(16, 23));

        verify(collection).aggregate(pipeline.capture());
        List<Bson> stages = pipeline.getValue();
        assertThat(stages).allSatisfy(stage -> assertThat(stage
                .toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson())
                .doesNotContain("schemaVersion"));
        verify(cursor).close();
    }

    @SuppressWarnings("unchecked")
    @Test void ignoresEventsWithoutTypeAndKeepsRemainingEvents() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> found = mock(FindIterable.class, RETURNS_SELF);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(collection.find(any(Bson.class))).thenReturn(found);
        when(found.iterator()).thenReturn(cursor);
        var document = new Document("events", List.of(
                new Document("t", 5).append("slot", 1).append("type", "BUY").append("itemId", 1001),
                new Document("t", 6).append("slot", 1).append("itemId", 1036),
                new Document("t", 7).append("slot", 1).append("type", "SELL").append("itemId", 2003)));
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(document);

        try (var matches = new MongoMatchSource(collection).byPatch(new PatchVersion(16, 22))) {
            var events = matches.findFirst().orElseThrow().events();
            assertThat(events).extracting(event -> event.type().name()).containsExactly("BUY", "SELL");
            assertThat(events).extracting(event -> event.itemId()).containsExactly(1001, 2003);
        }
    }

    @SuppressWarnings("unchecked")
    @Test void rejectsDocumentsWithPlayerIdentifiers() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> found = mock(FindIterable.class, RETURNS_SELF);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(collection.find(any(Bson.class))).thenReturn(found);
        when(found.iterator()).thenReturn(cursor);
        // A consulta de presença retorna somente o schema, sem obter valores dos identificadores.
        when(found.first()).thenReturn(new Document("schemaVersion", 2));
        var source = new MongoMatchSource(collection);

        assertThatThrownBy(() -> source.byPatch(new PatchVersion(16, 22)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Collector document contains player identifier fields");
        var query = ArgumentCaptor.forClass(Bson.class);
        verify(collection).find(query.capture());
        assertThat(query.getValue().toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson())
                .contains("\"patch\": \"16.22\"", "\"schemaVersion\": 2", "\"$exists\": true",
                        "participants.puuid", "participants.gameName", "participants.tagLine", "participants.summonerId");
        verify(found, never()).iterator();
    }

    @SuppressWarnings("unchecked")
    @Test void mapsOnlyAnonymousFieldsAndClosesCursor() throws Exception {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> found = mock(FindIterable.class, RETURNS_SELF);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(collection.find(any(Bson.class))).thenReturn(found);
        when(found.iterator()).thenReturn(cursor);
        String json;
        try (var input = getClass().getResourceAsStream("/fixtures/match-v2.json")) {
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var document = Document.parse(json);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(document);

        try (var matches = new MongoMatchSource(collection).byPatch(new PatchVersion(16, 22))) {
            var match = matches.findFirst().orElseThrow();
            assertThat(match.participants()).hasSize(10);
            assertThat(match.participants().getFirst().items()).containsExactly(3006, 3031);
            assertThat(match.frames()).hasSize(5);
            assertThat(match.frames().get(2).slots().getFirst().totalGold()).isEqualTo(1800);
            assertThat(match.events().get(1).frame()).isNull();
        }
        verify(cursor).close();
        verify(found).batchSize(500);
        var sort = ArgumentCaptor.forClass(Bson.class);
        verify(found).sort(sort.capture());
        assertThat(sort.getValue().toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson())
                .isEqualTo("{\"collectedAt\": 1, \"_id\": 1}");
    }

    @SuppressWarnings("unchecked")
    @Test void mapsRunesAndSpells() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> found = mock(FindIterable.class, RETURNS_SELF);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(collection.find(any(Bson.class))).thenReturn(found);
        when(found.iterator()).thenReturn(cursor);
        var runes = new Document("primaryStyle", 8000)
                .append("subStyle", 8400)
                .append("perks", List.of(8010, 9111, 9104, 8299, 8444, 8242))
                .append("statPerks", new Document("offense", 5005).append("flex", 5008).append("defense", 5011));
        var document = new Document("participants", List.of(new Document("slot", 1)
                .append("team", 100).append("championId", 266).append("win", true)
                .append("summonerSpells", List.of(4, 12)).append("runes", runes)));
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(document);

        try (var matches = new MongoMatchSource(collection).byPatch(new PatchVersion(16, 22))) {
            var participant = matches.findFirst().orElseThrow().participants().getFirst();
            assertThat(participant.summonerSpells()).containsExactly(4, 12);
            assertThat(participant.runes().primaryStyle()).isEqualTo(8000);
            assertThat(participant.runes().subStyle()).isEqualTo(8400);
            assertThat(participant.runes().perks()).containsExactly(8010, 9111, 9104, 8299, 8444, 8242);
            assertThat(participant.runes().statPerks()).isEqualTo(new MatchRecord.StatPerks(5005, 5008, 5011));
        }
    }

    @SuppressWarnings("unchecked")
    @Test void oldDocumentWithoutStatPerks() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> found = mock(FindIterable.class, RETURNS_SELF);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(collection.find(any(Bson.class))).thenReturn(found);
        when(found.iterator()).thenReturn(cursor);
        var document = new Document("participants", List.of(new Document("slot", 1)
                .append("team", 100).append("championId", 266)
                .append("runes", new Document("primaryStyle", 8000).append("subStyle", 8400).append("perks", List.of(8010))),
                new Document("slot", 2).append("team", 100).append("championId", 103)));
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(document);

        try (var matches = new MongoMatchSource(collection).byPatch(new PatchVersion(16, 22))) {
            var participants = matches.findFirst().orElseThrow().participants();
            assertThat(participants.getFirst().runes().statPerks()).isNull();
            assertThat(participants.get(1).runes()).isEqualTo(new MatchRecord.Runes(0, 0, List.of(), null));
        }
    }
}
