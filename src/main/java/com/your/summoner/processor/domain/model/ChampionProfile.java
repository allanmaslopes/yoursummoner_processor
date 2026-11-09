package com.your.summoner.processor.domain.model;

import java.util.List;

/** Perfil observado por campeão no patch, complementado por correções versionadas. */
public record ChampionProfile(int championId, int games, double physicalShare, double magicShare,
                              double trueShare, Double ccSecondsPerGame, Double healPerGame,
                              List<String> tags) {
    public ChampionProfile {
        tags = tags == null ? List.of() : tags.stream().sorted().toList();
    }
}
