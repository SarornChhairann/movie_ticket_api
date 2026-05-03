package co.tkm.ticketmanagementsystem.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(EncryptedStringConverter.class);

    private static final String PREFIX = "enc:";
    private static final String KEY_ENV = "APP_ENCRYPTION_KEY";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        SecretKey key = loadKey();
        if (key == null) {
            return attribute;
        }
        if (attribute.startsWith(PREFIX)) {
            return attribute;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            log.error("Encryption failed; storing plaintext.", ex);
            return attribute;
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        if (!dbData.startsWith(PREFIX)) {
            return dbData;
        }
        SecretKey key = loadKey();
        if (key == null) {
            return dbData;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(dbData.substring(PREFIX.length()));
            if (combined.length <= IV_LEN) {
                return dbData;
            }
            byte[] iv = new byte[IV_LEN];
            byte[] ciphertext = new byte[combined.length - IV_LEN];
            System.arraycopy(combined, 0, iv, 0, IV_LEN);
            System.arraycopy(combined, IV_LEN, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("Decryption failed; returning stored value.", ex);
            return dbData;
        }
    }

    private SecretKey loadKey() {
        String raw = System.getenv(KEY_ENV);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException ex) {
            bytes = raw.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
            log.warn("Invalid APP_ENCRYPTION_KEY length ({} bytes). Expected 16/24/32. Encryption disabled.", bytes.length);
            return null;
        }
        return new SecretKeySpec(bytes, "AES");
    }
}

