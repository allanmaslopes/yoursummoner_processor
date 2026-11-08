package com.your.summoner.processor.domain.model;

import java.util.List;

public record FullBuildPathStat(List<Integer> sequence, int games, int wins, boolean lowSample) {
    public FullBuildPathStat {
        sequence = List.copyOf(sequence);
    }
}
