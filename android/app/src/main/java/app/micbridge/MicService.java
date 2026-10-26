package app.micbridge;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.net.ssl.SSLSocket;

public final class MicService extends Service {
    static final String STATUS = "app.micbridge.STATUS";
    static volatile boolean active = false, muted = false;
    static volatile String message = "Pronto para conectar";
    private volatile boolean running;
    private volatile SSLSocket socket;
    private volatile AudioRecord recorder;
    private PowerManager.WakeLock wakeLock;
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if ("STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if ("MUTE".equals(intent.getAction())) { muted = !muted; update(muted ? "Microfone silenciado" : "Transmitindo para o Linux", 0); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { update("Permita o uso do microfone", 0); stopSelf(); return START_NOT_STICKY; }
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("micbridge", "Transmissão do microfone", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, MicService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "micbridge")
            .setContentTitle("MicBridge · Microfone ativo").setContentText("Seu áudio está sendo enviado ao PC")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Parar transmissão", stop).build()).build();
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(1, notification);
            running = true; active = true; muted = false;
            wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "micbridge:audio"); wakeLock.acquire(30 * 60 * 1000L);
            update("Conectando ao PC…", 0);
            new Thread(() -> transmit(intent), "micbridge-audio").start();
        } catch (Exception error) { active = false; update("Não foi possível iniciar: " + error.getMessage(), 0); stopSelf(); }
        return START_NOT_STICKY;
    }
    private void transmit(Intent intent) {
        try {
            socket = Identity.connect(intent.getStringExtra("host"), intent.getIntExtra("port", 47321), intent.getStringExtra("fingerprint"), null);
            if (!running) return;
            OutputStream output = socket.getOutputStream();
            String code = intent.getStringExtra("code");
            if (code == null || !code.matches("[0-9]{6}")) throw new IOException("Código inválido");
            output.write(("{\"code\":\"" + code + "\",\"rate\":48000,\"channels\":1,\"format\":\"s16le\"}\n").getBytes(StandardCharsets.UTF_8)); output.flush();
            StringBuilder reply = new StringBuilder(); int value;
            while ((value = socket.getInputStream().read()) != -1 && value != '\n' && reply.length() < 128) reply.append((char) value);
            if (!reply.toString().equals("OK")) throw new IOException("PC recusou a conexão: " + reply);
            int minimum = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IOException("Este celular não suporta áudio mono em 48 kHz");
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw new SecurityException("Permissão de microfone revogada");
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, 7680));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("Microfone indisponível");
            if (!running) return;
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IOException("Não foi possível capturar áudio");
            update("Transmitindo para o Linux", 0);
            byte[] buffer = new byte[1920]; long lastMeter = 0; long renewed = SystemClock.elapsedRealtime();
            while (running) {
                if (SystemClock.elapsedRealtime() - renewed > 10 * 60 * 1000L) {
                    synchronized (this) { if (wakeLock != null && running) { if (wakeLock.isHeld()) wakeLock.release(); wakeLock.acquire(30 * 60 * 1000L); } }
                    renewed = SystemClock.elapsedRealtime();
                }
                int count = recorder.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                if (count <= 0) throw new IOException("Captura interrompida (" + count + ")");
                if (muted) Arrays.fill(buffer, 0, count, (byte) 0);
                output.write(buffer, 0, count);
                if (System.currentTimeMillis() - lastMeter >= 150) {
                    long sum = 0; for (int i = 0; i + 1 < count; i += 2) { int sample = (short) ((buffer[i] & 255) | (buffer[i+1] << 8)); sum += (long) sample * sample; }
                    int level = (int) Math.min(100, Math.sqrt((double) sum / Math.max(1, count / 2)) / 32768 * 400);
                    update(muted ? "Microfone silenciado" : "Transmitindo para o Linux", level); lastMeter = System.currentTimeMillis();
                }
            }
        } catch (Exception error) { if (running) { active = false; update("Conexão encerrada: " + error.getMessage(), 0); } }
        finally { running = false; active = false; release(); stopSelf(); }
    }
    private synchronized void release() {
        if (socket != null) { try { socket.close(); } catch (IOException ignored) {} socket = null; }
        if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) {} recorder.release(); recorder = null; }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }
    private void update(String text, int level) {
        message = text;
        sendBroadcast(new Intent(STATUS).setPackage(getPackageName()).putExtra("message", text).putExtra("active", active).putExtra("muted", muted).putExtra("level", level), "app.micbridge.INTERNAL");
    }
    @Override public void onDestroy() {
        running = false; active = false; release();
        if (!message.startsWith("Conexão encerrada") && !message.startsWith("Não foi possível")) update("Transmissão encerrada", 0);
        super.onDestroy();
    }
}
