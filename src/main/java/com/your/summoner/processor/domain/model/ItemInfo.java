package com.your.summoner.processor.domain.model;

import java.util.List;

public record ItemInfo(int id, int totalGold, boolean boots, boolean consumable,
                       List<Integer> from, List<Integer> into) {
    public ItemInfo {
        from = from == null ? List.of() : List.copyOf(from);
        into = into == null ? List.of() : List.copyOf(into);
    }
}
