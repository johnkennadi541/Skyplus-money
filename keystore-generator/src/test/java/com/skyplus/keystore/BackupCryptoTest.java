package com.skyplus.keystore;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import org.junit.Test;

public final class BackupCryptoTest {
    private static final char[] PASSWORD = "correct horse battery staple".toCharArray();

    @Test public void encryptDecryptRoundTripPreservesBytes() throws Exception {
        byte[] original = new byte[] {0, 1, 2, 3, 4, 5, (byte) 0xff};
        byte[] encrypted = BackupCrypto.encrypt(original, PASSWORD);
        assertArrayEquals(original, BackupCrypto.decrypt(encrypted, PASSWORD));
    }

    @Test public void wrongPasswordIsRejected() throws Exception {
        byte[] encrypted = BackupCrypto.encrypt(new byte[] {1, 2, 3}, PASSWORD);
        assertThrows(GeneralSecurityException.class,
                () -> BackupCrypto.decrypt(encrypted, "different password".toCharArray()));
    }

    @Test public void modifiedCiphertextIsRejected() throws Exception {
        byte[] encrypted = BackupCrypto.encrypt(new byte[] {9, 8, 7}, PASSWORD);
        encrypted[encrypted.length - 1] ^= 0x01;
        assertThrows(GeneralSecurityException.class,
                () -> BackupCrypto.decrypt(encrypted, PASSWORD));
    }

    @Test public void unsupportedHeaderIsRejected() throws Exception {
        byte[] encrypted = BackupCrypto.encrypt(new byte[] {1}, PASSWORD);
        encrypted[0] ^= 0x01;
        assertThrows(GeneralSecurityException.class,
                () -> BackupCrypto.decrypt(encrypted, PASSWORD));
    }

    @Test public void shortPasswordIsRejected() {
        assertThrows(GeneralSecurityException.class,
                () -> BackupCrypto.encrypt(new byte[] {1}, "short".toCharArray()));
    }

    @Test public void emptyAndOversizedPayloadsAreRejected() {
        assertThrows(GeneralSecurityException.class,
                () -> BackupCrypto.encrypt(new byte[0], PASSWORD));
        assertThrows(GeneralSecurityException.class,
                () -> BackupCrypto.encrypt(new byte[16 * 1024 * 1024 + 1], PASSWORD));
    }
}
