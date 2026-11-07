package com.your.summoner.processor.domain.model;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ItemCatalog {
    private final Map<Integer, ItemInfo> items;

    public ItemCatalog(Collection<ItemInfo> items) {
        this.items = items.stream().collect(Collectors.toUnmodifiableMap(ItemInfo::id, Function.identity()));
    }

    public boolean isCompleted(int id) {
        var item = items.get(normalize(id));
        return item != null && item.into().isEmpty() && item.totalGold() >= 2000
                && !item.boots() && !item.consumable();
    }

    public boolean isTreeItem(int id) {
        var item = items.get(normalize(id));
        return item != null && item.totalGold() >= 1000 && !item.consumable();
    }

    public boolean isBoots(int id) {
        var item = items.get(normalize(id));
        return item != null && item.boots();
    }

    public int normalize(int id) {
        var item = items.get(id);
        if (item != null && item.from().size() == 1) {
            var base = items.get(item.from().getFirst());
            if (base != null && base.boots()) return base.id();
        }
        return id;
    }
}
