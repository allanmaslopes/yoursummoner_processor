package com.your.summoner.processor.domain.model;

import java.util.Optional;
import java.util.regex.Pattern;

/** Patch no formato major.minor usado pelo coletor. */
public record PatchVersion(int major, int minor) implements Comparable<PatchVersion> {
    private static final Pattern PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)(\\..*)?$");

    public PatchVersion {
        if (major < 0 || minor < 0) throw new IllegalArgumentException("patch must be non-negative");
    }

    public static Optional<PatchVersion> fromGameVersion(String version) { return parse(version); }

    public static Optional<PatchVersion> parse(String patch) {
        if (patch == null) return Optional.empty();
        var matcher = PATTERN.matcher(patch.trim());
        if (!matcher.matches()) return Optional.empty();
        try {
            return Optional.of(new PatchVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    @Override public int compareTo(PatchVersion other) {
        return major != other.major ? Integer.compare(major, other.major) : Integer.compare(minor, other.minor);
    }

    @Override public String toString() { return major + "." + minor; }
}
