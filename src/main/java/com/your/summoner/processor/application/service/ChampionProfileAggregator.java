package com.your.summoner.processor.application.service;

import com.your.summoner.processor.domain.model.ChampionProfile;
import com.your.summoner.processor.domain.model.MatchRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class ChampionProfileAggregator {
    private final Map<Integer, Correction> corrections;

    public ChampionProfileAggregator(Map<Integer, Correction> corrections) {
        this.corrections = Map.copyOf(corrections);
    }

    public List<ChampionProfile> aggregate(List<MatchRecord> matches) {
        var accumulator = accumulator();
        matches.forEach(accumulator::add);
        return accumulator.profiles();
    }

    public Accumulator accumulator() {
        return new Accumulator();
    }

    public final class Accumulator {
        private final Map<Integer, Totals> totals = new HashMap<>();

        public void add(MatchRecord match) {
            for (var participant : match.participants()) {
                totals.computeIfAbsent(participant.championId(), ignored -> new Totals()).add(participant);
            }
        }

        public List<ChampionProfile> profiles() {
            return totals.entrySet().stream().map(entry -> profile(entry.getKey(), entry.getValue()))
                .sorted(java.util.Comparator.comparingInt(ChampionProfile::championId)).toList();
        }
    }

    private ChampionProfile profile(int championId, Totals totals) {
        double damage = totals.physical + totals.magic + totals.trueDamage;
        var correction = corrections.getOrDefault(championId, Correction.EMPTY);
        var tags = new HashSet<>(correction.add());
        tags.removeAll(correction.remove());
        return new ChampionProfile(championId, totals.games,
                share(totals.physical, damage), share(totals.magic, damage), share(totals.trueDamage, damage),
                average(totals.cc, totals.ccGames), average(totals.heal, totals.healGames), new ArrayList<>(tags));
    }

    private static double share(long value, double total) {
        return total == 0 ? 0.0 : Math.round(value / total * 10_000.0) / 10_000.0;
    }

    private static Double average(long value, int games) {
        return games == 0 ? null : (double) value / games;
    }

    public record Correction(List<String> add, List<String> remove) {
        static final Correction EMPTY = new Correction(List.of(), List.of());
        public Correction { add = List.copyOf(add); remove = List.copyOf(remove); }
    }

    private static final class Totals {
        int games;
        long physical;
        long magic;
        long trueDamage;
        long cc;
        int ccGames;
        long heal;
        int healGames;

        void add(MatchRecord.Participant participant) {
            games++;
            physical += participant.physicalDamage();
            magic += participant.magicDamage();
            trueDamage += participant.trueDamage();
            if (participant.timeCCingOthers() != null) { cc += participant.timeCCingOthers(); ccGames++; }
            if (participant.totalHeal() != null) { heal += participant.totalHeal(); healGames++; }
        }
    }
}
