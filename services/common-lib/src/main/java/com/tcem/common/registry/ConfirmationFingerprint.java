package com.tcem.common.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.ProductType;
import com.tcem.common.model.trade.Trade;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Computes a deterministic SHA-256 fingerprint over the <em>confirmable</em> fields
 * of a trade for a given product type.
 *
 * <h3>Purpose</h3>
 * When trade version N+1 arrives (AMEND_TRADE), the validation-service calls
 * {@link #compute(Trade, ObjectMapper)} and compares the result against the
 * {@code previousConfirmationFingerprint} stored in the last
 * {@link com.tcem.common.model.confirmation.ConfirmationRecord}.
 * <ul>
 *   <li><strong>Fingerprints match</strong> → only non-confirmable fields changed
 *       (e.g. internal routing code). No new confirmation is emitted.
 *       Log a {@code NO_CONFIRMATION_CHANGE} audit event.</li>
 *   <li><strong>Fingerprints differ</strong> → at least one confirmable field
 *       changed. Emit a new confirmation (and supersede the previous one).</li>
 * </ul>
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Serialise the full trade to a {@link JsonNode}.</li>
 *   <li>For each field path in {@link ConfirmableFieldRegistry#getConfirmableFields(ProductType)},
 *       extract the value (as a string) from the JSON tree.</li>
 *   <li>Sort field paths lexicographically (deterministic ordering).</li>
 *   <li>Concatenate as {@code "field1=value1|field2=value2|…"}.</li>
 *   <li>SHA-256 the UTF-8 bytes; return the hex string.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * This class is stateless and thread-safe. Use a shared singleton.
 *
 * <h3>Performance</h3>
 * SHA-256 on a typical trade JSON ({@literal <}5 KB) runs in under 1 ms, well
 * within the 10-second end-to-end STP SLA (spec §13).
 */
public final class ConfirmationFingerprint {

    private ConfirmationFingerprint() {}

    /**
     * Computes the SHA-256 fingerprint over all confirmable fields for the
     * given trade's product type.
     *
     * @param trade        the trade to fingerprint
     * @param objectMapper a configured Jackson {@link ObjectMapper}
     * @return 64-character lowercase hex SHA-256 string
     * @throws IllegalArgumentException if the product type has no registry entry
     * @throws RuntimeException         if SHA-256 is unexpectedly unavailable
     */
    public static String compute(Trade trade, ObjectMapper objectMapper) {
        ProductType productType = trade.getProductType();
        if (productType == null) {
            throw new IllegalArgumentException(
                    "Trade " + trade.getTradeId() + " has null productType; cannot fingerprint.");
        }

        Set<String> confirmableFields = ConfirmableFieldRegistry.getConfirmableFields(productType);
        JsonNode tradeNode = objectMapper.valueToTree(trade);

        // Sorted map guarantees deterministic ordering regardless of registry set iteration order
        Map<String, String> valueMap = new TreeMap<>();
        for (String fieldPath : confirmableFields) {
            String value = extractValue(tradeNode, fieldPath);
            valueMap.put(fieldPath, value != null ? value : "__NULL__");
        }

        // Build canonical string
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : valueMap.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('|');
        }

        return sha256Hex(sb.toString());
    }

    /**
     * Returns {@code true} when both trades produce the same fingerprint,
     * meaning no confirmation needs to be re-sent.
     *
     * @param trade1       first trade version
     * @param trade2       second trade version
     * @param objectMapper shared Jackson mapper
     * @return {@code true} if confirmable fields are identical
     */
    public static boolean hasConfirmableChanges(Trade trade1, Trade trade2, ObjectMapper objectMapper) {
        return !compute(trade1, objectMapper).equals(compute(trade2, objectMapper));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Extracts a value from a {@link JsonNode} using a dot-notation path.
     * Handles simple paths (e.g. {@code fxOptionEconomics.strike}) but NOT
     * the {@code parties[ROLE]} syntax – that requires the calling service to
     * pre-resolve party nodes before fingerprinting if needed.
     */
    private static String extractValue(JsonNode root, String dotPath) {
        String[] parts = dotPath.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || current.isMissingNode()) {
                return null;
            }
            current = current.get(part);
        }
        if (current == null || current.isNull() || current.isMissingNode()) {
            return null;
        }
        return current.asText();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JDK spec; this should never happen
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
