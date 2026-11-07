package com.your.summoner.processor.application.service;

import com.your.summoner.processor.domain.model.ItemCatalog;
import com.your.summoner.processor.domain.model.ItemInfo;
import com.your.summoner.processor.domain.model.MatchRecord;
import com.your.summoner.processor.domain.model.MatchRecord.EventType;
import com.your.summoner.processor.domain.model.MatchRecord.Frame;
import com.your.summoner.processor.domain.model.MatchRecord.Participant;
import com.your.summoner.processor.domain.model.MatchRecord.SlotState;
import com.your.summoner.processor.domain.model.MatchRecord.TimelineEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticipantExtractorTest {

    private final ParticipantExtractor extractor = new ParticipantExtractor();

    @Test
    void startingItemsUntilSixtySeconds() {
        var events = List.of(
                buy(60, 1, 1001, 0),
                buy(61, 1, 1002, 0));

        var view = viewForSlotOne(match(events, List.of()));

        assertEquals(List.of(1001), view.starting());
    }

    @Test
    void undoRemovesPreviousPurchase() {
        var events = List.of(
                buy(120, 1, 2001, 0),
                new TimelineEvent(121, 1, EventType.UNDO, 2001, null, 0),
                buy(122, 1, 2002, 0));

        var view = viewForSlotOne(match(events, List.of()));

        assertEquals(List.of(2002), view.completedOrder());
    }

    @Test
    void questUpgradeCountsAsBaseBoots() {
        var view = viewForSlotOne(match(List.of(buy(120, 1, 3002, 0)), List.of()));

        assertEquals(3001, view.boots());
    }

    @Test
    void completedOrderStopsAtThree() {
        var events = List.of(
                buy(120, 1, 2001, 0),
                buy(180, 1, 2002, 0),
                buy(240, 1, 2003, 0),
                buy(300, 1, 2004, 0));

        var view = viewForSlotOne(match(events, List.of()));

        assertEquals(List.of(2001, 2002, 2003), view.completedOrder());
    }

    @Test
    void treeIncludesComponentsAboveThousand() {
        var view = viewForSlotOne(match(List.of(buy(120, 1, 5001, 0)), List.of()));

        assertEquals(List.of(5001), view.treeOrder());
    }

    @Test
    void skillOrderFirstNine() {
        var events = new ArrayList<TimelineEvent>();
        for (int skill = 1; skill <= 10; skill++) {
            events.add(new TimelineEvent(skill * 60, 1, EventType.SKILL, null, skill, 0));
        }

        var view = viewForSlotOne(match(events, List.of()));

        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), view.skillOrder());
    }

    @Test
    void emptyPositionCountsOnlyForChampion() {
        var participants = participants();
        participants.set(0, new Participant(1, 100, 266, "", true, List.of(2001)));
        var match = new MatchRecord(participants, List.of(), List.of());

        var view = extractor.extract(match, catalog()).getFirst();

        assertEquals(266, view.championId());
        assertEquals("ANY", view.position());
    }

    @Test
    void decisionSampleUsesEventFrame() {
        var frames = List.of(
                new Frame(1, states(1_000, 9, 12_000, 8), 9, 8),
                new Frame(2, states(3_000, 6, 2_500, 5), 4, 3));
        var view = viewForSlotOne(match(List.of(buy(120, 1, 4001, 1)), frames));

        assertEquals(1, view.decisions().size());
        var sample = view.decisions().getFirst();
        assertEquals(2, sample.minute());
        assertEquals(4001, sample.itemId());
        assertEquals(1_000, sample.teamGoldDiff());
        assertEquals(1, sample.laneLevelDiff());
        assertEquals(4, sample.blueKills());
        assertEquals(3, sample.redKills());
        assertTrue(sample.win());
    }

    private ParticipantExtractor.ParticipantView viewForSlotOne(MatchRecord match) {
        return extractor.extract(match, catalog()).getFirst();
    }

    private MatchRecord match(List<TimelineEvent> events, List<Frame> frames) {
        return new MatchRecord(participants(), events, frames);
    }

    private ArrayList<Participant> participants() {
        var participants = new ArrayList<Participant>();
        participants.add(new Participant(1, 100, 266, "TOP", true, List.of()));
        participants.add(new Participant(2, 100, 103, "JUNGLE", false, List.of()));
        participants.add(new Participant(3, 100, 84, "MIDDLE", true, List.of()));
        participants.add(new Participant(4, 100, 22, "BOTTOM", true, List.of()));
        participants.add(new Participant(5, 100, 40, "UTILITY", true, List.of()));
        participants.add(new Participant(6, 200, 122, "TOP", false, List.of()));
        participants.add(new Participant(7, 200, 64, "JUNGLE", true, List.of()));
        participants.add(new Participant(8, 200, 238, "MIDDLE", false, List.of()));
        participants.add(new Participant(9, 200, 51, "BOTTOM", false, List.of()));
        participants.add(new Participant(10, 200, 89, "UTILITY", false, List.of()));
        return participants;
    }

    private List<SlotState> states(int blueTopGold, int blueTopLevel,
                                   int redTopGold, int redTopLevel) {
        return List.of(
                new SlotState(1, 0, blueTopGold, blueTopLevel, 0, 0),
                new SlotState(2, 0, 1_750, 1, 0, 0),
                new SlotState(3, 0, 1_750, 1, 0, 0),
                new SlotState(4, 0, 1_750, 1, 0, 0),
                new SlotState(5, 0, 1_750, 1, 0, 0),
                new SlotState(6, 0, redTopGold, redTopLevel, 0, 0),
                new SlotState(7, 0, 1_625, 1, 0, 0),
                new SlotState(8, 0, 1_625, 1, 0, 0),
                new SlotState(9, 0, 1_625, 1, 0, 0),
                new SlotState(10, 0, 1_625, 1, 0, 0));
    }

    private TimelineEvent buy(int timestamp, int slot, int itemId, int frame) {
        return new TimelineEvent(timestamp, slot, EventType.BUY, itemId, null, frame);
    }

    private ItemCatalog catalog() {
        return new ItemCatalog(List.of(
                item(1001, 500, false, false, List.of(), List.of(2001)),
                item(1002, 500, false, false, List.of(), List.of(2002)),
                item(2001, 3_000, false, false, List.of(1001), List.of()),
                item(2002, 3_100, false, false, List.of(1002), List.of()),
                item(2003, 3_200, false, false, List.of(), List.of()),
                item(2004, 3_300, false, false, List.of(), List.of()),
                item(3001, 1_100, true, false, List.of(), List.of(3002)),
                item(3002, 1_100, true, false, List.of(3001), List.of()),
                item(4001, 3_400, false, false, List.of(), List.of()),
                item(5001, 1_200, false, false, List.of(), List.of(5002)),
                item(5002, 3_500, false, false, List.of(5001), List.of())));
    }

    private ItemInfo item(int id, int totalGold, boolean boots, boolean consumable,
                          List<Integer> from, List<Integer> into) {
        return new ItemInfo(id, totalGold, boots, consumable, from, into);
    }
}
