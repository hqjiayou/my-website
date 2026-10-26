package com.mywebsite.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.infra.crypto.ModelEntryCipher;
import com.mywebsite.infra.crypto.ModelEntrySecretProperties;
import org.junit.jupiter.api.Test;

class ModelEntryCipherTest {

    @Test
    void encryptsAndDecryptsWithoutEchoingThePlaintextInTheStoredValue() {
        ModelEntrySecretProperties properties = new ModelEntrySecretProperties();
        properties.setModelEntrySecret("test-secret");
        ModelEntryCipher cipher = new ModelEntryCipher(properties);

        String stored = cipher.encrypt("super-secret-key");

        assertThat(stored).startsWith("enc:v1:");
        assertThat(stored).doesNotContain("super-secret-key");
        assertThat(cipher.decrypt(stored)).isEqualTo("super-secret-key");
    }

    @Test
    void legacyPlaintextStaysReadable() {
        ModelEntrySecretProperties properties = new ModelEntrySecretProperties();
        properties.setModelEntrySecret("test-secret");
        ModelEntryCipher cipher = new ModelEntryCipher(properties);

        assertThat(cipher.decrypt("legacy-key")).isEqualTo("legacy-key");
    }

    @Test
    void encryptedValueIsNotReturnedWhenTheSecretIsMissing() {
        ModelEntrySecretProperties properties = new ModelEntrySecretProperties();
        ModelEntryCipher cipher = new ModelEntryCipher(properties);
        ModelEntrySecretProperties withSecret = new ModelEntrySecretProperties();
        withSecret.setModelEntrySecret("test-secret");
        String stored = new ModelEntryCipher(withSecret).encrypt("super-secret-key");

        assertThat(cipher.decrypt(stored)).isNull();
    }
}
