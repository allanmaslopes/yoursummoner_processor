package com.your.summoner.processor.application.service;

import com.your.summoner.processor.application.service.ParticipantExtractor.ParticipantView;
import com.your.summoner.processor.domain.model.ChampionKnowledge;
import com.your.summoner.processor.domain.model.ItemStat;
import com.your.summoner.processor.domain.model.KeystoneStat;
import com.your.summoner.processor.domain.model.MatchRecord;
import com.your.summoner.processor.domain.model.PrimaryRecommendation;
import com.your.summoner.processor.domain.model.RunePageStat;
import com.your.summoner.processor.domain.model.SequenceStat;
import com.your.summoner.processor.domain.model.SpellPairStat;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeAggregatorTest {
    private final KnowledgeAggregator aggregator = new KnowledgeAggregator();

    @Test
    void singleGameStillHasPrimary() {
        var statPerks = new MatchRecord.StatPerks(5005, 5008, 5011);
        var result = top(Stream.of(runeView("TOP", true, List.of(1055),
                List.of(3153, 3078, 3053), List.of(12, 4), 8000, 8400,
                List.of(8010, 9111, 9104, 8299, 8444, 8242), statPerks)), 30, 10, 5);

        assertEquals(1, result.games());
        assertEquals(1, result.wins());
        assertTrue(result.lowSample());
        assertEquals(List.of(new KeystoneStat(8010, 1, 1, true)), result.keystones());
        assertEquals(List.of(new RunePageStat(8010, 8000, 8400,
                List.of(8010, 9111, 9104, 8299, 8444, 8242), statPerks,
                1, 1, true, new SequenceStat(List.of(3153, 3078, 3053), 1, 1), true)),
                result.runePages());
        assertEquals(List.of(new SpellPairStat(List.of(4, 12), 1, 1, true)), result.summonerSpells());
        assertEquals(new PrimaryRecommendation(8010, List.of(4, 12), true), result.primary());
    }

    @Test
    void pagesGroupByKeystoneWithOwnBuildPath() {
        var views = Stream.of(
                runeView("TOP", true, List.of(), List.of(3153, 3078, 3053), List.of(4, 12),
                        8000, 8400, List.of(8010, 9111, 9104, 8299, 8444, 8242), null),
                runeView("TOP", false, List.of(), List.of(3153, 3078, 3053), List.of(4, 12),
                        8000, 8400, List.of(8010, 9111, 9104, 8299, 8444, 8242), null),
                runeView("TOP", true, List.of(), List.of(3078, 3153, 3053), List.of(4, 12),
                        8400, 8000, List.of(8437, 8446, 8444, 8451, 8347, 8304), null),
                runeView("TOP", false, List.of(), List.of(6672, 3006, 3031), List.of(4, 12),
                        8100, 8300, List.of(8112, 8126, 8138, 8135, 8347, 8304), null));

        var result = top(views, 1, 1, 2);

        assertEquals(List.of(
                new KeystoneStat(8010, 2, 1, false),
                new KeystoneStat(8437, 1, 1, false)), result.keystones());
        assertEquals(List.of(
                new RunePageStat(8010, 8000, 8400,
                        List.of(8010, 9111, 9104, 8299, 8444, 8242), null,
                        2, 1, false, new SequenceStat(List.of(3153, 3078, 3053), 2, 1), false),
                new RunePageStat(8437, 8400, 8000,
                        List.of(8437, 8446, 8444, 8451, 8347, 8304), null,
                        1, 1, false, new SequenceStat(List.of(3078, 3153, 3053), 1, 1), false)),
                result.runePages());
        assertEquals(new PrimaryRecommendation(8010, List.of(4, 12), false), result.primary());
    }

    @Test
    void lowSampleBuildPathFallsBackToGeneral() {
        var views = Stream.of(
                runeView("TOP", true, List.of(), List.of(3078, 3153, 3053), List.of(4, 12),
                        8000, 8400, List.of(8010, 9111, 9104, 8299, 8444, 8242), null),
                runeView("TOP", false, List.of(), List.of(3078, 3153, 3053), List.of(4, 12),
                        8000, 8400, List.of(8010, 9111, 9104, 8299, 8444, 8242), null),
                runeView("TOP", true, List.of(), List.of(3153, 3078, 3053), List.of(4, 12),
                        8400, 8000, List.of(8437, 8446, 8444, 8451, 8347, 8304), null),
                runeView("TOP", true, List.of(), List.of(3153, 3078, 3053), List.of(4, 12),
                        8400, 8000, List.of(8437, 8446, 8444, 8451, 8347, 8304), null),
                runeView("TOP", false, List.of(), List.of(3153, 3078, 3053), List.of(4, 12),
                        8400, 8000, List.of(8437, 8446, 8444, 8451, 8347, 8304), null),
                runeView("TOP", false, List.of(), List.of(3153, 3078, 3053), List.of(4, 12),
                        8400, 8000, List.of(8437, 8446, 8444, 8451, 8347, 8304), null));

        var result = top(views, 1, 3, 2);
        var page = page(result, 8010);

        assertEquals(new SequenceStat(List.of(3153, 3078, 3053), 4, 2), page.buildPath());
        assertFalse(page.buildPathLowSample());
    }

    @Test
    void pageTieBreakIsDeterministic() {
        var moreGames = top(Stream.of(
                pageView(false, 9000, 8400, List.of(8010, 9, 9, 9, 9, 9)),
                pageView(false, 9000, 8400, List.of(8010, 9, 9, 9, 9, 9)),
                pageView(false, 9000, 8400, List.of(8010, 9, 9, 9, 9, 9)),
                pageView(true, 8000, 8300, List.of(8010, 1, 1, 1, 1, 1)),
                pageView(true, 8000, 8300, List.of(8010, 1, 1, 1, 1, 1))), 1, 1, 1);
        assertEquals(9000, moreGames.runePages().getFirst().primaryStyle());

        var higherWinRate = top(Stream.of(
                pageView(true, 8000, 8400, List.of(8010, 1, 1, 1, 1, 1)),
                pageView(false, 8000, 8400, List.of(8010, 1, 1, 1, 1, 1)),
                pageView(true, 9000, 8300, List.of(8010, 9, 9, 9, 9, 9)),
                pageView(true, 9000, 8300, List.of(8010, 9, 9, 9, 9, 9))), 1, 1, 1);
        assertEquals(9000, higherWinRate.runePages().getFirst().primaryStyle());

        var lowerPrimaryStyle = top(Stream.of(
                pageView(true, 9000, 8300, List.of(8010, 1, 1, 1, 1, 1)),
                pageView(true, 8000, 8400, List.of(8010, 9, 9, 9, 9, 9))), 1, 1, 1);
        assertEquals(8000, lowerPrimaryStyle.runePages().getFirst().primaryStyle());

        var lowerSubStyle = top(Stream.of(
                pageView(true, 8000, 8400, List.of(8010, 1, 1, 1, 1, 1)),
                pageView(true, 8000, 8300, List.of(8010, 9, 9, 9, 9, 9))), 1, 1, 1);
        assertEquals(8300, lowerSubStyle.runePages().getFirst().subStyle());

        var lowerPerks = top(Stream.of(
                pageView(true, 8000, 8400, List.of(8010, 9, 9, 9, 9, 9)),
                pageView(true, 8000, 8400, List.of(8010, 1, 9, 9, 9, 9))), 1, 1, 1);
        assertEquals(List.of(8010, 1, 9, 9, 9, 9), lowerPerks.runePages().getFirst().perks());
    }

    @Test
    void statPerksFromDocumentsThatHaveThem() {
        var common = new MatchRecord.StatPerks(5005, 5008, 5011);
        var other = new MatchRecord.StatPerks(5008, 5001, 5002);
        var perks = List.of(8010, 9111, 9104, 8299, 8444, 8242);
        var mixed = top(Stream.of(
                pageView(true, 8000, 8400, perks),
                pageView(false, 8000, 8400, perks, common),
                pageView(true, 8000, 8400, perks, common),
                pageView(true, 8000, 8400, perks, other)), 1, 1, 1);

        assertEquals(new MatchRecord.StatPerks(5005, 5008, 5011),
                mixed.runePages().getFirst().statPerks());

        var oldDocuments = top(Stream.of(
                pageView(true, 8000, 8400, perks),
                pageView(false, 8000, 8400, perks)), 1, 1, 1);
        assertNull(oldDocuments.runePages().getFirst().statPerks());
    }

    @Test
    void spellPairsAreOrderInsensitive() {
        var perks = List.of(8010, 9111, 9104, 8299, 8444, 8242);
        var result = top(Stream.of(
                runeView("TOP", true, List.of(), List.of(), List.of(12, 4), 8000, 8400, perks, null),
                runeView("TOP", false, List.of(), List.of(), List.of(4, 12), 8000, 8400, perks, null),
                runeView("TOP", true, List.of(), List.of(), List.of(7, 14), 8000, 8400, perks, null)),
                1, 2, 1);

        assertEquals(List.of(new SpellPairStat(List.of(4, 12), 2, 1, false)), result.summonerSpells());
        assertEquals(new PrimaryRecommendation(8010, List.of(4, 12), false), result.primary());
    }

    @Test
    void incompleteRunesAreSkippedButCounted() {
        var complete = List.of(8010, 9111, 9104, 8299, 8444, 8242);
        var result = top(Stream.of(
                runeView("TOP", true, List.of(), List.of(), List.of(4, 12),
                        8000, 8400, List.of(8010), null),
                runeView("TOP", false, List.of(), List.of(), List.of(4, 12),
                        8000, 8400, complete, null)), 1, 1, 5);

        assertEquals(2, result.games());
        assertEquals(1, result.wins());
        assertEquals(List.of(new KeystoneStat(8010, 1, 0, false)), result.keystones());
        assertEquals(1, result.runePages().size());
        assertEquals(new PrimaryRecommendation(8010, List.of(4, 12), false), result.primary());

        var noCompleteRunes = top(Stream.of(runeView("TOP", true, List.of(), List.of(), List.of(4, 12),
                8000, 8400, List.of(8010), null)), 1, 1, 5);
        assertEquals(1, noCompleteRunes.games());
        assertTrue(noCompleteRunes.keystones().isEmpty());
        assertTrue(noCompleteRunes.runePages().isEmpty());
        assertNull(noCompleteRunes.primary());
    }

    @Test
    void blankPositionOnlyInAnyWithPrimary() {
        var result = aggregator.aggregate("16.1", Stream.of(runeView(" ", true, List.of(), List.of(),
                List.of(12, 4), 8000, 8400,
                List.of(8010, 9111, 9104, 8299, 8444, 8242), null)), 30, 10, 5);

        assertEquals(List.of("ANY"), result.stream().map(ChampionKnowledge::position).toList());
        assertTrue(result.getFirst().lowSample());
        assertEquals(new PrimaryRecommendation(8010, List.of(4, 12), true), result.getFirst().primary());
    }

    @Test
    void v1FieldsKeepFilters() {
        var perks = List.of(8010, 9111, 9104, 8299, 8444, 8242);
        var views = Stream.of(
                runeView("TOP", true, List.of(1055), List.of(3153, 3078, 3053), List.of(4, 12),
                        8000, 8400, perks, null),
                runeView("TOP", true, List.of(1055), List.of(3153, 3078, 3053), List.of(4, 12),
                        8000, 8400, perks, null),
                runeView("TOP", false, List.of(1055), List.of(3153, 3078, 3053), List.of(4, 12),
                        8000, 8400, perks, null),
                runeView("TOP", true, List.of(1054), List.of(3078, 3153, 3053), List.of(4, 12),
                        8000, 8400, perks, null),
                runeView("TOP", true, List.of(1054), List.of(3078, 3153, 3053), List.of(4, 12),
                        8000, 8400, perks, null));

        var result = top(views, 30, 3, 5);

        assertEquals(5, result.games());
        assertTrue(result.lowSample());
        assertEquals(List.of(new SequenceStat(List.of(1055), 3, 2)), result.starting());
        assertEquals(List.of(new SequenceStat(List.of(3153, 3078, 3053), 3, 2)), result.buildPaths());
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

    private ParticipantView runeView(String position, boolean win, List<Integer> starting,
                                     List<Integer> completedOrder, List<Integer> spells,
                                     int primaryStyle, int subStyle, List<Integer> perks,
                                     MatchRecord.StatPerks statPerks) {
        return new ParticipantView(266, position, win, starting, null, completedOrder, completedOrder,
                List.of(), completedOrder, List.of(), spells,
                new MatchRecord.Runes(primaryStyle, subStyle, perks, statPerks));
    }

    private ParticipantView pageView(boolean win, int primaryStyle, int subStyle, List<Integer> perks) {
        return pageView(win, primaryStyle, subStyle, perks, null);
    }

    private ParticipantView pageView(boolean win, int primaryStyle, int subStyle, List<Integer> perks,
                                     MatchRecord.StatPerks statPerks) {
        return runeView("TOP", win, List.of(), List.of(), List.of(4, 12),
                primaryStyle, subStyle, perks, statPerks);
    }

    private RunePageStat page(ChampionKnowledge knowledge, int keystone) {
        return knowledge.runePages().stream()
                .filter(candidate -> candidate.keystone() == keystone)
                .findFirst()
                .orElseThrow();
    }
}
