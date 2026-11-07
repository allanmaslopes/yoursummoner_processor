package com.your.summoner.processor.domain.model;

import java.util.List;

/** Recorte anônimo do contrato matches v2 usado pelas agregações. */
public record MatchRecord(List<Participant> participants, List<TimelineEvent> events, List<Frame> frames) {
    public MatchRecord {
        participants = List.copyOf(participants);
        events = List.copyOf(events);
        frames = List.copyOf(frames);
    }

    public record Participant(int slot, int team, int championId, String position, boolean win, List<Integer> items,
                              List<Integer> summonerSpells, Runes runes) {
        public Participant {
            items = List.copyOf(items);
            summonerSpells = summonerSpells == null ? List.of() : List.copyOf(summonerSpells);
            runes = runes == null ? new Runes(0, 0, List.of(), null) : runes;
        }

        public Participant(int slot, int team, int championId, String position, boolean win, List<Integer> items) {
            this(slot, team, championId, position, win, items, List.of(), new Runes(0, 0, List.of(), null));
        }
    }
    public record Runes(int primaryStyle, int subStyle, List<Integer> perks, StatPerks statPerks) {
        public Runes { perks = perks == null ? List.of() : List.copyOf(perks); }
    }
    public record StatPerks(int offense, int flex, int defense) {}
    public enum EventType { BUY, SELL, UNDO, SKILL }
    public record TimelineEvent(int t, int slot, EventType type, Integer itemId, Integer skillSlot, Integer frame) {}
    public record Frame(int minute, List<SlotState> slots, int blueKills, int redKills) {
        public Frame { slots = List.copyOf(slots); }
    }
    public record SlotState(int slot, int gold, int totalGold, int level, int xp, int cs) {}
}
