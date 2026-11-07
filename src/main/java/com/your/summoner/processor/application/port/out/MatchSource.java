package com.your.summoner.processor.application.port.out;

import com.your.summoner.processor.domain.model.MatchRecord;
import com.your.summoner.processor.domain.model.PatchVersion;
import java.util.List;
import java.util.stream.Stream;

public interface MatchSource {
    /** O chamador deve fechar o stream para liberar o cursor. */
    Stream<MatchRecord> byPatch(PatchVersion patch);
    List<PatchVersion> patches();
}
