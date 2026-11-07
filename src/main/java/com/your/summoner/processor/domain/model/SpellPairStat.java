package com.your.summoner.processor.domain.model;

import java.util.List;

public record SpellPairStat(List<Integer> spells, int games, int wins, boolean lowSample) {
    public SpellPairStat {
        spells = List.copyOf(spells);
    }
}
