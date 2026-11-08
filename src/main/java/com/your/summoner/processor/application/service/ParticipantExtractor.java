package com.your.summoner.processor.application.service;

import com.your.summoner.processor.domain.model.ItemCatalog;
import com.your.summoner.processor.domain.model.MatchRecord;
import com.your.summoner.processor.domain.model.MatchRecord.Frame;
import com.your.summoner.processor.domain.model.MatchRecord.Participant;
import com.your.summoner.processor.domain.model.MatchRecord.SlotState;
import com.your.summoner.processor.domain.model.MatchRecord.TimelineEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ParticipantExtractor {

    public List<ParticipantView> extract(MatchRecord match, ItemCatalog catalog) {
        return match.participants().stream()
                .map(participant -> extract(participant, match, catalog))
                .toList();
    }

    private ParticipantView extract(Participant participant, MatchRecord match, ItemCatalog catalog) {
        var position = position(participant);
        var purchases = effectivePurchases(participant.slot(), match.events(), catalog);

        var starting = purchases.stream()
                .filter(event -> event.t() <= 60)
                .map(event -> catalog.normalize(event.itemId()))
                .toList();

        var boots = purchases.stream()
                .map(event -> catalog.normalize(event.itemId()))
                .filter(catalog::isBoots)
                .findFirst()
                .orElse(null);

        var completedOrder = purchases.stream()
                .map(event -> catalog.normalize(event.itemId()))
                .filter(itemId -> catalog.isCompleted(itemId) && !catalog.isBoots(itemId))
                .limit(6)
                .toList();

        var treeOrder = treeOrder(purchases, catalog);

        var skillOrder = match.events().stream()
                .filter(event -> event.slot() == participant.slot())
                .filter(event -> event.type() == MatchRecord.EventType.SKILL)
                .sorted(Comparator.comparingInt(TimelineEvent::t))
                .map(TimelineEvent::skillSlot)
                .limit(9)
                .toList();

        var finalCompleted = participant.items().stream()
                .map(catalog::normalize)
                .filter(itemId -> catalog.isCompleted(itemId) && !catalog.isBoots(itemId))
                .toList();

        var decisions = purchases.stream()
                .filter(event -> !catalog.isConsumable(event.itemId()))
                .filter(event -> event.frame() != null
                        && event.frame() >= 0 && event.frame() < match.frames().size())
                .filter(event -> match.frames().get(event.frame()).slots().stream()
                        .anyMatch(state -> state.slot() == participant.slot()))
                .map(event -> decision(participant, position, event, match, catalog))
                .toList();

        return new ParticipantView(
                participant.championId(),
                position,
                participant.win(),
                starting,
                boots,
                completedOrder,
                treeOrder,
                skillOrder,
                finalCompleted,
                decisions,
                participant.summonerSpells(),
                participant.runes());
    }

    private List<TimelineEvent> effectivePurchases(int slot, List<TimelineEvent> events,
                                                    ItemCatalog catalog) {
        var purchases = new ArrayList<TimelineEvent>();
        events.stream()
                .filter(event -> event.slot() == slot)
                .sorted(Comparator.comparingInt(TimelineEvent::t))
                .forEach(event -> {
                    if (event.type() == MatchRecord.EventType.BUY && event.itemId() != null) {
                        purchases.add(event);
                    } else if (event.type() == MatchRecord.EventType.UNDO && event.itemId() != null) {
                        removePreviousPurchase(purchases, catalog.normalize(event.itemId()), catalog);
                    }
                });
        return purchases;
    }

    private void removePreviousPurchase(List<TimelineEvent> purchases, int normalizedItemId,
                                        ItemCatalog catalog) {
        for (int index = purchases.size() - 1; index >= 0; index--) {
            if (catalog.normalize(purchases.get(index).itemId()) == normalizedItemId) {
                purchases.remove(index);
                return;
            }
        }
    }

    private List<Integer> treeOrder(List<TimelineEvent> purchases, ItemCatalog catalog) {
        var result = new ArrayList<Integer>();
        int completed = 0;
        for (var purchase : purchases) {
            int itemId = catalog.normalize(purchase.itemId());
            if (!catalog.isTreeItem(itemId)) {
                continue;
            }
            result.add(itemId);
            if (catalog.isCompleted(itemId) && ++completed == 3) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private DecisionSample decision(Participant participant, String position, TimelineEvent event,
                                    MatchRecord match, ItemCatalog catalog) {
        var frame = match.frames().get(event.frame());
        int ownGold = teamGold(participant.team(), frame, match.participants());
        int otherGold = frame.slots().stream().mapToInt(SlotState::totalGold).sum() - ownGold;
        int ownLevel = level(participant.slot(), frame);
        int rivalLevel = rival(participant, position, match.participants())
                .map(rival -> level(rival.slot(), frame))
                .orElse(ownLevel);

        return new DecisionSample(
                frame.minute(),
                catalog.normalize(event.itemId()),
                ownGold - otherGold,
                ownLevel - rivalLevel,
                frame.blueKills(),
                frame.redKills(),
                participant.win());
    }

    private int teamGold(int team, Frame frame, List<Participant> participants) {
        return frame.slots().stream()
                .filter(state -> participants.stream()
                        .anyMatch(participant -> participant.team() == team
                                && participant.slot() == state.slot()))
                .mapToInt(SlotState::totalGold)
                .sum();
    }

    private java.util.Optional<Participant> rival(Participant participant, String position,
                                                   List<Participant> participants) {
        if ("ANY".equals(position)) {
            return java.util.Optional.empty();
        }
        return participants.stream()
                .filter(candidate -> candidate.team() != participant.team())
                .filter(candidate -> position.equals(position(candidate)))
                .findFirst();
    }

    private int level(int slot, Frame frame) {
        return frame.slots().stream()
                .filter(state -> state.slot() == slot)
                .mapToInt(SlotState::level)
                .findFirst()
                .orElse(0);
    }

    private String position(Participant participant) {
        return participant.position() == null || participant.position().isBlank()
                ? "ANY"
                : participant.position();
    }

    public record ParticipantView(
            int championId,
            String position,
            boolean win,
            List<Integer> starting,
            Integer boots,
            List<Integer> completedOrder,
            List<Integer> treeOrder,
            List<Integer> skillOrder,
            List<Integer> finalCompleted,
            List<DecisionSample> decisions,
            List<Integer> summonerSpells,
            MatchRecord.Runes runes) {
        public ParticipantView(int championId, String position, boolean win, List<Integer> starting, Integer boots,
                               List<Integer> completedOrder, List<Integer> treeOrder, List<Integer> skillOrder,
                               List<Integer> finalCompleted, List<DecisionSample> decisions) {
            this(championId, position, win, starting, boots, completedOrder, treeOrder, skillOrder, finalCompleted,
                    decisions, List.of(), new MatchRecord.Runes(0, 0, List.of(), null));
        }
    }

    public record DecisionSample(
            int minute,
            int itemId,
            int teamGoldDiff,
            int laneLevelDiff,
            int blueKills,
            int redKills,
            boolean win) {
    }
}
