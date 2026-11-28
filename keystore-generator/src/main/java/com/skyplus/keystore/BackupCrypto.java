package com.skyplus.keystore;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Versioned, authenticated backup envelope for a JKS byte stream.
 *
 * Format: 8-byte magic, 4-byte PBKDF2 iteration count, 16-byte salt,
 * 12-byte GCM nonce, then AES-GCM ciphertext and 128-bit authentication tag.
 * The complete header is authenticated as GCM additional data.
 */
final class BackupCrypto {
    private static final byte[] MAGIC = "SKYJKS01".getBytes(StandardCharsets.US_ASCII);
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int KEY_BITS = 256;
    private static final int TAG_BITS = 128;
    private static final int HEADER_BYTES = 8 + 4 + SALT_BYTES + NONCE_BYTES;
    private static final int MAX_PLAINTEXT_BYTES = 16 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BackupCrypto() {}

    static byte[] encrypt(byte[] plaintext, char[] password) throws GeneralSecurityException {
        if (plaintext == null || plaintext.length == 0 || plaintext.length > MAX_PLAINTEXT_BYTES) {
            throw new GeneralSecurityException("Invalid keystore backup size");
        }
        requirePassword(password);

        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(nonce);

        ByteBuffer headerBuffer = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
        headerBuffer.put(MAGIC);
        headerBuffer.putInt(ITERATIONS);
        headerBuffer.put(salt);
        headerBuffer.put(nonce);
        byte[] header = headerBuffer.array();

        byte[] key = deriveKey(password, salt, ITERATIONS);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(header);
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] output = new byte[header.length + encrypted.length];
            System.arraycopy(header, 0, output, 0, header.length);
            System.arraycopy(encrypted, 0, output, header.length, encrypted.length);
            Arrays.fill(encrypted, (byte) 0);
            return output;
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
        }
    }

    static byte[] decrypt(byte[] backup, char[] password) throws GeneralSecurityException {
        if (backup == null || backup.length < HEADER_BYTES + 16
                || backup.length > HEADER_BYTES + MAX_PLAINTEXT_BYTES + 16) {
            throw new GeneralSecurityException("Invalid backup file size");
        }
        requirePassword(password);

        ByteBuffer headerBuffer = ByteBuffer.wrap(backup, 0, HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
        byte[] magic = new byte[MAGIC.length];
        headerBuffer.get(magic);
        if (!Arrays.equals(MAGIC, magic)) {
            throw new GeneralSecurityException("Unsupported backup format");
        }
        int iterations = headerBuffer.getInt();
        if (iterations != ITERATIONS) {
            throw new GeneralSecurityException("Unsupported backup KDF parameters");
        }
        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        headerBuffer.get(salt);
        headerBuffer.get(nonce);
        byte[] header = Arrays.copyOfRange(backup, 0, HEADER_BYTES);
        byte[] encrypted = Arrays.copyOfRange(backup, HEADER_BYTES, backup.length);
        byte[] key = deriveKey(password, salt, iterations);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(header);
            return cipher.doFinal(encrypted);
        } catch (AEADBadTagException wrongPasswordOrTampered) {
            throw new GeneralSecurityException("Backup password is incorrect or the file was modified");
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(encrypted, (byte) 0);
        }
    }

    private static byte[] deriveKey(char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static void requirePassword(char[] password) throws GeneralSecurityException {
        if (password == null || password.length < 16 || password.length > 1024) {
            throw new GeneralSecurityException("Backup password must contain 16–1024 characters");
        }
    }
}
