package com.your.summoner.processor.application.service;

import com.your.summoner.processor.application.port.out.KnowledgeStore;
import com.your.summoner.processor.application.port.out.MatchSource;
import com.your.summoner.processor.application.service.ParticipantExtractor.DecisionSample;
import com.your.summoner.processor.domain.model.MatchRecord;
import com.your.summoner.processor.domain.model.PatchVersion;
import com.your.summoner.processor.domain.model.SequenceStat;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import static com.your.summoner.processor.support.RecomputeFixtures.*;
import static org.assertj.core.api.Assertions.*;

class RecomputeServiceTest {
    @Test void recomputeIsIdempotent() {
        var source = new Source();
        source.matches.put(PATCH, List.of(match(true), match(false)));
        var store = new MemoryStore();
        var service = new RecomputeService(source, patch -> 3, patch -> catalog(), store, 1, 1, 5);

        service.recompute();
        var first = store.find(PATCH).orElseThrow();
        service.recompute();
        var second = store.find(PATCH).orElseThrow();

        assertThat(first.matches()).isEqualTo(2);
        assertThat(first.ignoredBySchema()).isEqualTo(3);
        assertThat(first.durationMillis()).isGreaterThanOrEqualTo(0);
        assertThat(first.computedAt()).isNotNull();
        assertThat(first.entries()).hasSize(2).allSatisfy(entry -> {
            assertThat(entry.patch()).isEqualTo("16.22");
            assertThat(entry.championId()).isEqualTo(1);
            assertThat(entry.games()).isEqualTo(2);
            assertThat(entry.wins()).isEqualTo(1);
            assertThat(entry.starting()).containsExactly(new SequenceStat(List.of(1055, 2003), 2, 1));
            assertThat(entry.buildPaths()).containsExactly(new SequenceStat(List.of(3031), 2, 1));
        });
        assertThat(first.entries()).extracting(entry -> entry.position()).containsExactly("ANY", "TOP");
        assertThat(first.decisions()).containsExactly(
                new KnowledgeStore.Decision(1, "TOP", new DecisionSample(1, 1055, 2000, 0, 1, 0, true)),
                new KnowledgeStore.Decision(1, "TOP", new DecisionSample(10, 3031, 6000, 0, 3, 2, true)),
                new KnowledgeStore.Decision(1, "TOP", new DecisionSample(1, 1055, 2000, 0, 1, 0, false)),
                new KnowledgeStore.Decision(1, "TOP", new DecisionSample(10, 3031, 6000, 0, 3, 2, false)));
        assertThat(second.entries()).isEqualTo(first.entries());
        assertThat(second.decisions()).isEqualTo(first.decisions());
        assertThat(second.matches()).isEqualTo(2);
        assertThat(second.ignoredBySchema()).isEqualTo(3);
        assertThat(store.patches()).containsExactly(PATCH);
        assertThat(source.opened).isEqualTo(2);
        assertThat(source.closed).isEqualTo(2);
    }

    @Test void patchesOutsideSourceAreRemoved() {
        var source = new Source();
        source.matches.put(PATCH, List.of(match(true)));
        var expired = new PatchVersion(16, 21);
        var store = new MemoryStore();
        store.publish(snapshot(expired, 2));
        store.publish(snapshot(PATCH, 99));
        var service = new RecomputeService(source, patch -> 0, patch -> catalog(), store, 1, 1, 5);

        service.recompute();

        assertThat(store.find(expired)).isEmpty();
        assertThat(store.patches()).containsExactly(PATCH);
        assertThat(store.find(PATCH).orElseThrow().entries()).extracting(entry -> entry.championId())
                .containsOnly(1);
        assertThat(source.closed).isEqualTo(1);
    }

    @Test void emptySourceKeepsPublishedPatchesAndWarns() {
        var source = new Source();
        var store = new MemoryStore();
        var published = snapshot(PATCH, 99);
        store.publish(published);
        var service = new RecomputeService(source, patch -> 0, patch -> catalog(), store, 1, 1, 5);
        var logger = (Logger) LoggerFactory.getLogger(RecomputeService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.recompute();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(store.find(PATCH)).contains(published);
        assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains("16.22"));
    }

    @Test void closesMatchStreamAndKeepsPublishedPackageWhenReadingFails() {
        var source = new Source();
        source.matches.put(PATCH, List.of(match(true)));
        source.failDuringRead = true;
        var store = new MemoryStore();
        var previous = snapshot(PATCH, 99);
        store.publish(previous);
        var service = new RecomputeService(source, patch -> 0, patch -> catalog(), store, 1, 1, 5);

        assertThatThrownBy(service::recompute).isInstanceOf(IllegalStateException.class);

        assertThat(source.opened).isEqualTo(1);
        assertThat(source.closed).isEqualTo(1);
        assertThat(store.find(PATCH)).contains(previous);
    }

    @Test void failureInOnePatchDoesNotStopOthers() {
        var source = new Source();
        var broken = new PatchVersion(16, 21);
        source.matches.put(broken, List.of(match(true)));
        source.matches.put(PATCH, List.of(match(true)));
        source.failingPatch = broken;
        var expired = new PatchVersion(16, 20);
        var store = new MemoryStore();
        store.publish(snapshot(expired, 2));
        var service = new RecomputeService(source, patch -> 0, patch -> catalog(), store, 1, 1, 5);

        assertThatThrownBy(service::recompute).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16.21").hasMessageContaining("IllegalArgumentException");

        assertThat(store.find(PATCH)).isPresent();
        assertThat(store.find(broken)).isEmpty();
        assertThat(store.find(expired)).isEmpty();
    }

    private static final class Source implements MatchSource {
        PatchVersion failingPatch;
        final Map<PatchVersion, List<MatchRecord>> matches = new LinkedHashMap<>();
        int opened;
        int closed;
        boolean failDuringRead;

        @Override public Stream<MatchRecord> byPatch(PatchVersion patch) {
            if (patch.equals(failingPatch)) throw new IllegalArgumentException("broken patch");
            opened++;
            var result = matches.get(patch).stream();
            if (failDuringRead) {
                result = Stream.concat(result, Stream.<MatchRecord>generate(() -> {
                    throw new IllegalStateException("source cursor failed");
                }).limit(1));
            }
            return result.onClose(() -> closed++);
        }

        @Override public List<PatchVersion> patches() { return List.copyOf(matches.keySet()); }
    }

    private static final class MemoryStore implements KnowledgeStore {
        final Map<PatchVersion, Snapshot> snapshots = new LinkedHashMap<>();

        @Override public void publish(Snapshot snapshot) { snapshots.put(snapshot.patch(), snapshot); }
        @Override public Optional<Snapshot> find(PatchVersion patch) { return Optional.ofNullable(snapshots.get(patch)); }
        @Override public List<PatchVersion> patches() { return List.copyOf(snapshots.keySet()); }
        @Override public void remove(PatchVersion patch) { snapshots.remove(patch); }
    }
}
