package co.tkm.ticketmanagementsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ABA PayWay checkout credentials, all from the environment ({@code PAYWAY_*} in .env).
 * {@code apiKey} is the HMAC key (the sandbox "public key"), not the RSA pair.
 * {@code returnUrl} is the public URL of {@code POST /api/payway/callback}; its domain
 * must be whitelisted on the merchant profile.
 */
@ConfigurationProperties(prefix = "payway")
public record PaywayProperties(
        String baseUrl,
        String merchantId,
        String apiKey,
        String returnUrl
) {

    /** True when the sandbox keys are set; otherwise PayWay is never called. */
    public boolean isConfigured() {
        return notBlank(baseUrl) && notBlank(merchantId) && notBlank(apiKey);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
