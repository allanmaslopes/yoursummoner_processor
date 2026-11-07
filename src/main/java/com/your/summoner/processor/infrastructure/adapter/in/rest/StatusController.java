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
        return new Status(store.patches().stream().sorted().flatMap(patch -> store.findPublished(patch).stream())
                .map(snapshot -> new PatchStatus(snapshot.patch().toString(), snapshot.matches(),
                        snapshot.ignoredBySchema(), snapshot.entries().size(), snapshot.computedAt().toString(),
                        snapshot.durationMillis())).toList());
    }

    public record Status(List<PatchStatus> patches) {}
    public record PatchStatus(String patch, long matches, long ignoredBySchema, int entries,
                              String computedAt, long durationMillis) {}
}
