package com.your.summoner.processor.domain.model;

import java.util.List;

/** Recorte anônimo do contrato matches v2 usado pelas agregações. */
public record MatchRecord(List<Participant> participants, List<TimelineEvent> events, List<Frame> frames) {
    public MatchRecord {
        participants = List.copyOf(participants);
        events = List.copyOf(events);
        frames = List.copyOf(frames);
    }

    public record Participant(int slot, int team, int championId, String position, boolean win, List<Integer> items) {
        public Participant { items = List.copyOf(items); }
    }
    public enum EventType { BUY, SELL, UNDO, SKILL }
    public record TimelineEvent(int t, int slot, EventType type, Integer itemId, Integer skillSlot, Integer frame) {}
    public record Frame(int minute, List<SlotState> slots, int blueKills, int redKills) {
        public Frame { slots = List.copyOf(slots); }
    }
    public record SlotState(int slot, int gold, int totalGold, int level, int xp, int cs) {}
}
