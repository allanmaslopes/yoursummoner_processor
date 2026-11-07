package com.your.summoner.processor.infrastructure.adapter.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.your.summoner.processor.application.port.out.KnowledgeStore.Decision;
import com.your.summoner.processor.domain.model.ChampionKnowledge;
import com.your.summoner.processor.domain.model.PatchVersion;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class MongoKnowledgeStorePublicReadTest {
    @Test
    void publicReadNeverLoadsDecisionCollection() {
        var mongo = mock(MongoTemplate.class);
        var patch = new PatchVersion(16, 22);
        var metadata = new Document("activeCollection", "knowledge_16.22_v1")
                .append("activeDecisionCollection", "decision_samples_16.22_v1")
                .append("computedAt", "2026-11-07T12:00:00Z")
                .append("matches", 42L).append("ignoredBySchema", 3L).append("durationMillis", 125L);
        when(mongo.findById("16.22", Document.class, "knowledge_meta")).thenReturn(metadata);
        when(mongo.findAll(ChampionKnowledge.class, "knowledge_16.22_v1")).thenReturn(List.of());

        var result = new MongoKnowledgeStore(mongo).findPublished(patch).orElseThrow();

        assertThat(result.patch()).isEqualTo(patch);
        assertThat(result.decisions()).isEmpty();
        verify(mongo, times(2)).findById("16.22", Document.class, "knowledge_meta");
        verify(mongo).findAll(ChampionKnowledge.class, "knowledge_16.22_v1");
        verify(mongo, never()).findAll(eq(Decision.class), anyString());
    }
}
