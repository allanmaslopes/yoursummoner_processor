package com.your.summoner.processor.domain.model;

import java.util.List;

public record PrimaryRecommendation(int keystone, List<Integer> summonerSpells,
                                    boolean summonerSpellsLowSample) {
    public PrimaryRecommendation {
        summonerSpells = List.copyOf(summonerSpells);
    }
}
