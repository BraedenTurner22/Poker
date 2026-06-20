package com.collegeshowdown.poker_project.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Verifies Google Identity Services ID tokens via Google's tokeninfo
 * endpoint. Confirms the token was issued for our client ID and the
 * email is verified by Google, without needing a client secret.
 */
@Component
public class GoogleTokenVerifier {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token={token}";

    private final String clientId;
    private final RestTemplate restTemplate = new RestTemplate();

    public GoogleTokenVerifier(@Value("${google.oauth.client-id}") String clientId) {
        this.clientId = clientId;
    }

    public record GoogleIdentity(String email, String name) {}

    public GoogleIdentity verify(String idToken) {
        Map<?, ?> info;
        try {
            info = restTemplate.getForObject(TOKENINFO_URL, Map.class, idToken);
        } catch (RestClientException e) {
            throw new GoogleTokenException("Invalid or expired Google sign-in token.");
        }
        if (info == null) {
            throw new GoogleTokenException("Invalid Google sign-in token.");
        }

        Object aud = info.get("aud");
        if (!clientId.equals(aud)) {
            throw new GoogleTokenException("Google token was not issued for this app.");
        }

        Object email = info.get("email");
        boolean verified = "true".equals(String.valueOf(info.get("email_verified")));
        if (!(email instanceof String) || !verified) {
            throw new GoogleTokenException("Your Google account email is not verified.");
        }

        String normalizedEmail = ((String) email).toLowerCase().trim();
        Object name = info.get("name");
        String resolvedName = (name instanceof String s && !s.isBlank())
                ? s.trim()
                : normalizedEmail.substring(0, normalizedEmail.indexOf('@'));

        return new GoogleIdentity(normalizedEmail, resolvedName);
    }

    public static class GoogleTokenException extends RuntimeException {
        public GoogleTokenException(String message) { super(message); }
    }
}
