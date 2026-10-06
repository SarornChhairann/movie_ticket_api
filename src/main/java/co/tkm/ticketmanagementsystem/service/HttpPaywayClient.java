package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.config.PaywayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** PayWay Ecommerce Checkout over HTTP. Keys come from {@link PaywayProperties}; never logged. */
@Service
public class HttpPaywayClient implements PaywayClient {

    private static final Logger log = LoggerFactory.getLogger(HttpPaywayClient.class);

    static final String PURCHASE_PATH = "/api/payment-gateway/v1/payments/purchase";
    static final String CHECK_PATH = "/api/payment-gateway/v1/payments/check-transaction-2";
    static final String CLOSE_PATH = "/api/payment-gateway/v1/payments/close-transaction";

    private final PaywayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public HttpPaywayClient(PaywayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (properties.isConfigured()) {
            builder.baseUrl(properties.baseUrl());
        }
        this.restClient = builder.build();
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public PurchaseResult purchase(Map<String, String> fields) {
        requireConfigured();
        Map<String, String> signed = new LinkedHashMap<>(fields);
        signed.put("merchant_id", properties.merchantId());
        signed.put("hash", PaywaySigner.purchaseHash(signed, properties.apiKey()));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        signed.forEach((name, value) -> {
            if (value != null && !value.isEmpty()) form.add(name, value);
        });

        JsonNode body = post(PURCHASE_PATH, MediaType.MULTIPART_FORM_DATA, form);
        String checkoutUrl = text(body.path("checkout_qr_url"));
        if (checkoutUrl.isEmpty()) {
            log.warn("PayWay purchase {} refused: code={} message={}", fields.get("tran_id"),
                    text(body.path("status").path("code")), text(body.path("status").path("message")));
            String message = text(body.path("status").path("message"));
            throw new PaywayException(message.isEmpty() ? "PayWay did not return a checkout page." : message);
        }
        return new PurchaseResult(checkoutUrl, text(body.path("abapay_deeplink")));
    }

    @Override
    public String checkTransaction(String tranId) {
        JsonNode body = postTransaction(CHECK_PATH, tranId);
        String status = text(body.path("data").path("payment_status"));
        if (status.isEmpty()) {
            JsonNode statusNode = body.path("status").isMissingNode() ? body.path("data").path("status") : body.path("status");
            throw new PaywayException("PayWay check failed: " + text(statusNode.path("message")));
        }
        return status;
    }

    @Override
    public boolean closeTransaction(String tranId) {
        JsonNode body = postTransaction(CLOSE_PATH, tranId);
        String code = text(body.path("status").path("code"));
        if (!"00".equals(code)) {
            log.warn("PayWay close {} refused: code={} message={}", tranId, code,
                    text(body.path("status").path("message")));
            return false;
        }
        return true;
    }

    private JsonNode postTransaction(String path, String tranId) {
        requireConfigured();
        String reqTime = PaywayService.reqTime();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("req_time", reqTime);
        payload.put("merchant_id", properties.merchantId());
        payload.put("tran_id", tranId);
        payload.put("hash", PaywaySigner.transactionHash(reqTime, properties.merchantId(), tranId, properties.apiKey()));
        return post(path, MediaType.APPLICATION_JSON, payload);
    }

    private JsonNode post(String path, MediaType contentType, Object body) {
        String raw;
        try {
            raw = restClient.post().uri(path)
                    .contentType(contentType)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("PayWay {} failed: {}", path, e.getMessage());
            throw new PaywayException("PayWay is unreachable. Please try again.", e);
        }
        try {
            return objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (IOException e) {
            throw new PaywayException("PayWay returned an unexpected response.", e);
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new PaywayException("ABA PayWay is not configured on this server.");
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText();
    }
}
