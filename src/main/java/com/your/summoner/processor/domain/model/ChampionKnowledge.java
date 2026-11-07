package com.your.summoner.processor.domain.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record ChampionKnowledge(String patch, int championId, String position,
                                int games, int wins, List<SequenceStat> starting,
                                List<SequenceStat> boots, List<SequenceStat> buildPaths,
                                List<SequenceStat> purchaseTree,
                                Map<Integer, List<SequenceStat>> componentsBefore,
                                List<SequenceStat> skillOrders, String skillPriority,
                                List<ItemStat> itemWinRates, boolean lowSample,
                                List<KeystoneStat> keystones, List<RunePageStat> runePages,
                                List<SpellPairStat> summonerSpells, PrimaryRecommendation primary) {
    public ChampionKnowledge {
        starting = List.copyOf(starting);
        boots = List.copyOf(boots);
        buildPaths = List.copyOf(buildPaths);
        purchaseTree = List.copyOf(purchaseTree);
        var components = new TreeMap<Integer, List<SequenceStat>>();
        componentsBefore.forEach((item, sequences) -> components.put(item, List.copyOf(sequences)));
        componentsBefore = Collections.unmodifiableMap(components);
        skillOrders = List.copyOf(skillOrders);
        itemWinRates = List.copyOf(itemWinRates);
        keystones = List.copyOf(keystones);
        runePages = List.copyOf(runePages);
        summonerSpells = List.copyOf(summonerSpells);
    }

    public ChampionKnowledge(String patch, int championId, String position,
                             int games, int wins, List<SequenceStat> starting,
                             List<SequenceStat> boots, List<SequenceStat> buildPaths,
                             List<SequenceStat> purchaseTree,
                             Map<Integer, List<SequenceStat>> componentsBefore,
                             List<SequenceStat> skillOrders, String skillPriority,
                             List<ItemStat> itemWinRates) {
        this(patch, championId, position, games, wins, starting, boots, buildPaths, purchaseTree,
                componentsBefore, skillOrders, skillPriority, itemWinRates,
                false, List.of(), List.of(), List.of(), null);
    }
}
