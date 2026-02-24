package com.koreacb.confluence.aigeneration.security;

import com.koreacb.confluence.aigeneration.service.EncryptionService;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.inject.Named;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.UUID;

@Named("encryptionService")
public class AesEncryptionService implements EncryptionService {
    private static final Logger LOG = LoggerFactory.getLogger(AesEncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 10000;
    private static final String PEPPER = "ai-gen-plugin-v1-pepper";

    private final PluginSettingsFactory pluginSettingsFactory;

    @Inject
    public AesEncryptionService(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public String encrypt(String plaintext) throws Exception {
        if (plaintext == null || plaintext.isEmpty()) return null;
        SecretKey key = deriveKey();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
        byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
        byteBuffer.put(iv);
        byteBuffer.put(cipherText);
        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }

    @Override
    public String decrypt(String ciphertext) throws Exception {
        if (ciphertext == null || ciphertext.isEmpty()) return null;
        SecretKey key = deriveKey();
        byte[] decoded = Base64.getDecoder().decode(ciphertext);
        ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
        byte[] iv = new byte[GCM_IV_LENGTH];
        byteBuffer.get(iv);
        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
        byte[] plainText = cipher.doFinal(cipherText);
        return new String(plainText, StandardCharsets.UTF_8);
    }

    private SecretKey deriveKey() throws Exception {
        String salt = getInstanceSalt();
        KeySpec spec = new PBEKeySpec(
                (PEPPER + salt).toCharArray(),
                salt.getBytes(StandardCharsets.UTF_8),
                ITERATION_COUNT, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private String getInstanceSalt() {
        try {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            String salt = (String) settings.get("ai-generation.encryption.salt");
            if (salt == null || salt.isEmpty()) {
                salt = UUID.randomUUID().toString();
                settings.put("ai-generation.encryption.salt", salt);
            }
            return salt;
        } catch (Exception e) {
            LOG.warn("Could not retrieve instance salt, using fallback", e);
            return "fallback-salt-" + PEPPER;
        }
    }
}
