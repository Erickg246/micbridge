package app.micbridge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLSocket;

public final class IdentityCheck {
    public static void main(String[] args) throws Exception {
        String host = args[0], expected = args[2]; int port = Integer.parseInt(args[1]);
        if (!expected.equals(Identity.discover(host, port))) throw new AssertionError("Discovery fingerprint mismatch");
        try (SSLSocket ignored = Identity.connect(host, port, "00", null)) { throw new AssertionError("Wrong fingerprint accepted"); }
        catch (javax.net.ssl.SSLHandshakeException correct) { /* Must reject an untrusted peer. */ }
        try (SSLSocket socket = Identity.connect(host, port, expected, null)) {
            socket.getOutputStream().write("{\"code\":\"123456\",\"rate\":48000,\"channels\":1,\"format\":\"s16le\"}\n".getBytes(StandardCharsets.UTF_8));
            String response = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
            if (!"OK".equals(response)) throw new AssertionError("Android transport rejected: " + response);
            socket.getOutputStream().write(new byte[] {1,2,3,4}); socket.getOutputStream().flush();
        }
        System.out.println("Android TLS discovery, rejection and pinned authentication passed");
    }
}
