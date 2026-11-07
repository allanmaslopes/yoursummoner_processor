package com.your.summoner.processor.application.service;

import com.your.summoner.processor.application.port.out.ItemCatalogSource;
import com.your.summoner.processor.application.port.out.KnowledgeStore;
import com.your.summoner.processor.application.port.out.KnowledgeStore.Decision;
import com.your.summoner.processor.application.port.out.KnowledgeStore.Snapshot;
import com.your.summoner.processor.application.port.out.MatchSource;
import com.your.summoner.processor.application.port.out.MatchSourceMetrics;
import com.your.summoner.processor.application.service.ParticipantExtractor.ParticipantView;
import com.your.summoner.processor.domain.model.PatchVersion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RecomputeService {
    private static final Logger LOG = LoggerFactory.getLogger(RecomputeService.class);
    private final MatchSource source;
    private final MatchSourceMetrics metrics;
    private final ItemCatalogSource catalogs;
    private final KnowledgeStore store;
    private final int minGames;
    private final int minOccurrences;
    private final int topN;
    private final ParticipantExtractor extractor = new ParticipantExtractor();
    private final KnowledgeAggregator aggregator = new KnowledgeAggregator();

    public RecomputeService(MatchSource source, MatchSourceMetrics metrics, ItemCatalogSource catalogs,
                            KnowledgeStore store, int minGames, int minOccurrences, int topN) {
        if (minGames < 1 || minOccurrences < 1 || topN < 1) {
            throw new IllegalArgumentException("Publication thresholds must be positive");
        }
        this.source = source;
        this.metrics = metrics;
        this.catalogs = catalogs;
        this.store = store;
        this.minGames = minGames;
        this.minOccurrences = minOccurrences;
        this.topN = topN;
    }

    public synchronized void recompute() {
        var patches = source.patches();
        var failures = new ArrayList<String>();
        var suppressed = new ArrayList<RuntimeException>();
        for (var patch : patches) {
            try {
                recompute(patch);
            } catch (RuntimeException failure) {
                LOG.warn("Recompute of patch {} failed: {}", patch, failure.toString());
                failures.add(patch + " (" + failure.getClass().getSimpleName() + ")");
                suppressed.add(failure);
            }
        }
        var retained = new HashSet<>(patches);
        for (var patch : store.patches()) {
            if (!retained.contains(patch)) store.remove(patch);
        }
        if (!failures.isEmpty()) {
            var aggregate = new IllegalStateException("Recompute failed for patches: " + String.join(", ", failures));
            suppressed.forEach(aggregate::addSuppressed);
            throw aggregate;
        }
    }

    private void recompute(PatchVersion patch) {
        long started = System.nanoTime();
        var catalog = catalogs.forPatch(patch);
        var views = new ArrayList<ParticipantView>();
        var decisions = new ArrayList<Decision>();
        long matches = 0;
        try (var stream = source.byPatch(patch)) {
            var iterator = stream.iterator();
            while (iterator.hasNext()) {
                var extracted = extractor.extract(iterator.next(), catalog);
                matches++;
                views.addAll(extracted);
                for (var view : extracted) {
                    for (var sample : view.decisions()) {
                        decisions.add(new Decision(view.championId(), view.position(), sample));
                    }
                }
            }
        }
        var entries = aggregator.aggregate(patch.toString(), views.stream(), minGames, minOccurrences, topN);
        long ignored = metrics.ignoredBySchema(patch);
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        store.publish(new Snapshot(patch, Instant.now(), matches, ignored, duration, entries, decisions));
    }
}
