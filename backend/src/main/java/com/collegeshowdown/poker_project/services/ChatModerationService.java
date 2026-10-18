package com.collegeshowdown.poker_project.services;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Blocks slurs in chat while allowing general profanity. Word list lives in
 * {@code resources/moderation/blocked-terms.txt} — one term per line, edit
 * that file to adjust the list without touching code.
 */
@Service
public class ChatModerationService {

    private final List<Pattern> blockedPatterns = new ArrayList<>();

    public ChatModerationService() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("moderation/blocked-terms.txt").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String term = line.strip().toLowerCase(java.util.Locale.ROOT);
                if (term.isEmpty() || term.startsWith("#")) continue;
                // Only a plural s/z suffix is allowed after the term. An open-ended
                // \w* suffix turned ordinary words into "slurs": "spicy"/"spices",
                // "Pakistan", "coonhound", "retardant" ... Other inflections that
                // should be blocked go in the word list as their own lines.
                blockedPatterns.add(Pattern.compile(
                        "\\b" + Pattern.quote(term) + "[sz]?\\b", Pattern.CASE_INSENSITIVE));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load chat moderation word list", e);
        }
    }

    /** Combining marks (diacritics) and invisible format chars (zero-width space/joiner, soft hyphen, ...). */
    private static final Pattern INVISIBLE_OR_MARK = Pattern.compile("[\\p{M}\\p{Cf}]");

    /**
     * A run of 2+ single letters/digits separated only by non-alphanumerics,
     * e.g. "a b c" or "a.b.c" — the classic spaced-out evasion.
     */
    private static final Pattern SPACED_OUT = Pattern.compile(
            "(?<![\\p{L}\\p{N}])[\\p{L}\\p{N}](?:[^\\p{L}\\p{N}]+[\\p{L}\\p{N}](?![\\p{L}\\p{N}]))+");

    /** True if the text contains a blocked slur (whole word, allowing common suffixes). */
    public boolean containsBlockedTerm(String text) {
        if (text == null || text.isBlank()) return false;

        // Fold look-alikes back to plain letters before matching: NFKD maps
        // fullwidth/stylized letters to ASCII and splits accents off so they
        // can be dropped, and zero-width characters are removed — otherwise
        // any of these render identically to the slur but slip past \b...\b.
        String normalized = INVISIBLE_OR_MARK
                .matcher(Normalizer.normalize(text, Normalizer.Form.NFKD))
                .replaceAll("");

        // Also check with spaced-out single letters joined back together.
        Matcher m = SPACED_OUT.matcher(normalized);
        StringBuilder collapsed = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(collapsed,
                    Matcher.quoteReplacement(m.group().replaceAll("[^\\p{L}\\p{N}]", "")));
        }
        m.appendTail(collapsed);

        for (Pattern p : blockedPatterns) {
            if (p.matcher(normalized).find() || p.matcher(collapsed).find()) return true;
        }
        return false;
    }
}
