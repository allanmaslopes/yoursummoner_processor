package com.your.summoner.processor.application.service;

import com.your.summoner.processor.application.service.ParticipantExtractor.ParticipantView;
import com.your.summoner.processor.domain.model.ChampionKnowledge;
import com.your.summoner.processor.domain.model.ItemStat;
import com.your.summoner.processor.domain.model.SequenceStat;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeAggregatorTest {
    private final KnowledgeAggregator aggregator = new KnowledgeAggregator();

    @Test
    void belowMinimumIsNotPublished() {
        var views = Stream.generate(() -> view("TOP", true, List.of(1055))).limit(29);
        assertTrue(aggregator.aggregate("16.1", views, 30, 10, 5).isEmpty());

        var published = new ArrayList<ParticipantView>();
        add(published, 9, true, List.of(1055));
        add(published, 21, true, List.of(1054));
        var result = top(published.stream(), 30, 10, 5);
        assertEquals(30, result.games());
        assertEquals(30, result.wins());
        assertEquals("16.1", result.patch());
        assertEquals(List.of(new SequenceStat(List.of(1054), 21, 21)), result.starting());
    }

    @Test
    void sequencesSortedByGamesThenWinRate() {
        var views = new ArrayList<ParticipantView>();
        add(views, 3, false, List.of(200));
        add(views, 2, true, List.of(300));
        add(views, 2, false, List.of(100));
        add(views, 2, false, List.of(50));
        add(views, 1, true, List.of(1));

        assertEquals(List.of(
                new SequenceStat(List.of(200), 3, 0),
                new SequenceStat(List.of(300), 2, 2),
                new SequenceStat(List.of(50), 2, 0),
                new SequenceStat(List.of(100), 2, 0)), top(views.stream(), 1, 2, 10).starting());
    }

    @Test
    void topNLimits() {
        var views = Stream.of(
                detailed(true, List.of(1055, 2003, 2003), 3006, List.of(1001, 2001), List.of(1, 2, 3, 1, 1, 4, 1, 3, 1)),
                detailed(false, List.of(1054), 3047, List.of(1002, 2002), List.of(2, 1, 3, 2, 2, 4, 2, 1, 2)));
        var result = top(views, 1, 1, 1);

        assertEquals(List.of(new SequenceStat(List.of(1055, 2003, 2003), 1, 1)), result.starting());
        assertEquals(1, result.boots().size());
        assertEquals(1, result.buildPaths().size());
        assertEquals(1, result.purchaseTree().size());
        assertEquals(1, result.skillOrders().size());
        assertTrue(result.componentsBefore().values().stream().allMatch(value -> value.size() <= 1));
        assertEquals(List.of(new ItemStat(2001, 1, 1), new ItemStat(2002, 1, 0)), result.itemWinRates());
    }

    @Test
    void skillPriorityFromMostCommonOrder() {
        var common = List.of(2, 1, 3, 2, 1, 4, 2, 3, 3);
        var eighteen = List.of(2, 1, 3, 2, 2, 4, 2, 1, 2, 1, 4, 1, 1, 3, 3, 4, 3, 3);
        var views = new ArrayList<ParticipantView>();
        views.add(skills(false, common));
        views.add(skills(false, common));
        views.add(skills(true, List.of(2, 1, 3, 2, 2, 4, 2, 1, 2)));
        for (int i = 0; i < 3; i++) views.add(skills(true, eighteen));

        var result = top(views.stream(), 1, 1, 5);
        assertEquals("R > W > Q > E", result.skillPriority());
        assertEquals(2, result.skillOrders().size());
        assertEquals("R > E > Q > W", top(Stream.of(skills(true,
                List.of(3, 3, 3, 3, 3, 4, 3, 3, 3))), 1, 1, 5).skillPriority());
    }

    @Test
    void skillPriorityIgnoresSequencesBelowMinimum() {
        var majority = List.of(2, 1, 3, 2, 2, 4, 2, 1, 2);
        var minority = List.of(3, 1, 2, 3, 1, 4, 3, 2, 3);
        var result = top(Stream.of(skills(true, majority), skills(true, majority),
                skills(true, majority), skills(true, minority), skills(false, minority)), 1, 3, 5);

        assertEquals(List.of(new SequenceStat(majority, 3, 3)), result.skillOrders());
        assertEquals("R > W > Q > E", result.skillPriority());

        var unpublished = top(Stream.of(skills(true, minority), skills(false, minority)), 1, 3, 5);
        assertTrue(unpublished.skillOrders().isEmpty());
        assertEquals("R > Q > W > E", unpublished.skillPriority());
    }

    @Test
    void componentsBeforeCompletedItem() {
        var first = detailed(true, List.of(), null,
                List.of(1001, 1002, 2001, 1003, 2002, 1004), List.of());
        var second = detailed(false, List.of(), null,
                List.of(1001, 1002, 2001, 1005, 2002), List.of());
        first = withFinalItems(first, List.of(2001, 9001, 9001));
        second = withFinalItems(second, List.of(2002));
        var result = top(Stream.of(first, second), 1, 1, 5);

        assertEquals(List.of(new SequenceStat(List.of(1001, 1002), 2, 1)), result.componentsBefore().get(2001));
        assertEquals(List.of(new SequenceStat(List.of(1003), 1, 1),
                new SequenceStat(List.of(1005), 1, 0)), result.componentsBefore().get(2002));
        assertEquals(List.of(new ItemStat(2001, 1, 1), new ItemStat(9001, 1, 1),
                new ItemStat(2002, 1, 0)), result.itemWinRates());
    }

    @Test
    void anyPositionAggregatesAllRoles() {
        var views = Stream.of(view("TOP", true, List.of(1055)),
                view("MIDDLE", false, List.of(1055)),
                view("ANY", true, List.of(1055)));
        var result = aggregator.aggregate("16.1", views, 1, 1, 5);

        assertEquals(List.of("ANY", "MIDDLE", "TOP"), result.stream().map(ChampionKnowledge::position).toList());
        assertEquals(3, result.getFirst().games());
        assertEquals(2, result.getFirst().wins());
        assertEquals(List.of(new SequenceStat(List.of(1055), 3, 2)), result.getFirst().starting());
        assertEquals(1, result.get(1).games());
        assertEquals(1, result.get(2).games());
    }

    private ChampionKnowledge top(Stream<ParticipantView> views, int minGames, int minOccurrences, int topN) {
        return aggregator.aggregate("16.1", views, minGames, minOccurrences, topN).stream()
                .filter(entry -> "TOP".equals(entry.position())).findFirst().orElseThrow();
    }

    private void add(List<ParticipantView> views, int count, boolean win, List<Integer> starting) {
        for (int i = 0; i < count; i++) views.add(view("TOP", win, starting));
    }

    private ParticipantView view(String position, boolean win, List<Integer> starting) {
        return new ParticipantView(266, position, win, starting, null, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private ParticipantView skills(boolean win, List<Integer> order) {
        return new ParticipantView(266, "TOP", win, List.of(), null, List.of(), List.of(), order, List.of(), List.of());
    }

    private ParticipantView detailed(boolean win, List<Integer> starting, Integer boots,
                                     List<Integer> tree, List<Integer> skills) {
        var completed = tree.stream().filter(item -> item >= 2000).toList();
        return new ParticipantView(266, "TOP", win, starting, boots, completed, tree, skills, completed, List.of());
    }

    private ParticipantView withFinalItems(ParticipantView view, List<Integer> finalItems) {
        return new ParticipantView(view.championId(), view.position(), view.win(), view.starting(),
                view.boots(), view.completedOrder(), view.treeOrder(), view.skillOrder(), finalItems, List.of());
    }
}
