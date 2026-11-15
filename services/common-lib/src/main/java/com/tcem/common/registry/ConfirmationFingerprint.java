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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *       extract the value (as a string) from the JSON tree. Paths support plain dot
 *       notation ({@code fxOptionEconomics.strike}) and array selectors
 *       ({@code parties[COUNTERPARTY].partyId} or the explicit form
 *       {@code parties[partyRole=COUNTERPARTY].partyId}).</li>
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
     * Matches a path segment carrying an array selector, e.g. {@code parties[COUNTERPARTY]}
     * (group 1 = array field name, group 2 = selector).
     */
    private static final Pattern ARRAY_SEGMENT = Pattern.compile("^([^\\[\\]]+)\\[([^\\]]+)]$");

    /** Field matched by the shorthand selector {@code parties[COUNTERPARTY]}. */
    private static final String DEFAULT_SELECTOR_FIELD = "partyRole";

    /**
     * Extracts a value from a {@link JsonNode} using a dot-notation path.
     * <p>
     * Supported syntax:
     * <ul>
     *   <li>{@code fxOptionEconomics.strike} - plain nested object access</li>
     *   <li>{@code parties[COUNTERPARTY].partyId} - shorthand: picks the first element of the
     *       {@code parties} array whose {@code partyRole} equals {@code COUNTERPARTY}</li>
     *   <li>{@code parties[partyRole=COUNTERPARTY].partyId} - explicit {@code field=value} selector</li>
     * </ul>
     * Selector values must not contain {@code .}. Returns {@code null} when any step of the
     * path is missing, null, or when no array element matches the selector.
     * <p>
     * Package-private so the unit tests can verify path resolution directly.
     */
    static String extractValue(JsonNode root, String dotPath) {
        String[] parts = dotPath.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            Matcher matcher = ARRAY_SEGMENT.matcher(part);
            if (matcher.matches()) {
                current = selectArrayElement(current.get(matcher.group(1)), matcher.group(2));
            } else {
                current = current.get(part);
            }
        }
        if (current == null || current.isNull() || current.isMissingNode()) {
            return null;
        }
        return current.asText();
    }

    /**
     * Returns the first element of {@code array} matching {@code selector}
     * ({@code VALUE} or {@code field=value}), or {@code null} if there is none.
     */
    private static JsonNode selectArrayElement(JsonNode array, String selector) {
        if (array == null || !array.isArray()) {
            return null;
        }
        String field = DEFAULT_SELECTOR_FIELD;
        String expected = selector.trim();
        int eq = selector.indexOf('=');
        if (eq > 0) {
            field = selector.substring(0, eq).trim();
            expected = selector.substring(eq + 1).trim();
        }
        for (JsonNode element : array) {
            JsonNode candidate = element.get(field);
            if (candidate != null && !candidate.isNull() && expected.equals(candidate.asText())) {
                return element;
            }
        }
        return null;
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
