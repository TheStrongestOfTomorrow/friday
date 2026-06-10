package com.friday.assistant.core;

import android.util.Log;

/**
 * Friday — Wake Word Engine
 *
 * Matches recognized speech against the configured wake word.
 * Uses simple string similarity with configurable confidence threshold.
 * In a production build, this would use Porcupine or Vosk for always-on
 * keyword detection, but for now we match against speech recognition results.
 */
public class WakeWordEngine {

    private static final String TAG = "Friday/WakeWord";

    /**
     * Result of a wake word match attempt.
     */
    public static class MatchResult {
        public final boolean matched;
        public final boolean ambiguous;
        public final float confidence;
        public final String suggestion;

        public MatchResult(boolean matched, boolean ambiguous, float confidence, String suggestion) {
            this.matched = matched;
            this.ambiguous = ambiguous;
            this.confidence = confidence;
            this.suggestion = suggestion;
        }
    }

    /**
     * Check if the given text matches the wake word.
     *
     * @param text           The recognized speech text (lowercase)
     * @param wakeWord       The configured wake word (lowercase)
     * @param threshold      Minimum confidence threshold (0.0 - 1.0)
     * @return MatchResult with match details
     */
    public static MatchResult match(String text, String wakeWord, float threshold) {
        if (text == null || text.isEmpty() || wakeWord == null || wakeWord.isEmpty()) {
            return new MatchResult(false, false, 0f, null);
        }

        String normalizedText = text.toLowerCase().trim();
        String normalizedWakeWord = wakeWord.toLowerCase().trim();

        // Exact match
        if (normalizedText.contains(normalizedWakeWord) || normalizedWakeWord.contains(normalizedText)) {
            float confidence = calculateConfidence(normalizedText, normalizedWakeWord);
            Log.d(TAG, "Wake word matched with confidence " + confidence);
            return new MatchResult(confidence >= threshold, false, confidence, null);
        }

        // Fuzzy match using Levenshtein distance
        float similarity = calculateSimilarity(normalizedText, normalizedWakeWord);

        if (similarity >= threshold) {
            Log.d(TAG, "Wake word fuzzy matched with similarity " + similarity);
            return new MatchResult(true, false, similarity, null);
        }

        // Check if it's ambiguous (close but not enough)
        if (similarity >= threshold * 0.7f) {
            String suggestion = "Did you mean \"" + wakeWord + "\"?";
            Log.d(TAG, "Wake word ambiguous match, similarity " + similarity);
            return new MatchResult(false, true, similarity, suggestion);
        }

        return new MatchResult(false, false, similarity, null);
    }

    /**
     * Calculate confidence score between text and wake word.
     */
    private static float calculateConfidence(String text, String wakeWord) {
        if (text.equals(wakeWord)) return 1.0f;
        if (text.contains(wakeWord)) return 0.95f;
        if (wakeWord.contains(text)) return 0.85f;
        return calculateSimilarity(text, wakeWord);
    }

    /**
     * Calculate string similarity using normalized Levenshtein distance.
     */
    private static float calculateSimilarity(String a, String b) {
        if (a.equals(b)) return 1.0f;
        if (a.isEmpty() || b.isEmpty()) return 0f;

        int maxLen = Math.max(a.length(), b.length());
        int distance = levenshteinDistance(a, b);

        return 1.0f - ((float) distance / maxLen);
    }

    /**
     * Compute Levenshtein distance between two strings.
     */
    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[a.length()][b.length()];
    }
}
