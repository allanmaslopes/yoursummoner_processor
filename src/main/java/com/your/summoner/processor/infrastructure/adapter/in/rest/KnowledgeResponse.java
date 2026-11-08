package com.your.summoner.processor.infrastructure.adapter.in.rest;

import com.your.summoner.processor.domain.model.ChampionKnowledge;
import com.your.summoner.processor.domain.model.FullBuildPathStat;
import com.your.summoner.processor.domain.model.ItemStat;
import com.your.summoner.processor.domain.model.KeystoneStat;
import com.your.summoner.processor.domain.model.MatchRecord;
import com.your.summoner.processor.domain.model.PrimaryRecommendation;
import com.your.summoner.processor.domain.model.RunePageStat;
import com.your.summoner.processor.domain.model.SequenceStat;
import com.your.summoner.processor.domain.model.SpellPairStat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record KnowledgeResponse(String patch, int championId, String position, int games, int wins,
                                BigDecimal winRate, List<SequenceResponse> starting,
                                List<SequenceResponse> boots, List<SequenceResponse> buildPaths,
                                List<FullBuildPathResponse> fullBuildPaths,
                                List<SequenceResponse> purchaseTree,
                                Map<Integer, List<SequenceResponse>> componentsBefore,
                                List<SequenceResponse> skillOrders, String skillPriority,
                                List<ItemResponse> itemWinRates, boolean lowSample,
                                List<KeystoneResponse> keystones, List<RunePageResponse> runePages,
                                List<SpellPairResponse> summonerSpells, PrimaryResponse primary) {
    static KnowledgeResponse from(ChampionKnowledge value) {
        var components = value.componentsBefore().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> sequences(entry.getValue())));
        return new KnowledgeResponse(value.patch(), value.championId(), value.position(), value.games(), value.wins(),
                rate(value.wins(), value.games()), sequences(value.starting()), sequences(value.boots()),
                sequences(value.buildPaths()),
                value.fullBuildPaths().stream().map(FullBuildPathResponse::from).toList(),
                sequences(value.purchaseTree()), components,
                sequences(value.skillOrders()), value.skillPriority(),
                value.itemWinRates().stream().map(ItemResponse::from).toList(), value.lowSample(),
                value.keystones().stream().map(KeystoneResponse::from).toList(),
                value.runePages().stream().map(RunePageResponse::from).toList(),
                value.summonerSpells().stream().map(SpellPairResponse::from).toList(),
                value.primary() == null ? null : PrimaryResponse.from(value.primary()));
    }

    private static List<SequenceResponse> sequences(List<SequenceStat> values) {
        return values.stream().map(SequenceResponse::from).toList();
    }

    private static BigDecimal rate(int wins, int games) {
        return games == 0 ? BigDecimal.ZERO.setScale(4)
                : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(games), 4, RoundingMode.HALF_UP);
    }

    public record SequenceResponse(List<Integer> sequence, int games, int wins, BigDecimal winRate) {
        static SequenceResponse from(SequenceStat value) {
            return new SequenceResponse(value.sequence(), value.games(), value.wins(), rate(value.wins(), value.games()));
        }
    }

    public record FullBuildPathResponse(List<Integer> sequence, int games, int wins,
                                        BigDecimal winRate, boolean lowSample) {
        static FullBuildPathResponse from(FullBuildPathStat value) {
            return new FullBuildPathResponse(value.sequence(), value.games(), value.wins(),
                    rate(value.wins(), value.games()), value.lowSample());
        }
    }

    public record ItemResponse(int itemId, int games, int wins, BigDecimal winRate) {
        static ItemResponse from(ItemStat value) {
            return new ItemResponse(value.itemId(), value.games(), value.wins(), rate(value.wins(), value.games()));
        }
    }

    public record KeystoneResponse(int perk, int games, int wins, BigDecimal winRate, boolean lowSample) {
        static KeystoneResponse from(KeystoneStat value) {
            return new KeystoneResponse(value.perk(), value.games(), value.wins(),
                    rate(value.wins(), value.games()), value.lowSample());
        }
    }

    public record StatPerksResponse(int offense, int flex, int defense) {
        static StatPerksResponse from(MatchRecord.StatPerks value) {
            return value == null ? null : new StatPerksResponse(value.offense(), value.flex(), value.defense());
        }
    }

    public record RunePageResponse(int keystone, int primaryStyle, int subStyle, List<Integer> perks,
                                   StatPerksResponse statPerks, int games, int wins, BigDecimal winRate,
                                   boolean lowSample, SequenceResponse buildPath, boolean buildPathLowSample,
                                   FullBuildPathResponse fullBuildPath) {
        static RunePageResponse from(RunePageStat value) {
            return new RunePageResponse(value.keystone(), value.primaryStyle(), value.subStyle(), value.perks(),
                    StatPerksResponse.from(value.statPerks()), value.games(), value.wins(),
                    rate(value.wins(), value.games()), value.lowSample(),
                    value.buildPath() == null ? null : SequenceResponse.from(value.buildPath()),
                    value.buildPathLowSample(), value.fullBuildPath() == null
                            ? null : FullBuildPathResponse.from(value.fullBuildPath()));
        }
    }

    public record SpellPairResponse(List<Integer> sequence, int games, int wins, BigDecimal winRate,
                                    boolean lowSample) {
        static SpellPairResponse from(SpellPairStat value) {
            return new SpellPairResponse(value.spells(), value.games(), value.wins(),
                    rate(value.wins(), value.games()), value.lowSample());
        }
    }

    public record PrimaryResponse(int keystone, List<Integer> summonerSpells, boolean summonerSpellsLowSample) {
        static PrimaryResponse from(PrimaryRecommendation value) {
            return new PrimaryResponse(value.keystone(), value.summonerSpells(), value.summonerSpellsLowSample());
        }
    }
}
