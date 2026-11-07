package com.your.summoner.processor.application.port.out;

import com.your.summoner.processor.domain.model.PatchVersion;

public interface MatchSourceMetrics {
    long ignoredBySchema(PatchVersion patch);
}
