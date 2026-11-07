package com.your.summoner.processor.domain.model;

import java.util.List;

public record SequenceStat(List<Integer> sequence, int games, int wins) {
    public SequenceStat {
        sequence = List.copyOf(sequence);
    }
}
