package com.your.summoner.processor.infrastructure.adapter.out.ddragon;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "dataDragon", url = "${processor.ddragon.url:https://ddragon.leagueoflegends.com}")
public interface DataDragonClient {
    @GetMapping("/api/versions.json")
    String versions();

    @GetMapping("/cdn/{version}/data/en_US/item.json")
    String items(@PathVariable("version") String version);
}
