package co.tkm.ticketmanagementsystem.service;

import com.fasterxml.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** PayWay's request and callback signatures: Base64 of HMAC-SHA512 with the API key. */
public final class PaywaySigner {

    /** Purchase hash fields, in the order the Purchase doc concatenates them. */
    public static final List<String> PURCHASE_HASH_FIELDS = List.of(
            "req_time", "merchant_id", "tran_id", "amount", "items", "shipping",
            "firstname", "lastname", "email", "phone", "type", "payment_option",
            "return_url", "cancel_url", "continue_success_url", "return_deeplink", "currency",
            "custom_fields", "return_params", "payout", "lifetime", "additional_params",
            "google_pay_token", "skip_success_page");

    private PaywaySigner() {
    }

    public static String sign(String data, String apiKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA512 is not available", e);
        }
    }

    /** Purchase hash; a field missing from [fields] counts as an empty string. */
    public static String purchaseHash(Map<String, String> fields, String apiKey) {
        StringBuilder b4hash = new StringBuilder();
        for (String name : PURCHASE_HASH_FIELDS) {
            String value = fields.get(name);
            b4hash.append(value == null ? "" : value);
        }
        return sign(b4hash.toString(), apiKey);
    }

    /** Check and Close transaction hash: {@code req_time + merchant_id + tran_id}. */
    public static String transactionHash(String reqTime, String merchantId, String tranId, String apiKey) {
        return sign(reqTime + merchantId + tranId, apiKey);
    }

    /**
     * Callback signature (checkout guide): sort the JSON keys, concatenate the values the
     * way PHP casts them to strings, HMAC-SHA512, Base64.
     */
    public static String callbackSignature(JsonNode body, String apiKey) {
        List<String> keys = new ArrayList<>();
        Iterator<String> names = body.fieldNames();
        names.forEachRemaining(keys::add);
        keys.sort(null);
        StringBuilder b4hash = new StringBuilder();
        for (String key : keys) {
            b4hash.append(phpString(body.get(key)));
        }
        return sign(b4hash.toString(), apiKey);
    }

    /** Constant-time comparison of a received signature with the expected one. */
    public static boolean matches(String expected, String received) {
        if (received == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                received.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static String phpString(JsonNode value) {
        if (value == null || value.isNull()) return "";
        if (value.isContainerNode()) return value.toString();
        if (value.isBoolean()) return value.booleanValue() ? "1" : "";
        if (value.isFloatingPointNumber()) {
            BigDecimal decimal = value.decimalValue().stripTrailingZeros();
            return decimal.scale() < 0 ? decimal.setScale(0).toPlainString() : decimal.toPlainString();
        }
        return value.asText();
    }
}
