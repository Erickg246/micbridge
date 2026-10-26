package app.micbridge;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

final class Identity {
    static String fingerprint(X509Certificate cert) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        StringBuilder result = new StringBuilder();
        for (byte b : digest) { if (result.length() > 0) result.append(':'); result.append(String.format("%02X", b & 255)); }
        return result.toString();
    }
    static SSLSocket connect(String host, int port, String expected, String[] discovered) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String auth) throws CertificateException { throw new CertificateException("Not a server"); }
            public void checkServerTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                try {
                    if (chain.length == 0) throw new CertificateException("Certificado ausente");
                    chain[0].checkValidity();
                    String actual = fingerprint(chain[0]);
                    if (expected == null) { discovered[0] = actual; throw new CertificateException("Confirme a identidade no celular"); }
                    if (!MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII), expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                        throw new CertificateException("A identidade deste PC mudou. Faça o pareamento novamente.");
                } catch (CertificateException error) { throw error; }
                catch (Exception error) { throw new CertificateException(error); }
            }
        } }, null);
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        try {
            socket.connect(new java.net.InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(10000); socket.setTcpNoDelay(true); socket.startHandshake(); return socket;
        } catch (Exception error) { try { socket.close(); } catch (IOException ignored) {} throw error; }
    }
    static String discover(String host, int port) throws Exception {
        String[] observed = { null };
        try (SSLSocket ignored = connect(host, port, null, observed)) { throw new IOException("Verificação de identidade inesperada"); }
        catch (Exception error) { if (observed[0] != null) return observed[0]; throw error; }
    }
}
