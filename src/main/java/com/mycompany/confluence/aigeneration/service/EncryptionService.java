package com.mycompany.confluence.aigeneration.service;

public interface EncryptionService {
    String encrypt(String plaintext) throws Exception;
    String decrypt(String ciphertext) throws Exception;
}
