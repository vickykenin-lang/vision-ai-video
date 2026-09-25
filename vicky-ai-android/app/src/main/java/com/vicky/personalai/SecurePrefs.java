package com.vicky.personalai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecurePrefs {
    private static final String PREFS = "vicky_ai_prefs";
    private static final String ALIAS = "vicky_ai_master_key";
    private final SharedPreferences prefs;

    SecurePrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureKey();
    }

    void putSecret(String key, String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            prefs.edit()
                    .putString(key + "_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    .putString(key + "_ct", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .apply();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt secret", e);
        }
    }

    String getSecret(String key) {
        String ivText = prefs.getString(key + "_iv", null);
        String ctText = prefs.getString(key + "_ct", null);
        if (ivText == null || ctText == null) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP));
            cipher.init(Cipher.DECRYPT_MODE, getKey(), spec);
            byte[] plain = cipher.doFinal(Base64.decode(ctText, Base64.NO_WRAP));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    String getString(String key, String fallback) {
        return prefs.getString(key, fallback);
    }

    private void ensureKey() {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (!store.containsAlias(ALIAS)) {
                KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                generator.init(new KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build());
                generator.generateKey();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize Android Keystore", e);
        }
    }

    private SecretKey getKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return ((KeyStore.SecretKeyEntry) store.getEntry(ALIAS, null)).getSecretKey();
    }
}
