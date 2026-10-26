package app.micbridge;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

final class PhoneIdentity {
    private static final String ALIAS = "micbridge-server-v2";
    static KeyStore keys() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setCertificateSubject(new X500Principal("CN=MicBridge Android"))
                .setCertificateSerialNumber(new BigInteger(128, new SecureRandom()))
                .setCertificateNotBefore(new Date(System.currentTimeMillis() - 86400000L))
                .setCertificateNotAfter(new Date(System.currentTimeMillis() + 10L * 365 * 86400000L)).build());
            generator.generateKeyPair(); store.load(null);
        }
        return store;
    }
    static SSLContext context(KeyStore store) throws Exception {
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(store, null);
        SSLContext context = SSLContext.getInstance("TLS"); context.init(managers.getKeyManagers(), null, new SecureRandom()); return context;
    }
    static String fingerprint(KeyStore store) throws Exception {
        X509Certificate certificate = (X509Certificate) store.getCertificate(ALIAS);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder text = new StringBuilder();
        for (byte b : digest) { if (text.length() > 0) text.append(':'); text.append(String.format(java.util.Locale.ROOT, "%02X", b & 255)); }
        return text.toString();
    }
}
