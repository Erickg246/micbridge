package app.micbridge;
import java.io.*;
import java.net.*;
public final class PhoneServerCheck {
    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(0, 2, InetAddress.getLoopbackAddress())) {
            System.out.println(server.getLocalPort()); System.out.flush();
            for (int attempt = 0; attempt < 2; attempt++) {
                final boolean allow = attempt == 1;
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(6000);
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
