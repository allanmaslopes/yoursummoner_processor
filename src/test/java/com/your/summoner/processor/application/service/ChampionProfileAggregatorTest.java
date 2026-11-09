package com.your.summoner.processor.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.your.summoner.processor.domain.model.ChampionProfile;
import com.your.summoner.processor.domain.model.MatchRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChampionProfileAggregatorTest {

    @Test
    void aggregatesMatchesIncrementally() {
        var aggregator = new ChampionProfileAggregator(Map.of());
        var accumulator = aggregator.accumulator();

        accumulator.add(new MatchRecord(List.of(new MatchRecord.Participant(1, 100, 62, "TOP", true, List.of(),
                List.of(), null, 100, 50, 50, 12, 200)), List.of(), List.of()));
        accumulator.add(new MatchRecord(List.of(new MatchRecord.Participant(1, 100, 62, "TOP", false, List.of(),
                List.of(), null, 100, 50, 50, null, null)), List.of(), List.of()));

        assertEquals(List.of(new ChampionProfile(62, 2, 0.5000, 0.2500, 0.2500,
                12.0, 200.0, List.of())), accumulator.profiles());
    }

    @Test
    void calculatesSharesAveragesFromPresentFieldsAndMergesManualTags() {
        var profiles = new ChampionProfileAggregator(Map.of(
                62, new ChampionProfileAggregator.Correction(List.of("cura-propria"), List.of())))
                .aggregate(List.of(
                        new MatchRecord(List.of(new MatchRecord.Participant(1, 100, 62, "TOP", true, List.of(),
                                List.of(), null, 100, 50, 50, 12, 200)), List.of(), List.of()),
                        new MatchRecord(List.of(new MatchRecord.Participant(1, 100, 62, "TOP", false, List.of(),
                                List.of(), null, 100, 50, 50, null, null)), List.of(), List.of())));

        assertEquals(List.of(new ChampionProfile(62, 2, 0.5000, 0.2500, 0.2500,
                12.0, 200.0, List.of("cura-propria"))), profiles);
    }
}
