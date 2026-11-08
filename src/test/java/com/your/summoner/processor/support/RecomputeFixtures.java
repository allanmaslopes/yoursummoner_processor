package com.your.summoner.processor.support;

import com.your.summoner.processor.application.port.out.KnowledgeStore.Decision;
import com.your.summoner.processor.application.port.out.KnowledgeStore.Snapshot;
import com.your.summoner.processor.application.service.ParticipantExtractor.DecisionSample;
import com.your.summoner.processor.domain.model.ChampionKnowledge;
import com.your.summoner.processor.domain.model.ItemCatalog;
import com.your.summoner.processor.domain.model.ItemInfo;
import com.your.summoner.processor.domain.model.MatchRecord;
import com.your.summoner.processor.domain.model.MatchRecord.*;
import com.your.summoner.processor.domain.model.PatchVersion;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RecomputeFixtures {
    public static final PatchVersion PATCH = new PatchVersion(16, 22);

    private RecomputeFixtures() {}

    public static ItemCatalog catalog() {
        return new ItemCatalog(List.of(
                new ItemInfo(1055, 450, false, false, List.of(), List.of()),
                new ItemInfo(2003, 50, false, true, List.of(), List.of()),
                new ItemInfo(3031, 3400, false, false, List.of(), List.of())));
    }

    public static MatchRecord match(boolean win) {
        return new MatchRecord(
                List.of(new Participant(1, 100, 1, "TOP", win, List.of(3031))),
                List.of(new TimelineEvent(30, 1, EventType.BUY, 1055, null, 0),
                        new TimelineEvent(40, 1, EventType.BUY, 2003, null, 0),
                        new TimelineEvent(600, 1, EventType.BUY, 3031, null, 1)),
                List.of(new Frame(1, List.of(new SlotState(1, 200, 2000, 2, 400, 10)), 1, 0),
                        new Frame(10, List.of(new SlotState(1, 1000, 6000, 8, 3000, 80)), 3, 2)));
    }

    public static Snapshot snapshot(PatchVersion patch, int championId) {
        var entry = new ChampionKnowledge(patch.toString(), championId, "TOP", 30, 20,
                List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), List.of(),
                "R > Q > W > E", List.of(), false, List.of(), List.of(), List.of(), null);
        var decision = new Decision(championId, "TOP", new DecisionSample(10, 3031, 500, 1, 3, 2, true));
        return new Snapshot(patch, Instant.parse("2026-11-07T12:00:00Z"), 30, 2, 25,
                List.of(entry), List.of(decision));
    }
}
