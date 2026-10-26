package app.micbridge;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class PhoneProtocol {
    interface Approval { boolean allow() throws Exception; }
    interface AudioSource extends AutoCloseable {
        void start() throws Exception;
        int read(byte[] buffer) throws Exception;
        void close();
    }
    static void serve(Socket socket, Approval approval, AudioSource source) throws Exception {
        try (AudioSource audio = source) {
            InputStream input = socket.getInputStream(); OutputStream output = socket.getOutputStream();
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            int next;
            while ((next = input.read()) != -1 && next != '\n') {
                if (header.size() >= 64) throw new IOException("Cabeçalho inválido"); header.write(next);
            }
            if (next == -1) return; // Certificate discovery disconnects without requesting audio.
            if (!header.toString(StandardCharsets.US_ASCII.name()).equals("MICBRIDGE/2")) { output.write("ERROR version\n".getBytes(StandardCharsets.US_ASCII)); return; }
            output.write("WAIT\n".getBytes(StandardCharsets.US_ASCII)); output.flush();
            if (!approval.allow()) { output.write("DENIED\n".getBytes(StandardCharsets.US_ASCII)); output.flush(); return; }
            audio.start();
            output.write("OK 48000 1 s16le\n".getBytes(StandardCharsets.US_ASCII)); output.flush();
            byte[] pcm = new byte[1920];
            while ((next = audio.read(pcm)) >= 0) {
                if (next == 0) continue;
                if (next > pcm.length || next % 2 != 0) throw new IOException("Bloco de áudio inválido");
                output.write(pcm, 0, next);
            }
        }
    }
}
