package com.your.summoner.processor.infrastructure.adapter.in.rest;

import com.your.summoner.processor.application.port.out.KnowledgeStore;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {
    private final KnowledgeStore store;

    public StatusController(KnowledgeStore store) { this.store = store; }

    @GetMapping("/status")
    public Status status() {
        return new Status(store.patches().stream().sorted().flatMap(patch -> store.summary(patch).stream())
                .map(summary -> new PatchStatus(summary.patch().toString(), summary.matches(),
                        summary.ignoredBySchema(), summary.entries(), summary.computedAt().toString(),
                        summary.durationMillis())).toList());
    }

    public record Status(List<PatchStatus> patches) {}
    public record PatchStatus(String patch, long matches, long ignoredBySchema, int entries,
                              String computedAt, long durationMillis) {}
}
