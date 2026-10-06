package co.tkm.ticketmanagementsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Vectors computed independently with {@code openssl dgst -sha512 -hmac test-api-key -binary | base64}. */
class PaywaySignerTest {

    private static final String KEY = "test-api-key";

    @Test
    void purchaseHashMatchesKnownVectorWithEmptyOptionalFields() {
        Map<String, String> fields = new HashMap<>();
        fields.put("req_time", "20261006120000");
        fields.put("merchant_id", "ec000002");
        fields.put("tran_id", "CK1TEST");
        fields.put("amount", "12.50");
        fields.put("shipping", "0");
        fields.put("firstname", "Dara");
        fields.put("lastname", "Sok");
        fields.put("email", "dara@example.com");
        fields.put("phone", "012345678");
        fields.put("type", "purchase");
        fields.put("payment_option", "abapay_khqr_deeplink");
        fields.put("currency", "USD");
        fields.put("lifetime", "7");
        fields.put("skip_success_page", "1");
        // items, return_url, cancel_url, continue_success_url, return_deeplink, custom_fields,
        // return_params, payout, additional_params, google_pay_token are empty.
        fields.put("payment_gate", "0");      // sent, but not part of the hash
        fields.put("view_type", "hosted_view"); // sent, but not part of the hash

        assertThat(PaywaySigner.purchaseHash(fields, KEY)).isEqualTo(
                "k1cbU/syeIzUv6bEJIXBo8zyr+j6D0nXMrYeHtcMYyko8uybZ45+HYkh5O+35bS7GVAo3qFoCS9RAzjkVZbBKg==");
    }

    @Test
    void transactionHashIsReqTimeMerchantTranId() {
        assertThat(PaywaySigner.transactionHash("20261006120000", "ec000002", "CK1TEST", KEY)).isEqualTo(
                "mb9LaLpoHt45eTGqilx86chcCbycVqcltHGqriCNgcbYYrerewJxx0D//2x3xvXG8yJQCvvqbyKVeLeOFtDeCQ==");
    }

    @Test
    void callbackSignatureSortsKeysAndConcatenatesValues() throws Exception {
        var body = new ObjectMapper().readTree("{\"tran_id\":\"CK1TEST\",\"status\":\"0\",\"payment_amount\":0.01}");
        String expected = "Cb6sYwfTKAdH+tRWSp1ksVjpMm2oXiKNQ9ZIgX4Hw3yemcDKrqr+ggl2sdQPU7PasUOSY1UOoJKxlAqa2aHkCw==";

        assertThat(PaywaySigner.callbackSignature(body, KEY)).isEqualTo(expected);
        assertThat(PaywaySigner.matches(expected, expected)).isTrue();
        assertThat(PaywaySigner.matches(expected, "bogus")).isFalse();
        assertThat(PaywaySigner.matches(expected, null)).isFalse();
    }

    @Test
    void nameSplitsOnTheFirstSpace() {
        assertThat(PaywayService.splitName("Sok Dara Chan")).containsExactly("Sok", "Dara Chan");
        assertThat(PaywayService.splitName("Dara")).containsExactly("Dara", "-");
    }
}
