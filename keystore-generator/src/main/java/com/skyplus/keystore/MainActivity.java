package com.skyplus.keystore;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Date;

import javax.crypto.AEADBadTagException;

public final class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 41;
    private static final int REQUEST_RESTORE = 42;
    private static final int MAX_BACKUP_BYTES = 16 * 1024 * 1024 + 56;

    private EditText password;
    private EditText confirmPassword;
    private EditText exportBackupPassword;
    private EditText confirmExportBackupPassword;
    private EditText restoreBackupPassword;
    private EditText restoreKeystorePassword;
    private TextView status;
    private Button generate;
    private Button export;
    private Button restore;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("SKYPLUS MONEY\nOffline Key Vault");
        title.setTextSize(24);
        root.addView(title);

        TextView notice = new TextView(this);
        notice.setText("Offline prototype. No Internet permission. Passwords stay on this device and are never logged. Keep the keystore password and backup password separately and never share either one.");
        root.addView(notice);

        addHeading(root, "1. Generate a local signing keystore");
        password = addPassword(root, "Keystore password (minimum 16 characters)");
        confirmPassword = addPassword(root, "Confirm keystore password");
        generate = addButton(root, "Generate RSA-4096 JKS");
        generate.setOnClickListener(v -> generateKey());

        addHeading(root, "2. Export an encrypted backup");
        exportBackupPassword = addPassword(root, "New backup password (minimum 16 characters)");
        confirmExportBackupPassword = addPassword(root, "Confirm backup password");
        export = addButton(root, "Export encrypted backup");
        export.setOnClickListener(v -> chooseExport());

        addHeading(root, "3. Restore and verify a backup");
        restoreBackupPassword = addPassword(root, "Backup password");
        restoreKeystorePassword = addPassword(root, "Original keystore password");
        restore = addButton(root, "Choose backup and restore");
        restore.setOnClickListener(v -> chooseRestore());

        status = new TextView(this);
        status.setText("No production key is stored yet.");
        root.addView(status);
        setContentView(scroll);
        recoverPreviousKeystore();
    }

    private void addHeading(LinearLayout root, String value) {
        TextView heading = new TextView(this);
        heading.setText("\n" + value);
        heading.setTextSize(18);
        root.addView(heading);
    }

    private EditText addPassword(LinearLayout root, String hint) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(129);
        field.setHint(hint);
        root.addView(field);
        return field;
    }

    private Button addButton(LinearLayout root, String label) {
        Button button = new Button(this);
        button.setText(label);
        root.addView(button);
        return button;
    }

    private File keystoreFile() {
        return new File(getNoBackupFilesDir(), "release-upload.jks");
    }

    private void recoverPreviousKeystore() {
        File destination = keystoreFile();
        File previous = new File(getNoBackupFilesDir(), "release-upload.previous");
        if (!destination.exists() && previous.isFile()) {
            if (previous.renameTo(destination)) {
                status.setText("Recovered the previously saved keystore after an interrupted file replacement.");
            } else {
                status.setText("Recovery file exists. Do not uninstall the app; contact support before continuing.");
            }
        }
    }

    private char[] takePassword(EditText field) {
        android.text.Editable entered = field.getText();
        char[] result = new char[entered.length()];
        entered.getChars(0, entered.length(), result, 0);
        entered.clear();
        return result;
    }

    private void setBusy(boolean busy) {
        generate.setEnabled(!busy);
        export.setEnabled(!busy);
        restore.setEnabled(!busy);
    }

    private void generateKey() {
        final char[] secret = takePassword(password);
        final char[] confirmation = takePassword(confirmPassword);
        if (secret.length < 16 || !Arrays.equals(secret, confirmation)) {
            Arrays.fill(secret, '\0');
            Arrays.fill(confirmation, '\0');
            status.setText(secret.length < 16
                    ? "Use a keystore password with at least 16 characters."
                    : "The keystore passwords do not match.");
            return;
        }
        Arrays.fill(confirmation, '\0');
        setBusy(true);
        status.setText("Generating locally. Keep this screen open…");
        new Thread(() -> {
            File staged = null;
            File previous = new File(getNoBackupFilesDir(), "release-upload.previous");
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(4096, new SecureRandom());
                KeyPair pair = generator.generateKeyPair();

                long now = System.currentTimeMillis();
                X500Name subject = new X500Name("CN=SKYPLUS MONEY, OU=SKYPLUS, O=SKYPLUS, L=Chennai, ST=Tamil Nadu, C=IN");
                BigInteger serial = new BigInteger(160, new SecureRandom()).abs().add(BigInteger.ONE);
                JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    subject, serial, new Date(now - 60_000L),
                    new Date(now + 10_000L * 24L * 60L * 60L * 1000L),
                    subject, pair.getPublic());
                ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate());
                X509CertificateHolder holder = builder.build(signer);
                X509Certificate cert = new JcaX509CertificateConverter().getCertificate(holder);
                cert.verify(pair.getPublic());

                KeyStore store = KeyStore.getInstance("JKS");
                store.load(null, secret);
                store.setKeyEntry("upload", pair.getPrivate(), secret,
                        new java.security.cert.Certificate[]{cert});

                staged = File.createTempFile("skyplus-release-", ".jks", getNoBackupFilesDir());
                try (FileOutputStream out = new FileOutputStream(staged)) {
                    store.store(out, secret);
                    out.getFD().sync();
                }
                KeyStore verify = loadAndVerifyJks(staged, secret);
                String fingerprint = fingerprint(verify.getCertificate("upload"));

                File destination = keystoreFile();
                if (previous.exists() && !previous.delete()) {
                    throw new SecurityException("Could not clear previous staging file");
                }
                if (destination.exists() && !destination.renameTo(previous)) {
                    throw new SecurityException("Could not safely stage the previous keystore");
                }
                if (!staged.renameTo(destination)) {
                    if (previous.exists()) previous.renameTo(destination);
                    throw new SecurityException("Could not install the verified keystore");
                }
                staged = null;
                if (previous.exists()) previous.delete();

                String result = "JKS generated and reopened successfully.\nAlias: upload\nRSA: 4096\nSHA-256: "
                        + fingerprint + "\n\nExport an encrypted backup and verify a restore before production use.";
                runOnUiThread(() -> {
                    status.setText(result);
                    Toast.makeText(this, "Local keystore generated", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                if (staged != null) staged.delete();
                runOnUiThread(() -> status.setText("Generation failed safely: " + safeError(e)));
            } finally {
                Arrays.fill(secret, '\0');
                runOnUiThread(() -> setBusy(false));
            }
        }).start();
    }

    private void chooseExport() {
        if (!keystoreFile().isFile()) {
            status.setText("Generate a keystore on this device before exporting.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "skyplus-upload-keystore.skybackup");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void chooseRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_RESTORE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_EXPORT) exportBackup(data.getData());
        else if (requestCode == REQUEST_RESTORE) restoreBackup(data.getData());
    }

    private void exportBackup(Uri destination) {
        final char[] keyPassword = takePassword(password);
        final char[] backupPassword = takePassword(exportBackupPassword);
        final char[] backupConfirmation = takePassword(confirmExportBackupPassword);
        if (keyPassword.length < 16 || backupPassword.length < 16
                || !Arrays.equals(backupPassword, backupConfirmation)) {
            Arrays.fill(keyPassword, '\0');
            Arrays.fill(backupPassword, '\0');
            Arrays.fill(backupConfirmation, '\0');
            status.setText("Enter the current keystore password and matching backup passwords (at least 16 characters).");
            return;
        }
        Arrays.fill(backupConfirmation, '\0');
        setBusy(true);
        status.setText("Creating encrypted backup on this device…");
        new Thread(() -> {
            byte[] raw = null;
            byte[] encrypted = null;
            try {
                KeyStore verified = loadAndVerifyJks(keystoreFile(), keyPassword);
                String expectedFingerprint = fingerprint(verified.getCertificate("upload"));
                raw = readFileBounded(keystoreFile(), MAX_BACKUP_BYTES);
                encrypted = BackupCrypto.encrypt(raw, backupPassword);
                try (OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                    if (out == null) throw new SecurityException("Could not open the selected backup destination");
                    out.write(encrypted);
                    out.flush();
                }
                String result = "Encrypted backup exported.\nCertificate SHA-256: " + expectedFingerprint
                        + "\nFormat: SKYJKS01 / AES-256-GCM / PBKDF2-HMAC-SHA256 (600,000 iterations)."
                        + "\nKeep the backup password separate from the backup file.";
                runOnUiThread(() -> {
                    status.setText(result);
                    Toast.makeText(this, "Encrypted backup saved", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Backup export failed: " + safeError(e)));
            } finally {
                if (raw != null) Arrays.fill(raw, (byte) 0);
                if (encrypted != null) Arrays.fill(encrypted, (byte) 0);
                Arrays.fill(keyPassword, '\0');
                Arrays.fill(backupPassword, '\0');
                runOnUiThread(() -> setBusy(false));
            }
        }).start();
    }

    private void restoreBackup(Uri source) {
        final char[] backupPassword = takePassword(restoreBackupPassword);
        final char[] keyPassword = takePassword(restoreKeystorePassword);
        if (backupPassword.length < 16 || keyPassword.length < 16) {
            Arrays.fill(backupPassword, '\0');
            Arrays.fill(keyPassword, '\0');
            status.setText("Both passwords must contain at least 16 characters.");
            return;
        }
        setBusy(true);
        status.setText("Decrypting and verifying backup locally…");
        new Thread(() -> {
            byte[] encrypted = null;
            byte[] restored = null;
            File staged = null;
            File previous = new File(getNoBackupFilesDir(), "release-upload.previous");
            try {
                encrypted = readUriBounded(source, MAX_BACKUP_BYTES);
                restored = BackupCrypto.decrypt(encrypted, backupPassword);
                File tempDirectory = getNoBackupFilesDir();
                staged = File.createTempFile("skyplus-restore-", ".jks", tempDirectory);
                try (FileOutputStream out = new FileOutputStream(staged)) {
                    out.write(restored);
                    out.getFD().sync();
                }
                KeyStore candidate = loadAndVerifyJks(staged, keyPassword);
                String restoredFingerprint = fingerprint(candidate.getCertificate("upload"));

                if (previous.exists() && !previous.delete()) {
                    throw new SecurityException("Could not clear previous staging file");
                }
                File destination = keystoreFile();
                if (destination.exists() && !destination.renameTo(previous)) {
                    throw new SecurityException("Could not safely stage the existing keystore");
                }
                if (!staged.renameTo(destination)) {
                    if (previous.exists()) previous.renameTo(destination);
                    throw new SecurityException("Could not install the restored keystore");
                }
                staged = null;
                if (previous.exists()) previous.delete();

                String result = "Backup restored and JKS read-back verified.\nAlias: upload\nSHA-256: "
                        + restoredFingerprint + "\nThe certificate fingerprint must match the original backup.";
                runOnUiThread(() -> {
                    status.setText(result);
                    Toast.makeText(this, "Backup restored and verified", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                if (staged != null) staged.delete();
                runOnUiThread(() -> status.setText("Restore failed safely: " + safeError(e)));
            } finally {
                if (encrypted != null) Arrays.fill(encrypted, (byte) 0);
                if (restored != null) Arrays.fill(restored, (byte) 0);
                Arrays.fill(backupPassword, '\0');
                Arrays.fill(keyPassword, '\0');
                runOnUiThread(() -> setBusy(false));
            }
        }).start();
    }

    private KeyStore loadAndVerifyJks(File file, char[] password) throws Exception {
        KeyStore store = KeyStore.getInstance("JKS");
        try (FileInputStream in = new FileInputStream(file)) {
            store.load(in, password);
        }
        if (!store.containsAlias("upload") || !store.isKeyEntry("upload")) {
            throw new SecurityException("JKS upload alias or private key is missing");
        }
        java.security.Key key = store.getKey("upload", password);
        java.security.cert.Certificate certificate = store.getCertificate("upload");
        if (!(key instanceof RSAPrivateKey) || !(certificate.getPublicKey() instanceof RSAPublicKey)) {
            throw new SecurityException("Expected an RSA private key and certificate");
        }
        RSAPrivateKey privateKey = (RSAPrivateKey) key;
        RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();
        if (privateKey.getModulus().bitLength() != 4096
                || publicKey.getModulus().bitLength() != 4096
                || !privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new SecurityException("RSA-4096 key and certificate do not match");
        }
        byte[] challenge = new byte[32];
        new SecureRandom().nextBytes(challenge);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(challenge);
        byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(challenge);
        boolean valid = verifier.verify(signature);
        Arrays.fill(challenge, (byte) 0);
        Arrays.fill(signature, (byte) 0);
        if (!valid) throw new SecurityException("Private key proof did not match the certificate");
        return store;
    }

    private static String fingerprint(java.security.cert.Certificate certificate) throws Exception {
        return toHex(java.security.MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    private static byte[] readFileBounded(File file, int maxBytes) throws Exception {
        try (InputStream in = new FileInputStream(file)) {
            return readBounded(in, maxBytes);
        }
    }

    private byte[] readUriBounded(Uri uri, int maxBytes) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new SecurityException("Could not read the selected backup");
            return readBounded(in, maxBytes);
        }
    }

    private static byte[] readBounded(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            int total = 0;
            int count;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) throw new SecurityException("Selected file exceeds the allowed size");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            Arrays.fill(buffer, (byte) 0);
        }
    }

    private static String safeError(Exception e) {
        if (e instanceof AEADBadTagException) return "Backup password is incorrect or file is damaged.";
        String message = e.getMessage();
        if (message != null && message.contains("Backup password is incorrect")) {
            return "Backup password is incorrect or file is damaged.";
        }
        return e.getClass().getSimpleName();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder b = new StringBuilder();
        for (byte value : bytes) b.append(String.format(java.util.Locale.ROOT, "%02X", value));
        return b.toString();
    }
}
