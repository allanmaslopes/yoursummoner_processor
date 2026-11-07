package com.your.summoner.processor.infrastructure.adapter.in.rest;

import com.your.summoner.processor.application.port.out.KnowledgeStore;
import com.your.summoner.processor.domain.model.PatchVersion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {
    private final KnowledgeStore store;

    public KnowledgeController(KnowledgeStore store) { this.store = store; }

    @GetMapping("/{patch}")
    public ResponseEntity<?> packageFor(@PathVariable String patch, ServletWebRequest request) {
        var snapshot = PatchVersion.parse(patch).flatMap(store::findPublished);
        if (snapshot.isEmpty()) return notFound();
        var value = snapshot.get();
        String etag = etag(value.computedAt().toString());
        // Weak-compares If-None-Match (W/, lists, *); Tomcat gzips only responses without a strong ETag.
        if (isWildcard(request) || request.checkNotModified(etag)) return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        return ResponseEntity.ok().eTag(etag).body(new KnowledgePackage(2, value.patch().toString(),
                value.computedAt().toString(), value.matches(),
                value.entries().stream().map(KnowledgeResponse::from).toList()));
    }

    @GetMapping("/{patch}/{championId}/{position}")
    public ResponseEntity<?> entry(@PathVariable String patch, @PathVariable int championId,
                                   @PathVariable String position) {
        return PatchVersion.parse(patch).flatMap(store::findPublished)
                .flatMap(snapshot -> snapshot.entries().stream()
                        .filter(entry -> entry.championId() == championId && entry.position().equals(position))
                        .findFirst())
                .<ResponseEntity<?>>map(entry -> ResponseEntity.ok(KnowledgeResponse.from(entry)))
                .orElseGet(KnowledgeController::notFound);
    }

    private static boolean isWildcard(ServletWebRequest request) {
        String header = request.getHeader("If-None-Match");
        return header != null && header.trim().equals("*");
    }

    private static ResponseEntity<ProblemDetail> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ProblemDetail.forStatus(HttpStatus.NOT_FOUND));
    }

    private static String etag(String computedAt) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(computedAt.getBytes(StandardCharsets.UTF_8));
            return "W/\"" + HexFormat.of().formatHex(hash) + "\"";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record KnowledgePackage(int formatVersion, String patch, String computedAt, long matches,
                                   java.util.List<KnowledgeResponse> entries) {}
}
