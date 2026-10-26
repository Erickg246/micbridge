package app.micbridge;
import java.io.*;
import java.security.KeyStore;
import javax.net.ssl.*;
public final class PhoneServerCheck {
    public static void main(String[] args) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = new FileInputStream(args[0])) { store.load(input, "test-only".toCharArray()); }
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); factory.init(store, "test-only".toCharArray());
        SSLContext context = SSLContext.getInstance("TLS"); context.init(factory.getKeyManagers(), null, null);
        try (SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(0, 2, java.net.InetAddress.getLoopbackAddress())) {
            System.out.println(server.getLocalPort()); System.out.flush();
            for (int attempt = 0; attempt < 3; attempt++) {
                final boolean allow = attempt == 2;
                try (SSLSocket socket = (SSLSocket) server.accept()) {
                    socket.setSoTimeout(6000); socket.startHandshake();
                    PhoneProtocol.serve(socket, () -> allow, new PhoneProtocol.AudioSource() {
                        boolean sent;
                        public void start() { if (!allow) throw new AssertionError("Capture before approval"); }
                        public int read(byte[] buffer) { if (sent) return -1; sent = true; buffer[0] = 1; buffer[1] = 2; buffer[2] = 3; buffer[3] = 4; return 4; }
                        public void close() {}
                    });
                } catch (IOException error) { if (attempt != 0) throw error; }
            }
        }
    }
}
