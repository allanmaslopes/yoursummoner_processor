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
                                List<ItemStat> itemWinRates, Boolean lowSample,
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
        lowSample = lowSample != null && lowSample;
        // v1 documents already stored in Mongo lack the v2 lists; read them as empty.
        keystones = keystones == null ? List.of() : List.copyOf(keystones);
        runePages = runePages == null ? List.of() : List.copyOf(runePages);
        summonerSpells = summonerSpells == null ? List.of() : List.copyOf(summonerSpells);
    }
}
