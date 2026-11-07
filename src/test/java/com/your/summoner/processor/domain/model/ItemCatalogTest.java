package com.your.summoner.processor.domain.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

class ItemCatalogTest {
    private final ItemCatalog catalog = new ItemCatalog(List.of(
            new ItemInfo(3031, 3500, false, false, List.of(1038), List.of()),
            new ItemInfo(3006, 1100, true, false, List.of(1001, 1042), List.of(3172)),
            new ItemInfo(3172, 1100, false, false, List.of(3006), List.of()),
            new ItemInfo(2003, 50, false, true, List.of(), List.of()),
            new ItemInfo(3077, 1200, false, false, List.of(1036), List.of(3074)),
            new ItemInfo(999, 3000, false, true, List.of(), List.of())));

    @Test void completedAndTreeRules() {
        assertThat(catalog.isCompleted(3031)).isTrue();
        assertThat(catalog.isTreeItem(3031)).isTrue();
        assertThat(catalog.isCompleted(3006)).isFalse();
        assertThat(catalog.isTreeItem(3006)).isTrue();
        assertThat(catalog.isCompleted(2003)).isFalse();
        assertThat(catalog.isTreeItem(2003)).isFalse();
        assertThat(catalog.isCompleted(3077)).isFalse();
        assertThat(catalog.isTreeItem(3077)).isTrue();
        assertThat(catalog.isCompleted(999)).isFalse();
        assertThat(catalog.isTreeItem(999)).isFalse();
        assertThat(catalog.isCompleted(0)).isFalse();
        assertThat(catalog.isTreeItem(0)).isFalse();
    }

    @Test void questUpgradeNormalizesToBase() {
        assertThat(catalog.normalize(3172)).isEqualTo(3006);
        assertThat(catalog.normalize(3031)).isEqualTo(3031);
        assertThat(catalog.normalize(123456)).isEqualTo(123456);
    }

    @Test void bootsClassificationUsesNormalizedItem() {
        assertThat(catalog.isBoots(3006)).isTrue();
        assertThat(catalog.isBoots(3172)).isTrue();
        assertThat(catalog.isBoots(3077)).isFalse();
        assertThat(catalog.isBoots(123456)).isFalse();
    }

    @Test void patchUsesCollectorParsingAndNumericOrdering() {
        assertThat(PatchVersion.parse(" 16.22.1 ")).contains(new PatchVersion(16, 22));
        assertThat(PatchVersion.fromGameVersion("16.22.123.456")).contains(new PatchVersion(16, 22));
        assertThat(PatchVersion.parse(null)).isEqualTo(Optional.empty());
        assertThat(PatchVersion.parse("invalid")).isEmpty();
        assertThat(PatchVersion.parse("999999999999.22")).isEmpty();
        assertThat(new PatchVersion(16, 22)).isGreaterThan(new PatchVersion(16, 9));
        assertThat(new PatchVersion(16, 22).toString()).isEqualTo("16.22");
        assertThatThrownBy(() -> new PatchVersion(-1, 22)).isInstanceOf(IllegalArgumentException.class);
    }
}
