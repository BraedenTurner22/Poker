package com.collegeshowdown.poker_project.services;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
                String term = line.strip().toLowerCase();
                if (term.isEmpty() || term.startsWith("#")) continue;
                // \w* after the term also catches simple suffixed variants (plurals, -er, etc.)
                blockedPatterns.add(Pattern.compile(
                        "\\b" + Pattern.quote(term) + "\\w*\\b", Pattern.CASE_INSENSITIVE));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load chat moderation word list", e);
        }
    }

    /** True if the text contains a blocked slur (whole word, allowing common suffixes). */
    public boolean containsBlockedTerm(String text) {
        if (text == null || text.isBlank()) return false;
        for (Pattern p : blockedPatterns) {
            if (p.matcher(text).find()) return true;
        }
        return false;
    }
}
