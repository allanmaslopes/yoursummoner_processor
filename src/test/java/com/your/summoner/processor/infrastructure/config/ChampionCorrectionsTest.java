package com.your.summoner.processor.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChampionCorrectionsTest {
    @Test void correctionsArePackagedOnTheClasspath() {
        assertThat(ChampionCorrections.class.getResourceAsStream("/correcoes-campeoes.json")).isNotNull();
    }
}
