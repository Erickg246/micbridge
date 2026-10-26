package app.micbridge;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;


public final class MicService extends Service {
    static final String STATUS = "app.micbridge.STATUS";
    static volatile boolean active = false, muted = false, streaming = false;
    static volatile String message = "Toque em Iniciar servidor", pendingId = "", peer = "";
    private volatile boolean running;
    private volatile Socket socket;
    private volatile ServerSocket server;
    private volatile AudioRecord recorder;
    private PowerManager.WakeLock wakeLock;
    private final Object approvalLock = new Object();
    private Boolean approved;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable renewWakeLock = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (wakeLock != null) { if (wakeLock.isHeld()) wakeLock.release(); wakeLock.acquire(30 * 60 * 1000L); }
            handler.postDelayed(this, 10 * 60 * 1000L);
        }
    };
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        String action = intent.getAction();
        if ("STOP".equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if ("APPROVE".equals(action) || "REJECT".equals(action)) {
            synchronized (approvalLock) {
                if (!pendingId.isEmpty() && pendingId.equals(intent.getStringExtra("requestId"))) { approved = "APPROVE".equals(action); approvalLock.notifyAll(); }
            }
            return START_NOT_STICKY;
        }
        if ("MUTE".equals(action)) { if (running) { muted = !muted; update(muted ? "Microfone silenciado" : "Transmitindo para o PC", 0); } return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { update("Permita o uso do microfone", 0); stopSelf(); return START_NOT_STICKY; }
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("micbridge", "Servidor MicBridge", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, MicService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "micbridge")
            .setContentTitle("MicBridge · Servidor iniciado").setContentText("Abra o app para autorizar o PC ou parar o servidor")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Parar servidor", stop).build()).build();
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(1, notification);
            running = true; active = true; muted = false;
            wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "micbridge:server");
            renewWakeLock.run(); update("Preparando servidor…", 0);
            new Thread(this::serve, "micbridge-server").start();
        } catch (Exception error) { active = false; update("Não foi possível iniciar: " + error.getMessage(), 0); stopSelf(); }
        return START_NOT_STICKY;
    }
    private void serve() {
        try {
            server = new ServerSocket(47321);
            if (!running) return;
            update("Servidor pronto · Digite o IP no PC", 0);
            while (running) {
                try {
                    socket = (Socket) server.accept(); socket.setSoTimeout(8000); socket.setTcpNoDelay(true);
                    if (!running) break;
                    PhoneProtocol.serve(socket, this::askApproval, new Microphone());
                } catch (Exception error) { if (running && streaming) update("PC desconectado: " + error.getMessage(), 0); }
                finally {
                    closeConnection();
                    if (running) update("Servidor pronto · Aguardando conexão do PC", 0);
                }
            }
        } catch (Exception error) { if (running) { active = false; update("Falha no servidor: " + error.getMessage(), 0); } }
        finally { running = false; active = false; release(); stopSelf(); }
    }
    private boolean askApproval() throws InterruptedException {
        synchronized (approvalLock) {
            approved = null; pendingId = UUID.randomUUID().toString(); peer = socket.getInetAddress().getHostAddress();
            update("PC " + peer + " quer usar o microfone", 0);
            long deadline = SystemClock.elapsedRealtime() + 60000;
            while (running && approved == null && SystemClock.elapsedRealtime() < deadline) approvalLock.wait(Math.max(1, deadline - SystemClock.elapsedRealtime()));
            boolean result = running && Boolean.TRUE.equals(approved); pendingId = ""; peer = "";
            return result;
        }
    }
    private final class Microphone implements PhoneProtocol.AudioSource {
        private long lastMeter;
        public void start() throws Exception {
            if (!running) throw new IOException("Servidor encerrado");
            int minimum = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IOException("Microfone não suporta 48 kHz");
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw new SecurityException("Permissão de microfone revogada");
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, 7680));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("Microfone indisponível");
            if (!running) throw new IOException("Servidor encerrado");
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IOException("Não foi possível iniciar a captura");
            streaming = true; update("Transmitindo para o PC", 0);
        }
        public int read(byte[] buffer) throws Exception {
            AudioRecord current = recorder; if (!running || current == null) return -1;
            int count = current.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
            if (count <= 0) throw new IOException("Captura interrompida");
            if (muted) Arrays.fill(buffer, 0, count, (byte) 0);
            if (SystemClock.elapsedRealtime() - lastMeter >= 150) {
                long sum = 0; for (int i = 0; i + 1 < count; i += 2) { int sample = (short) ((buffer[i] & 255) | (buffer[i+1] << 8)); sum += (long) sample * sample; }
                int level = (int) Math.min(100, Math.sqrt((double) sum / Math.max(1, count / 2)) / 32768 * 400);
                update(muted ? "Microfone silenciado" : "Transmitindo para o PC", level); lastMeter = SystemClock.elapsedRealtime();
            }
            return count;
        }
        public void close() { closeRecorder(); }
    }
    private synchronized void closeRecorder() {
        if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) {} recorder.release(); recorder = null; }
        streaming = false;
    }
    private void closeConnection() {
        Socket current = socket; socket = null;
        if (current != null) try { current.close(); } catch (IOException ignored) {}
        closeRecorder(); pendingId = ""; peer = "";
    }
    private void release() {
        ServerSocket listener = server; server = null;
        if (listener != null) try { listener.close(); } catch (IOException ignored) {}
        closeConnection();
        synchronized (approvalLock) { approvalLock.notifyAll(); }
    }
    private void update(String text, int level) {
        message = text;
        sendBroadcast(new Intent(STATUS).setPackage(getPackageName()).putExtra("level", level), "app.micbridge.INTERNAL");
    }
    @Override public void onDestroy() {
        running = false; active = false; handler.removeCallbacks(renewWakeLock); release();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (!message.startsWith("Falha") && !message.startsWith("Não foi possível")) update("Servidor parado", 0);
        super.onDestroy();
    }
}
