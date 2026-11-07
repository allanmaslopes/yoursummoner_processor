package com.your.summoner.processor.application.service;

import com.your.summoner.processor.application.service.ParticipantExtractor.ParticipantView;
import com.your.summoner.processor.domain.model.ChampionKnowledge;
import com.your.summoner.processor.domain.model.ItemStat;
import com.your.summoner.processor.domain.model.SequenceStat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

public final class KnowledgeAggregator {
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
                .filter(entry -> entry.getValue().size() >= minGames)
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Group::championId)
                        .thenComparing(Group::position)))
                .map(entry -> knowledge(patch, entry.getKey(), entry.getValue(), minOccurrences, topN))
                .toList();
    }

    private ChampionKnowledge knowledge(String patch, Group group, List<ParticipantView> views,
                                        int minOccurrences, int topN) {
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
        return new ChampionKnowledge(patch, group.championId(), group.position(), views.size(),
                (int) views.stream().filter(ParticipantView::win).count(),
                sequences(views, ParticipantView::starting, minOccurrences, topN),
                sequences(views, view -> view.boots() == null ? List.of() : List.of(view.boots()), minOccurrences, topN),
                sequences(views, ParticipantView::completedOrder, minOccurrences, topN),
                sequences(views, ParticipantView::treeOrder, minOccurrences, topN),
                componentsBefore, skills, skillPriority(skills), itemWinRates);
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

    private static final class Counts {
        private int games;
        private int wins;

        private void add(boolean win) {
            games++;
            if (win) wins++;
        }
    }
}
