package com.your.summoner.processor.application.port.out;

import com.your.summoner.processor.domain.model.ItemCatalog;
import com.your.summoner.processor.domain.model.PatchVersion;

public interface ItemCatalogSource {
    ItemCatalog forPatch(PatchVersion patch);
}
