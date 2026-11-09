package com.your.summoner.processor.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.your.summoner.processor.application.port.out.KnowledgeStore;
import com.your.summoner.processor.domain.model.ChampionKnowledge;
import com.your.summoner.processor.domain.model.ChampionProfile;
import com.your.summoner.processor.domain.model.FullBuildPathStat;
import com.your.summoner.processor.domain.model.ItemStat;
import com.your.summoner.processor.domain.model.KeystoneStat;
import com.your.summoner.processor.domain.model.MatchRecord;
import com.your.summoner.processor.domain.model.PrimaryRecommendation;
import com.your.summoner.processor.domain.model.RunePageStat;
import com.your.summoner.processor.domain.model.PatchVersion;
import com.your.summoner.processor.domain.model.SequenceStat;
import com.your.summoner.processor.domain.model.SpellPairStat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeApiTest {
    private static final PatchVersion PATCH = new PatchVersion(16, 22);
    private static final Instant COMPUTED_AT = Instant.parse("2026-11-07T12:00:00Z");
    private final InMemoryStore store = new InMemoryStore();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        store.publish(snapshot(PATCH, COMPUTED_AT));
        mvc = MockMvcBuilders.standaloneSetup(new KnowledgeController(store), new StatusController(store)).build();
    }

    @Test
    void packageHasEtagAndReturns304() throws Exception {
        var first = mvc.perform(get("/processor/knowledge/16.22").contextPath("/processor"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.formatVersion").value(2))
                .andExpect(jsonPath("$.patch").value("16.22"))
                .andExpect(jsonPath("$.computedAt").value(COMPUTED_AT.toString()))
                .andExpect(jsonPath("$.matches").value(42))
                .andExpect(jsonPath("$.championProfiles[0].championId").value(62))
                .andExpect(jsonPath("$.championProfiles[0].physicalShare").value(0.5))
                .andExpect(jsonPath("$.championProfiles[0].tags[0]").value("cura-propria"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].winRate").isNumber())
                .andExpect(jsonPath("$.entries[0].winRate").value(0.6667))
                .andExpect(jsonPath("$.entries[0].starting[0].winRate").isNumber())
                .andExpect(jsonPath("$.entries[0].starting[0].winRate").value(0.3333))
                .andExpect(jsonPath("$.entries[0].boots[0].winRate").value(0.3333))
                .andExpect(jsonPath("$.entries[0].buildPaths[0].winRate").value(0.3333))
                .andExpect(jsonPath("$.entries[0].purchaseTree[0].winRate").value(0.3333))
                .andExpect(jsonPath("$.entries[0].componentsBefore['3031'][0].winRate").value(0.3333))
                .andExpect(jsonPath("$.entries[0].skillOrders[0].winRate").value(0.3333))
                .andExpect(jsonPath("$.entries[0].itemWinRates[0].winRate").isNumber())
                .andExpect(jsonPath("$.entries[0].itemWinRates[0].winRate").value(0.6667))
                .andExpect(jsonPath("$.entries[1].winRate").isNumber())
                .andExpect(jsonPath("$.entries[1].winRate").value(0))
                .andExpect(jsonPath("$.entries[1].starting[0].winRate").value(0))
                .andExpect(jsonPath("$.entries[1].itemWinRates[0].winRate").value(0))
                .andExpect(jsonPath("$.decisions").doesNotExist())
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(COMPUTED_AT.toString().getBytes(StandardCharsets.UTF_8)));
        assertThat(etag).isEqualTo("W/\"" + expectedHash + "\"");
        assertThat(first.getResponse().getContentAsString()).contains("\"winRate\":0.0000")
                .doesNotContain("decisions");

        mvc.perform(get("/processor/knowledge/16.22").contextPath("/processor")
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
        mvc.perform(get("/processor/knowledge/16.22").contextPath("/processor"))
                .andExpect(header().string(HttpHeaders.ETAG, etag));

        store.publish(snapshot(PATCH, COMPUTED_AT.plusSeconds(1)));
        var changed = mvc.perform(get("/processor/knowledge/16.22").contextPath("/processor")
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isOk()).andReturn();
        assertThat(changed.getResponse().getHeader(HttpHeaders.ETAG)).isNotBlank().isNotEqualTo(etag);
    }

    @Test
    void v2FieldsSerialized() throws Exception {
        mvc.perform(get("/processor/knowledge/16.22").contextPath("/processor"))
                .andExpect(jsonPath("$.formatVersion").value(2))
                .andExpect(jsonPath("$.entries[0].fullBuildPaths[0].sequence[0]").value(1055))
                .andExpect(jsonPath("$.entries[0].fullBuildPaths[0].lowSample").value(true))
                .andExpect(jsonPath("$.entries[0].runePages[0].fullBuildPath.sequence[0]").value(1055))
                .andExpect(jsonPath("$.entries[0].primary.keystone").value(8010))
                .andExpect(jsonPath("$.entries[0].runePages[0].statPerks.offense").value(5005))
                .andExpect(jsonPath("$.entries[0].runePages[0].buildPath.sequence[0]").value(1055))
                .andExpect(jsonPath("$.entries[0].lowSample").value(true))
                .andExpect(jsonPath("$.entries[0].summonerSpells[0].sequence[1]").value(12))
                .andExpect(jsonPath("$.entries[0].keystones[0].winRate").value(0.6667))
                .andExpect(jsonPath("$.entries[1].primary").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.entries[1].lowSample").value(false));
    }

    @Test
    void weakAndListIfNoneMatchReturn304() throws Exception {
        String etag = mvc.perform(get("/processor/knowledge/16.22").contextPath("/processor"))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        String strong = etag.substring(2);
        for (String header : List.of(etag, strong, "\"other\", " + etag, "\"other\"," + strong, "*")) {
            var response = mvc.perform(get("/processor/knowledge/16.22").contextPath("/processor")
                            .header(HttpHeaders.IF_NONE_MATCH, header)).andReturn().getResponse();
            assertThat(response.getStatus()).as(header).isEqualTo(304);
        }
        mvc.perform(get("/processor/knowledge/16.22").contextPath("/processor")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"other\", W/\"another\""))
                .andExpect(status().isOk());
    }

    @Test
    void singleEntryOr404() throws Exception {
        mvc.perform(get("/processor/knowledge/16.22/1/TOP").contextPath("/processor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.championId").value(1))
                .andExpect(jsonPath("$.position").value("TOP"))
                .andExpect(jsonPath("$.winRate").value(0.6667));
        mvc.perform(get("/processor/knowledge/16.22/3/TOP").contextPath("/processor"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/processor/knowledge/16.21/1/TOP").contextPath("/processor"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void notModifiedSingleEntryAndStatusDoNotLoadTheFullPackage() throws Exception {
        String etag = mvc.perform(get("/processor/knowledge/16.22").contextPath("/processor"))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        store.fullLoads = 0;

        mvc.perform(get("/processor/knowledge/16.22").contextPath("/processor")
                .header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isNotModified());
        mvc.perform(get("/processor/knowledge/16.22/1/TOP").contextPath("/processor")).andExpect(status().isOk());
        mvc.perform(get("/processor/status").contextPath("/processor")).andExpect(status().isOk());

        assertThat(store.fullLoads).as("full package loads").isZero();
    }

    @Test
    void componentsBeforeKeysAreSortedInTheResponse() throws Exception {
        var sequence = new SequenceStat(List.of(1055), 3, 1);
        var components = Map.of(3031, List.of(sequence), 1001, List.of(sequence), 2003, List.of(sequence));
        var entry = new ChampionKnowledge("16.22", 9, "TOP", 3, 2, List.of(), List.of(), List.of(), List.of(),
                List.of(), components, List.of(), "R > Q > W > E", List.of(), false, List.of(), List.of(),
                List.of(), null);

        assertThat(KnowledgeResponse.from(entry).componentsBefore().keySet()).containsExactly(1001, 2003, 3031);
        store.publish(new KnowledgeStore.Snapshot(PATCH, COMPUTED_AT, 1, 0, 1, List.of(entry), List.of()));
        String json = mvc.perform(get("/processor/knowledge/16.22/9/TOP").contextPath("/processor"))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.indexOf("\"1001\"")).isPositive().isLessThan(json.indexOf("\"2003\""))
                .isLessThan(json.indexOf("\"3031\""));
        assertThat(json.indexOf("\"2003\"")).isLessThan(json.indexOf("\"3031\""));
    }

    @Test
    void statusShowsPerPatch() throws Exception {
        store.publish(snapshot(new PatchVersion(16, 21), COMPUTED_AT.minusSeconds(60)));
        mvc.perform(get("/processor/status").contextPath("/processor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patches.length()").value(2))
                .andExpect(jsonPath("$.patches[0].patch").value("16.21"))
                .andExpect(jsonPath("$.patches[1].patch").value("16.22"))
                .andExpect(jsonPath("$.patches[1].matches").value(42))
                .andExpect(jsonPath("$.patches[1].ignoredBySchema").value(3))
                .andExpect(jsonPath("$.patches[1].entries").value(2))
                .andExpect(jsonPath("$.patches[1].computedAt").value(COMPUTED_AT.toString()))
                .andExpect(jsonPath("$.patches[1].durationMillis").value(125))
                .andExpect(jsonPath("$.patches[1].decisions").doesNotExist());
    }

    private static KnowledgeStore.Snapshot snapshot(PatchVersion patch, Instant computedAt) {
        var sequence = new SequenceStat(List.of(1055), 3, 1);
        var fullBuild = new FullBuildPathStat(List.of(1055, 3006, 3031, 3078, 3153), 3, 1, true);
        var entry = new ChampionKnowledge(patch.toString(), 1, "TOP", 3, 2,
                List.of(sequence), List.of(sequence), List.of(sequence), List.of(fullBuild), List.of(sequence),
                Map.of(3031, List.of(sequence)), List.of(sequence), "R > Q > W > E",
                List.of(new ItemStat(3031, 3, 2)), true,
                List.of(new KeystoneStat(8010, 3, 2, true)),
                List.of(new RunePageStat(8010, 8000, 8400, List.of(8010, 9111, 9104, 8299, 8444, 8242),
                        new MatchRecord.StatPerks(5005, 5008, 5011), 3, 2, true, sequence, true, fullBuild)),
                List.of(new SpellPairStat(List.of(4, 12), 3, 2, true)),
                new PrimaryRecommendation(8010, List.of(4, 12), true));
        var zeroSequence = new SequenceStat(List.of(1055), 0, 0);
        var zeroEntry = new ChampionKnowledge(patch.toString(), 2, "TOP", 0, 0,
                List.of(zeroSequence), List.of(), List.of(), List.of(), List.of(), Map.of(), List.of(),
                "R > Q > W > E", List.of(new ItemStat(3031, 0, 0)),
                false, List.of(), List.of(), List.of(), null);
        return new KnowledgeStore.Snapshot(patch, computedAt, 42, 3, 125, List.of(entry, zeroEntry), List.of(),
                List.of(new ChampionProfile(62, 2, 0.5, 0.25, 0.25, 12.0, 200.0,
                        List.of("cura-propria"))));
    }

    private static final class InMemoryStore implements KnowledgeStore {
        private final Map<PatchVersion, Snapshot> snapshots = new LinkedHashMap<>();
        @Override public void publish(Snapshot snapshot) { snapshots.put(snapshot.patch(), snapshot); }
        @Override public Optional<Snapshot> find(PatchVersion patch) {
            throw new AssertionError("Public API must not load decisions");
        }
        int fullLoads;
        @Override public Optional<Snapshot> findPublished(PatchVersion patch) {
            fullLoads++;
            return Optional.ofNullable(snapshots.get(patch));
        }
        // Like the Mongo adapter: metadata and single entries without loading the whole package.
        @Override public Optional<Summary> summary(PatchVersion patch) {
            return Optional.ofNullable(snapshots.get(patch)).map(s -> new Summary(s.patch(), s.computedAt(),
                    s.matches(), s.ignoredBySchema(), s.durationMillis(), s.entries().size()));
        }
        @Override public Optional<ChampionKnowledge> findEntry(PatchVersion patch, int championId, String position) {
            return Optional.ofNullable(snapshots.get(patch)).flatMap(s -> s.entries().stream()
                    .filter(e -> e.championId() == championId && e.position().equals(position)).findFirst());
        }
        @Override public List<PatchVersion> patches() { return List.copyOf(snapshots.keySet()); }
        @Override public void remove(PatchVersion patch) { snapshots.remove(patch); }
    }
}
