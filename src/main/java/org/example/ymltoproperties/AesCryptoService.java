package org.example.ymltoproperties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class AesCryptoService {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 65536;

    private final SecureRandom secureRandom = new SecureRandom();
    private final String requestStaticKey;

    public AesCryptoService(@Value("${app.crypto.request-static-key}") String requestStaticKey) {
        this.requestStaticKey = requestStaticKey;
    }

    public String encryptWithStaticKey(String plainText) {
        SecretKey key = deriveStaticKey(requestStaticKey);
        return encrypt(plainText, key);
    }

    public String decryptWithStaticKey(String cipherTextBase64) {
        SecretKey key = deriveStaticKey(requestStaticKey);
        return decrypt(cipherTextBase64, key);
    }

    public DynamicEncryptedPayload encryptWithDynamicKey(String plainText) {
        byte[] dynamicKeyMaterial = randomBytes(32);
        byte[] salt = randomBytes(16);
        SecretKey key = derivePbkdf2Key(dynamicKeyMaterial, salt);

        String encrypted = encrypt(plainText, key);
        String encodedKey = Base64.getEncoder().encodeToString(dynamicKeyMaterial);
        String encodedSalt = Base64.getEncoder().encodeToString(salt);

        return new DynamicEncryptedPayload(encrypted, encodedKey, encodedSalt);
    }

    public String decryptWithDynamicKey(String cipherTextBase64, String keyBase64, String saltBase64) {
        byte[] dynamicKeyMaterial = Base64.getDecoder().decode(keyBase64);
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        SecretKey key = derivePbkdf2Key(dynamicKeyMaterial, salt);
        return decrypt(cipherTextBase64, key);
    }

    private String encrypt(String plainText, SecretKey key) {
        try {
            byte[] iv = randomBytes(IV_LENGTH);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Encryption failed", ex);
        }
    }

    private String decrypt(String cipherTextBase64, SecretKey key) {
        try {
            byte[] payload = Base64.getDecoder().decode(cipherTextBase64);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted payload");
            }

            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);

            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Decryption failed", ex);
        }
    }

    private SecretKey deriveStaticKey(String keyString) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(keyString.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, AES_ALGORITHM);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to derive static key", ex);
        }
    }

    private SecretKey derivePbkdf2Key(byte[] keyMaterial, byte[] salt) {
        try {
            char[] chars = Base64.getEncoder().encodeToString(keyMaterial).toCharArray();
            PBEKeySpec spec = new PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] encoded = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(encoded, AES_ALGORITHM);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to derive dynamic key", ex);
        }
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    public record DynamicEncryptedPayload(String encryptedBody, String responseKey, String responseSalt) {
    }
}
