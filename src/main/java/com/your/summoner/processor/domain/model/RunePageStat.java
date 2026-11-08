package com.your.summoner.processor.domain.model;

import java.util.List;

public record RunePageStat(int keystone, int primaryStyle, int subStyle, List<Integer> perks,
                           MatchRecord.StatPerks statPerks, int games, int wins, boolean lowSample,
                           SequenceStat buildPath, boolean buildPathLowSample,
                           FullBuildPathStat fullBuildPath) {
    public RunePageStat {
        perks = List.copyOf(perks);
    }
}
