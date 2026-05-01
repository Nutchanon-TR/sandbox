package com.sandbox.sandman.backend.commonauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Decodes a Supabase JWT payload without verifying the signature.
 *
 * The trust boundary is the gateway/oauth2-proxy, which already validates the token before
 * forwarding the request. This library treats the JWT as a credential carrier and only
 * extracts the `sub` claim (Supabase UID).
 */
public final class JwtDecoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JwtDecoder() {}

    public static UUID extractSupabaseUid(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        String[] parts = token.split("\\.");
        if (parts.length < 2) return null;
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            JsonNode payload = MAPPER.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            JsonNode sub = payload.get("sub");
            if (sub == null || sub.isNull()) return null;
            return UUID.fromString(sub.asText());
        } catch (Exception e) {
            return null;
        }
    }

    private static String padBase64(String s) {
        int pad = (4 - s.length() % 4) % 4;
        return s + "=".repeat(pad);
    }
}
