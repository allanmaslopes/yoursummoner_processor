package com.your.summoner.processor.infrastructure.adapter.in.rest;

import com.your.summoner.processor.domain.model.ChampionKnowledge;
import com.your.summoner.processor.domain.model.ItemStat;
import com.your.summoner.processor.domain.model.SequenceStat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record KnowledgeResponse(String patch, int championId, String position, int games, int wins,
                                BigDecimal winRate, List<SequenceResponse> starting,
                                List<SequenceResponse> boots, List<SequenceResponse> buildPaths,
                                List<SequenceResponse> purchaseTree,
                                Map<Integer, List<SequenceResponse>> componentsBefore,
                                List<SequenceResponse> skillOrders, String skillPriority,
                                List<ItemResponse> itemWinRates) {
    static KnowledgeResponse from(ChampionKnowledge value) {
        var components = value.componentsBefore().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> sequences(entry.getValue())));
        return new KnowledgeResponse(value.patch(), value.championId(), value.position(), value.games(), value.wins(),
                rate(value.wins(), value.games()), sequences(value.starting()), sequences(value.boots()),
                sequences(value.buildPaths()), sequences(value.purchaseTree()), components,
                sequences(value.skillOrders()), value.skillPriority(),
                value.itemWinRates().stream().map(ItemResponse::from).toList());
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

    public record ItemResponse(int itemId, int games, int wins, BigDecimal winRate) {
        static ItemResponse from(ItemStat value) {
            return new ItemResponse(value.itemId(), value.games(), value.wins(), rate(value.wins(), value.games()));
        }
    }
}
