package com.your.summoner.processor.application.service;

import com.your.summoner.processor.application.service.ParticipantExtractor.ParticipantView;
import com.your.summoner.processor.domain.model.ChampionKnowledge;
import com.your.summoner.processor.domain.model.FullBuildPathStat;
import com.your.summoner.processor.domain.model.ItemStat;
import com.your.summoner.processor.domain.model.KeystoneStat;
import com.your.summoner.processor.domain.model.MatchRecord;
import com.your.summoner.processor.domain.model.PrimaryRecommendation;
import com.your.summoner.processor.domain.model.RunePageStat;
import com.your.summoner.processor.domain.model.SequenceStat;
import com.your.summoner.processor.domain.model.SpellPairStat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

public final class KnowledgeAggregator {
    private static final int GOLD_CURVE_STEP_MINUTES = 5;
    private static final int GOLD_CURVE_MAX_MINUTE = 40;
    private static final int GOLD_CURVE_MIN_GAMES = 10;
    private static final Comparator<SequenceStat> SEQUENCE_ORDER =
            Comparator.comparingInt(SequenceStat::games).reversed()
                    .thenComparing(Comparator.comparingDouble(
                            (SequenceStat stat) -> (double) stat.wins() / stat.games()).reversed())
                    .thenComparing(SequenceStat::sequence, KnowledgeAggregator::compareSequences);

    public List<ChampionKnowledge> aggregate(String patch, Stream<ParticipantView> views,
                                             int minGames, int minOccurrences, int topN) {
        var groups = new HashMap<Group, List<ParticipantView>>();
        views.forEach(view -> {
            groups.computeIfAbsent(new Group(view.championId(), "ANY"), ignored -> new ArrayList<>()).add(view);
            if (view.position() != null && !view.position().isBlank() && !"ANY".equals(view.position())) {
                groups.computeIfAbsent(new Group(view.championId(), view.position()), ignored -> new ArrayList<>()).add(view);
            }
        });
        return groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Group::championId)
                        .thenComparing(Group::position)))
                .map(entry -> knowledge(patch, entry.getKey(), entry.getValue(), minGames, minOccurrences, topN))
                .toList();
    }

    private ChampionKnowledge knowledge(String patch, Group group, List<ParticipantView> views,
                                        int minGames, int minOccurrences, int topN) {
        var allSkills = sequences(views, view -> view.skillOrder().size() == 9 ? view.skillOrder() : List.of(),
                1, Integer.MAX_VALUE);
        var skills = allSkills.stream().filter(stat -> stat.games() >= minOccurrences).limit(topN).toList();
        var components = new TreeMap<Integer, Map<List<Integer>, Counts>>();
        var items = new HashMap<Integer, Counts>();
        for (var view : views) {
            int from = 0;
            for (int completed : view.completedOrder()) {
                int end = from;
                while (end < view.treeOrder().size() && view.treeOrder().get(end) != completed) end++;
                if (end == view.treeOrder().size()) break;
                if (end > from) {
                    var sequence = List.copyOf(view.treeOrder().subList(from, end));
                    components.computeIfAbsent(completed, ignored -> new HashMap<>())
                            .computeIfAbsent(sequence, ignored -> new Counts()).add(view.win());
                }
                from = end + 1;
            }
            view.finalCompleted().stream().distinct()
                    .forEach(item -> items.computeIfAbsent(item, ignored -> new Counts()).add(view.win()));
        }
        var componentsBefore = new TreeMap<Integer, List<SequenceStat>>();
        components.forEach((item, counts) -> {
            var ranked = rank(counts, minOccurrences, topN);
            if (!ranked.isEmpty()) componentsBefore.put(item, ranked);
        });
        var itemWinRates = items.entrySet().stream()
                .map(entry -> new ItemStat(entry.getKey(), entry.getValue().games, entry.getValue().wins))
                .sorted(Comparator.comparingInt(ItemStat::games).reversed()
                        .thenComparing(Comparator.comparingDouble(
                                (ItemStat stat) -> (double) stat.wins() / stat.games()).reversed())
                        .thenComparingInt(ItemStat::itemId))
                .toList();
        var starting = sequences(views, ParticipantView::starting, minOccurrences, topN);
        var boots = sequences(views, view -> view.boots() == null ? List.of() : List.of(view.boots()),
                minOccurrences, topN);
        // Caminho com menos de 3 itens (partida curta) só entra se nenhum chegou a 3: sempre há recomendação.
        var buildPaths = sequences(views, view -> view.completedOrder().size() >= 3
                ? firstItems(view.completedOrder(), 3) : List.of(), minOccurrences, topN);
        if (buildPaths.isEmpty()) {
            buildPaths = sequences(views, view -> firstItems(view.completedOrder(), 3), minOccurrences, topN);
        }
        var fullBuildPaths = fullBuildPaths(views, minOccurrences, topN);
        var generalFullBuildPath = fullBuildPaths.stream().findFirst().orElse(null);
        var purchaseTree = sequences(views, ParticipantView::treeOrder, minOccurrences, topN);
        var generalBuildPath = sequences(views,
                view -> view.completedOrder().size() >= 3 ? firstItems(view.completedOrder(), 3) : List.of(), 1, 1)
                .stream().findFirst().orElse(null);

        var validRuneViews = views.stream().filter(this::hasCompleteRunes).toList();
        var allKeystones = keystones(validRuneViews, minOccurrences, Integer.MAX_VALUE);
        var keystones = allKeystones.stream().limit(topN).toList();
        var runePages = keystones.stream()
                .map(keystone -> runePage(validRuneViews, keystone.perk(), generalBuildPath,
                        generalFullBuildPath, minOccurrences))
                .toList();
        var allSpellPairs = spellPairs(views, minOccurrences, Integer.MAX_VALUE);
        var spellPairs = allSpellPairs.stream().limit(topN).toList();
        var primary = allKeystones.isEmpty() ? null : new PrimaryRecommendation(
                allKeystones.getFirst().perk(),
                allSpellPairs.isEmpty() ? List.of() : allSpellPairs.getFirst().spells(),
                allSpellPairs.isEmpty() || allSpellPairs.getFirst().lowSample());

        return new ChampionKnowledge(patch, group.championId(), group.position(), views.size(),
                (int) views.stream().filter(ParticipantView::win).count(),
                starting, boots, buildPaths, fullBuildPaths, purchaseTree, componentsBefore, skills,
                skillPriority(skills), itemWinRates, views.size() < minGames,
                keystones, runePages, spellPairs, primary, goldCurve(views));
    }

    private List<ChampionKnowledge.GoldCurvePoint> goldCurve(List<ParticipantView> views) {
        var goldByMinute = new TreeMap<Integer, List<Integer>>();
        for (var view : views) {
            var goldByMinuteForParticipant = new HashMap<Integer, Integer>();
            for (var sample : view.goldSamples()) {
                if (sample.minute() > 0 && sample.minute() <= GOLD_CURVE_MAX_MINUTE
                        && sample.minute() % GOLD_CURVE_STEP_MINUTES == 0) {
                    goldByMinuteForParticipant.putIfAbsent(sample.minute(), sample.totalGold());
                }
            }
            goldByMinuteForParticipant.forEach((minute, totalGold) -> goldByMinute
                    .computeIfAbsent(minute, ignored -> new ArrayList<>()).add(totalGold));
        }
        return goldByMinute.entrySet().stream()
                .filter(entry -> hasMinimumGoldCurveGames(entry.getValue()))
                .map(entry -> new ChampionKnowledge.GoldCurvePoint(entry.getKey(), median(entry.getValue()),
                        entry.getValue().size()))
                .toList();
    }

    private boolean hasMinimumGoldCurveGames(List<Integer> goldValues) {
        return goldValues.size() >= GOLD_CURVE_MIN_GAMES;
    }

    private int median(List<Integer> values) {
        var sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (int) (((long) sorted.get(middle - 1) + sorted.get(middle)) / 2)
                : sorted.get(middle);
    }

    private boolean hasCompleteRunes(ParticipantView view) {
        return view.runes() != null && view.runes().perks().size() == 6;
    }

    private List<KeystoneStat> keystones(List<ParticipantView> views, int minOccurrences, int topN) {
        var counts = new HashMap<Integer, Counts>();
        for (var view : views) {
            int keystone = view.runes().perks().getFirst();
            counts.computeIfAbsent(keystone, ignored -> new Counts()).add(view.win());
        }
        return counts.entrySet().stream()
                .sorted((left, right) -> {
                    int compared = compareCounts(left.getValue(), right.getValue());
                    return compared != 0 ? compared : Integer.compare(left.getKey(), right.getKey());
                })
                .limit(topN)
                .map(entry -> new KeystoneStat(entry.getKey(), entry.getValue().games,
                        entry.getValue().wins, entry.getValue().games < minOccurrences))
                .toList();
    }

    private RunePageStat runePage(List<ParticipantView> validRuneViews, int keystone,
                                  SequenceStat generalBuildPath, FullBuildPathStat generalFullBuildPath,
                                  int minOccurrences) {
        var keystoneViews = validRuneViews.stream()
                .filter(view -> view.runes().perks().getFirst() == keystone)
                .toList();
        var pages = new HashMap<Page, Counts>();
        for (var view : keystoneViews) {
            pages.computeIfAbsent(page(view), ignored -> new Counts()).add(view.win());
        }
        var selected = pages.entrySet().stream()
                .sorted((left, right) -> {
                    int compared = compareCounts(left.getValue(), right.getValue());
                    return compared != 0 ? compared : comparePages(left.getKey(), right.getKey());
                })
                .findFirst()
                .orElseThrow();
        var pageViews = keystoneViews.stream()
                .filter(view -> selected.getKey().equals(page(view)))
                .toList();

        var ownBuildPath = sequences(keystoneViews,
                view -> view.completedOrder().size() >= 3 ? firstItems(view.completedOrder(), 3) : List.of(), 1, 1)
                .stream().findFirst().orElse(null);
        var buildPath = ownBuildPath;
        if ((ownBuildPath == null || ownBuildPath.games() < minOccurrences)
                && generalBuildPath != null
                && (ownBuildPath == null || generalBuildPath.games() > ownBuildPath.games())) {
            buildPath = generalBuildPath;
        }

        var key = selected.getKey();
        var counts = selected.getValue();
        var ownFullBuildPath = fullBuildPaths(pageViews, minOccurrences, 1).stream()
                .findFirst().orElse(null);
        return new RunePageStat(keystone, key.primaryStyle(), key.subStyle(), key.perks(),
                modalStatPerks(pageViews), counts.games, counts.wins, counts.games < minOccurrences,
                buildPath, buildPath != null && buildPath.games() < minOccurrences,
                ownFullBuildPath == null ? generalFullBuildPath : ownFullBuildPath);
    }

    private List<FullBuildPathStat> fullBuildPaths(List<ParticipantView> views,
                                                   int minOccurrences, int topN) {
        var ranked = sequences(views,
                view -> view.completedOrder().size() >= 5 ? firstItems(view.completedOrder(), 6) : List.of(),
                1, Integer.MAX_VALUE);
        if (ranked.isEmpty()) return List.of();
        var mostFrequent = ranked.getFirst();
        return ranked.stream()
                .filter(stat -> stat == mostFrequent || stat.games() >= minOccurrences)
                .limit(topN)
                .map(stat -> new FullBuildPathStat(stat.sequence(), stat.games(), stat.wins(),
                        stat.games() < minOccurrences))
                .toList();
    }

    private List<Integer> firstItems(List<Integer> items, int limit) {
        return List.copyOf(items.subList(0, Math.min(items.size(), limit)));
    }

    private MatchRecord.StatPerks modalStatPerks(List<ParticipantView> views) {
        var counts = new HashMap<MatchRecord.StatPerks, Integer>();
        for (var view : views) {
            var statPerks = view.runes().statPerks();
            if (statPerks != null) counts.merge(statPerks, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<MatchRecord.StatPerks, Integer>>comparingInt(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(entry -> entry.getKey().offense())
                        .thenComparing(entry -> entry.getKey().flex())
                        .thenComparing(entry -> entry.getKey().defense()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private List<SpellPairStat> spellPairs(List<ParticipantView> views, int minOccurrences, int topN) {
        var counts = new HashMap<List<Integer>, Counts>();
        for (var view : views) {
            var spells = view.summonerSpells();
            if (spells != null && spells.size() == 2) {
                var normalized = spells.get(0) <= spells.get(1)
                        ? List.of(spells.get(0), spells.get(1))
                        : List.of(spells.get(1), spells.get(0));
                counts.computeIfAbsent(normalized, ignored -> new Counts()).add(view.win());
            }
        }
        return rank(counts, 1, topN).stream()
                .map(stat -> new SpellPairStat(stat.sequence(), stat.games(), stat.wins(),
                        stat.games() < minOccurrences))
                .toList();
    }

    private Page page(ParticipantView view) {
        var runes = view.runes();
        return new Page(runes.primaryStyle(), runes.subStyle(), runes.perks());
    }

    private static int compareCounts(Counts left, Counts right) {
        int compared = Integer.compare(right.games, left.games);
        if (compared != 0) return compared;
        return Long.compare((long) right.wins * left.games, (long) left.wins * right.games);
    }

    private static int comparePages(Page left, Page right) {
        int compared = Integer.compare(left.primaryStyle(), right.primaryStyle());
        if (compared != 0) return compared;
        compared = Integer.compare(left.subStyle(), right.subStyle());
        return compared != 0 ? compared : compareSequences(left.perks(), right.perks());
    }

    private List<SequenceStat> sequences(List<ParticipantView> views,
                                          Function<ParticipantView, List<Integer>> sequence,
                                          int minOccurrences, int topN) {
        var counts = new HashMap<List<Integer>, Counts>();
        for (var view : views) {
            var values = sequence.apply(view);
            if (!values.isEmpty()) {
                counts.computeIfAbsent(List.copyOf(values), ignored -> new Counts()).add(view.win());
            }
        }
        return rank(counts, minOccurrences, topN);
    }

    private List<SequenceStat> rank(Map<List<Integer>, Counts> counts, int minOccurrences, int topN) {
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue().games >= minOccurrences)
                .map(entry -> new SequenceStat(entry.getKey(), entry.getValue().games, entry.getValue().wins))
                .sorted(SEQUENCE_ORDER).limit(topN).toList();
    }

    private String skillPriority(List<SequenceStat> skills) {
        if (skills.isEmpty()) return "R > Q > W > E";
        var order = skills.getFirst().sequence();
        return "R > " + Stream.of(1, 2, 3)
                .sorted(Comparator.comparingInt((Integer skill) -> secondPoint(order, skill))
                        .thenComparingInt(Integer::intValue))
                .map(skill -> switch (skill) { case 1 -> "Q"; case 2 -> "W"; default -> "E"; })
                .collect(java.util.stream.Collectors.joining(" > "));
    }

    private int secondPoint(List<Integer> order, int skill) {
        int points = 0;
        for (int index = 0; index < order.size(); index++) {
            if (order.get(index) == skill && ++points == 2) return index;
        }
        return Integer.MAX_VALUE;
    }

    private static int compareSequences(List<Integer> left, List<Integer> right) {
        for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
            int compared = Integer.compare(left.get(index), right.get(index));
            if (compared != 0) return compared;
        }
        return Integer.compare(left.size(), right.size());
    }

    private record Group(int championId, String position) {}

    private record Page(int primaryStyle, int subStyle, List<Integer> perks) {
        private Page {
            perks = List.copyOf(perks);
        }
    }

    private static final class Counts {
        private int games;
        private int wins;

        private void add(boolean win) {
            games++;
            if (win) wins++;
        }
    }
}
