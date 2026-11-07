package com.your.summoner.processor.infrastructure.adapter.out.collector;

import com.your.summoner.processor.domain.model.MatchRecord.EventType;
import com.your.summoner.processor.domain.model.PatchVersion;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MongoMatchSourceTest {
    @Autowired MongoTemplate mongo;
    private MongoMatchSource source;
    private final PatchVersion patch = new PatchVersion(16, 22);

    @BeforeEach void setup() {
        mongo.dropCollection("matches");
        source = new MongoMatchSource(mongo.getCollection("matches"));
    }

    @Test void onlySchemaV2IsRead() throws Exception {
        var collection = mongo.getCollection("matches");
        collection.insertMany(List.of(match("b", "16.22", 2, 20), match("a", "16.22", 2, 20),
                match("old", "16.22", 1, 10), match("future", "16.22", 3, 10),
                match("other", "16.23", 2, 10), match("early", "16.22", 2, 5)));
        try (var matches = source.byPatch(patch)) {
            var read = matches.toList();
            assertThat(read).extracting(m -> m.participants().getFirst().championId()).containsExactly(5, 20, 20);
            assertThat(read.getFirst().participants()).hasSize(10);
            assertThat(read.getFirst().frames()).hasSize(5);
            assertThat(read.getFirst().frames().getFirst().slots()).hasSize(10);
            assertThat(read.getFirst().events()).extracting(e -> e.type())
                    .contains(EventType.BUY, EventType.UNDO, EventType.SELL, EventType.SKILL);
            assertThat(read.getFirst().events().stream().filter(e -> e.type() == EventType.UNDO).findFirst().orElseThrow().frame()).isNull();
        }
        assertThat(source.patches()).containsExactly(new PatchVersion(16, 22), new PatchVersion(16, 23));
    }

    @Test void sourceNeverWrites() throws Exception {
        var collection = mongo.getCollection("matches");
        collection.insertMany(List.of(match("a", "16.22", 2, 1), match("old", "16.22", 1, 2)));
        var before = collection.find().into(new java.util.ArrayList<>());
        long countBefore = collection.countDocuments();
        try (var matches = source.byPatch(patch)) { assertThat(matches.count()).isEqualTo(1); }
        assertThat(source.patches()).containsExactly(patch);
        assertThat(collection.countDocuments()).isEqualTo(countBefore);
        assertThat(collection.find().into(new java.util.ArrayList<>())).isEqualTo(before);
    }

    private Document match(String id, String patch, int schema, int order) throws Exception {
        String json;
        try (var input = getClass().getResourceAsStream("/fixtures/match-v2.json")) {
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var document = Document.parse(json).append("_id", id).append("patch", patch)
                .append("schemaVersion", schema).append("collectedAt", new Date(order));
        document.getList("participants", Document.class).getFirst().put("championId", order);
        return document;
    }
}
