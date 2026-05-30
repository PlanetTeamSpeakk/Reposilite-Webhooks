package com.ptsmods.repwh;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ptsmods.repwh.settings.WebhookSettings;
import com.ptsmods.repwh.settings.types.BodyType;
import com.ptsmods.repwh.settings.types.EventType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.stream.IntStream;

public class Util {
    public static final String ALPHANUMERIC_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Gson GSON = new Gson();

    public static String generateRandomString(int length) {
        return generateRandomString(length, ALPHANUMERIC_ALPHABET);
    }

    public static String generateRandomString(int length, String alphabet) {
        int[] chars = IntStream.range(0, length)
                .map(i -> alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length())))
                .toArray();
        return new String(chars, 0, chars.length);
    }

    /**
     * Builds the complete request body, wrapping the event-specific data in an envelope:
     * <pre>{@code
     * {
     *   "event":      "DEPLOY",
     *   "deliveryId": "550e8400-...",
     *   "timestamp":  1748619234567,
     *   "data":       { ... }   // null for events with no payload (e.g. SERVER_STARTED)
     * }
     * }</pre>
     * For URL-encoded form, {@code data} is JSON-encoded into a single field value.
     */
    public static String buildBody(BodyType bodyType, EventType eventType,
                                   JsonElement data, String deliveryId, long timestamp) {
        return switch (bodyType) {
            case JSON -> {
                JsonObject envelope = new JsonObject();
                envelope.addProperty("event",      eventType.name());
                envelope.addProperty("deliveryId", deliveryId);
                envelope.addProperty("timestamp",  timestamp);
                envelope.add("data", data); // JsonNull.INSTANCE → "data": null
                yield GSON.toJson(envelope);
            }
            case URL_ENCODED_FORM -> {
                // 'data' is JSON-encoded into a single field; form encoding has no native
                // concept of nested objects or null, so this keeps the contract unambiguous.
                String encodedData = URLEncoder.encode(GSON.toJson(data), StandardCharsets.UTF_8);
                yield "event="      + URLEncoder.encode(eventType.name(), StandardCharsets.UTF_8)
                    + "&deliveryId=" + URLEncoder.encode(deliveryId,       StandardCharsets.UTF_8)
                    + "&timestamp="  + timestamp
                    + "&data="       + encodedData;
            }
        };
    }

    public static String hmac(String algorithm, String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] hash = mac.doFinal(data);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute " + algorithm + " HMAC", e);
        }
    }

    /** Returns a human-readable identifier for a webhook, for use in log messages. */
    public static String label(WebhookSettings webhook) {
        String ref = webhook.getReference();
        return ref.isBlank() ? webhook.getPushUrl() : ref;
    }
}
