package com.your.summoner.processor.infrastructure.config;

import com.your.summoner.processor.application.port.out.ItemCatalogSource;
import com.your.summoner.processor.application.port.out.KnowledgeStore;
import com.your.summoner.processor.application.port.out.MatchSource;
import com.your.summoner.processor.application.port.out.MatchSourceMetrics;
import com.your.summoner.processor.application.service.RecomputeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RecomputeConfiguration {
    @Bean
    RecomputeService recomputeService(MatchSource source, MatchSourceMetrics metrics,
                                     ItemCatalogSource catalogs, KnowledgeStore store,
                                     ChampionCorrections corrections,
                                     @Value("${processor.min-games:30}") int minGames,
                                     @Value("${processor.min-occurrences:10}") int minOccurrences,
                                     @Value("${processor.top-n:5}") int topN) {
        return new RecomputeService(source, metrics, catalogs, store, corrections.values(), minGames, minOccurrences, topN);
    }
}
