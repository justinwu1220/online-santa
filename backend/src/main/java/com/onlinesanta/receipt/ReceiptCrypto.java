package com.onlinesanta.receipt;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 身分證字號／統一編號的加解密（AES-256-GCM）。
 *
 * <p>密文格式：base64( IV 12 bytes ‖ 密文 ‖ GCM tag 16 bytes )。每次加密都用新的隨機 IV，
 * 同一個證號兩次加密的結果不同；GCM 的 tag 讓被竄改的密文解不開，而不是解出一串亂碼。
 *
 * <p>金鑰由 {@code RECEIPT_ENCRYPTION_KEY} 提供。缺漏時 {@code prod} 直接啟動失敗——
 * 帶著內建的開發金鑰上線等於沒有加密；本機與測試則退回內建金鑰，不必每個開發者先設環境變數。
 */
@Component
public class ReceiptCrypto {

    private static final Logger log = LoggerFactory.getLogger(ReceiptCrypto.class);

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public ReceiptCrypto(ReceiptProperties properties, Environment environment) {
        this.key = new SecretKeySpec(resolveKey(properties.encryptionKey(), environment), "AES");
    }

    private static byte[] resolveKey(String configured, Environment environment) {
        if (!StringUtils.hasText(configured)) {
            if (environment.matchesProfiles("prod")) {
                throw new IllegalStateException(
                        "未設定 app.receipt.encryption-key（環境變數 RECEIPT_ENCRYPTION_KEY），"
                                + "無法加密捐贈收據的身分證字號。可用 openssl rand -base64 32 產生");
            }
            log.warn("RECEIPT_ENCRYPTION_KEY 未設定，使用內建的開發用金鑰——只能用於本機與測試。");
            return sha256("online-santa-development-only-receipt-key");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.receipt.encryption-key 不是合法的 base64", e);
        }
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "app.receipt.encryption-key 解碼後必須是 %d bytes，目前是 %d bytes"
                            .formatted(KEY_BYTES, decoded.length));
        }
        return decoded;
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] packed = new byte[IV_BYTES + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, IV_BYTES);
            System.arraycopy(encrypted, 0, packed, IV_BYTES, encrypted.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("加密失敗", e);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            byte[] packed = Base64.getDecoder().decode(ciphertext);
            if (packed.length <= IV_BYTES) {
                throw new IllegalStateException("密文格式不正確");
            }

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, packed, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 不把密文或金鑰資訊帶進訊息
            throw new IllegalStateException("解密失敗：金鑰不符或密文被竄改", e);
        }
    }
}
