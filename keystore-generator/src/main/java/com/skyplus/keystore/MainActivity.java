package com.skyplus.keystore;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

public final class MainActivity extends Activity {
    private EditText password;
    private TextView status;
    private Button generate;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("SKYPLUS MONEY\nOffline Key Vault");
        title.setTextSize(24);
        root.addView(title);

        TextView notice = new TextView(this);
        notice.setText("Prototype only. Works offline. The password is used only in memory and is never logged or transmitted. Use a unique strong password.");
        root.addView(notice);

        password = new EditText(this);
        password.setSingleLine(true);
        password.setInputType(129);
        password.setHint("Keystore password (minimum 16 characters)");
        root.addView(password);

        generate = new Button(this);
        generate.setText("Generate RSA-4096 JKS");
        root.addView(generate);

        status = new TextView(this);
        root.addView(status);

        setContentView(root);
        generate.setOnClickListener(v -> generateKey());
    }

    private void generateKey() {
        final char[] secret = password.getText().toString().toCharArray();
        if (secret.length < 16) {
            status.setText("Use at least 16 characters.");
            java.util.Arrays.fill(secret, '\0');
            return;
        }
        generate.setEnabled(false);
        status.setText("Generating locally. Keep this screen open…");
        new Thread(() -> {
            File temp = null;
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
                store.setKeyEntry("upload", pair.getPrivate(), secret, new java.security.cert.Certificate[]{cert});

                temp = File.createTempFile("skyplus-release-", ".jks", getNoBackupFilesDir());
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    store.store(out, secret);
                    out.getFD().sync();
                }
                KeyStore verify = KeyStore.getInstance("JKS");
                try (java.io.FileInputStream in = new java.io.FileInputStream(temp)) {
                    verify.load(in, secret);
                }
                if (!verify.containsAlias("upload") || !verify.isKeyEntry("upload")) {
                    throw new SecurityException("JKS read-back verification failed");
                }
                String fingerprint = toHex(java.security.MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
                final String result = "JKS created and read-back verified.\nAlias: upload\nRSA: 4096\nSHA-256: " + fingerprint + "\n\nPrototype does not yet provide encrypted backup/export. Do not use this key for production signing.";
                runOnUiThread(() -> {
                    status.setText(result);
                    Toast.makeText(this, "Prototype key generated", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                if (temp != null) temp.delete();
                runOnUiThread(() -> status.setText("Generation failed safely: " + e.getClass().getSimpleName()));
            } finally {
                java.util.Arrays.fill(secret, '\0');
                runOnUiThread(() -> generate.setEnabled(true));
            }
        }).start();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder b = new StringBuilder();
        for (byte value : bytes) b.append(String.format(java.util.Locale.ROOT, "%02X", value));
        return b.toString();
    }
}
