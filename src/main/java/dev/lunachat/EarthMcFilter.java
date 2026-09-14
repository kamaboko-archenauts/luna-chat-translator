package dev.lunachat;

import java.util.Locale;
import java.util.regex.Pattern;

public final class EarthMcFilter {
    private static final Pattern VOTE_COMMAND = Pattern.compile("(?i)(?<![a-z0-9_])/vote\\b");
    private static final Pattern VOTE_REWARD = Pattern.compile("(?i)\\bvoted\\s+and\\s+(?:received|got)\\b.*\\b(?:gold|crate|reward)");
    public static boolean ignore(String text) {
        String normalized = text.replaceAll("§.", "").replaceAll("\\s+", " ").strip();
        return VOTE_COMMAND.matcher(normalized).find() || VOTE_REWARD.matcher(normalized).find();
    }
}
