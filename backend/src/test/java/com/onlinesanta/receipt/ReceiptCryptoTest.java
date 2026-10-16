package com.onlinesanta.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("捐贈收據的加解密")
class ReceiptCryptoTest {

    private static final String KEY_A = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String KEY_B = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes());

    private ReceiptCrypto crypto(String key, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new ReceiptCrypto(new ReceiptProperties(key), environment);
    }

    @Test
    @DisplayName("加密後能解回原文，密文不含明文")
    void roundTrips() {
        ReceiptCrypto crypto = crypto(KEY_A);

        String encrypted = crypto.encrypt("A123456789");

        assertThat(encrypted).doesNotContain("A123456789");
        assertThat(crypto.decrypt(encrypted)).isEqualTo("A123456789");
    }

    @Test
    @DisplayName("同一個明文每次加密的結果都不同（隨機 IV）")
    void usesARandomIvEveryTime() {
        ReceiptCrypto crypto = crypto(KEY_A);

        assertThat(crypto.encrypt("12345678")).isNotEqualTo(crypto.encrypt("12345678"));
    }

    @Test
    @DisplayName("密文被竄改就解不開")
    void detectsTampering() {
        ReceiptCrypto crypto = crypto(KEY_A);
        byte[] packed = Base64.getDecoder().decode(crypto.encrypt("A123456789"));
        packed[packed.length - 1] ^= 1;

        assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(packed)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("換了金鑰就解不開")
    void rejectsADifferentKey() {
        String encrypted = crypto(KEY_A).encrypt("A123456789");

        assertThatThrownBy(() -> crypto(KEY_B).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("金鑰格式不對，啟動就失敗")
    void rejectsAMalformedKey() {
        assertThatThrownBy(() -> crypto("not-base64!!"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("正式環境沒設金鑰就啟動失敗，本機與測試退回開發金鑰")
    void requiresAKeyInProduction() {
        assertThatThrownBy(() -> crypto("", "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECEIPT_ENCRYPTION_KEY");

        ReceiptCrypto dev = crypto("");
        assertThat(dev.decrypt(dev.encrypt("A123456789"))).isEqualTo("A123456789");
    }
}
