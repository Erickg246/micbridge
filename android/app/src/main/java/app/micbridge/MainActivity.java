package app.micbridge;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    private final int bg = Color.rgb(16,21,30), panel = Color.rgb(23,30,41), green = Color.rgb(183,247,107), ink = Color.rgb(237,243,249), gray = Color.rgb(145,160,179);
    private EditText address, code;
    private TextView status;
    private Button connect, mute;
    private ProgressBar meter;
    private boolean probing = false;
    private String pendingHost, pendingCode, pendingFingerprint;
    private int pendingPort;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { render(intent.getStringExtra("message"), intent.getBooleanExtra("active", false)); meter.setProgress(intent.getIntExtra("level", 0)); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(26), dp(34), dp(26), dp(32)); scroll.addView(root);
        root.addView(text("M ↗    MicBridge", 25, ink, true));
        TextView eyebrow = text("ANDROID → LINUX", 11, green, true); eyebrow.setPadding(0,dp(32),0,dp(16)); root.addView(eyebrow);
        root.addView(text("Sua voz.\nSem cabos.", 43, ink, true));
        TextView description = text("Transforme seu celular em um microfone para o seu PC Linux.", 15, gray, false); description.setPadding(0,dp(16),0,dp(26)); root.addView(description);
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(20),dp(20),dp(20),dp(20)); card.setBackground(shape(panel)); root.addView(card);
        card.addView(text("Conectar ao PC", 20, ink, true));
        TextView hint = text("Abra o MicBridge no Linux e use os dados que aparecem na tela. Os dois aparelhos devem estar na mesma rede Wi-Fi.", 13, gray, false); hint.setPadding(0,dp(10),0,dp(20)); card.addView(hint);
        card.addView(text("ENDEREÇO DO PC", 10, gray, true));
        address = input("192.168.1.10:47321", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); card.addView(address);
        address.setText(getPreferences(0).getString("address", ""));
        card.addView(text("CÓDIGO DE CONEXÃO", 10, gray, true));
        code = input("6 dígitos", InputType.TYPE_CLASS_NUMBER); code.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(6) }); card.addView(code);
        connect = new Button(this); connect.setText(app.micbridge.R.string.connect); connect.setTextColor(bg); connect.setBackgroundTintList(android.content.res.ColorStateList.valueOf(green)); card.addView(connect);
        connect.setOnClickListener(v -> { if (MicService.active) { stopService(new Intent(this, MicService.class)); render("Transmissão encerrada", false); } else begin(); });
        status = text("Pronto para conectar", 13, gray, false); status.setPadding(0,dp(24),0,dp(12)); root.addView(status);
        meter = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); meter.setMax(100); meter.setProgressTintList(android.content.res.ColorStateList.valueOf(green)); root.addView(meter);
        mute = new Button(this); mute.setText(app.micbridge.R.string.mute); mute.setEnabled(false); root.addView(mute);
        mute.setOnClickListener(v -> startService(new Intent(this, MicService.class).setAction("MUTE")));
        TextView privacy = text("Áudio criptografado · Sem conta\nSem navegador · Sem servidor na internet", 11, gray, false); privacy.setGravity(Gravity.CENTER); privacy.setPadding(0,dp(25),0,0); root.addView(privacy);
        setContentView(scroll);
        root.setOnApplyWindowInsetsListener((view, insets) -> { if (Build.VERSION.SDK_INT >= 29) { android.graphics.Insets bars = insets.getSystemWindowInsets(); view.setPadding(dp(26)+bars.left, dp(24)+bars.top, dp(26)+bars.right, dp(24)+bars.bottom); } return insets; });
    }
    // Android 8–12 use our signature permission; Android 13+ also forbids exported receivers.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, new IntentFilter(MicService.STATUS), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, new IntentFilter(MicService.STATUS), "app.micbridge.INTERNAL", null);
        render(MicService.message, MicService.active);
    }
    @Override protected void onStop() { unregisterReceiver(receiver); super.onStop(); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(16)); return d; }
    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private EditText input(String hint, int type) { EditText t = new EditText(this); t.setSingleLine(); t.setTextSize(18); t.setTextColor(ink); t.setHintTextColor(gray); t.setHint(hint); t.setInputType(type); t.setPadding(0,dp(12),0,dp(12)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(18); t.setLayoutParams(p); return t; }
    private void render(String message, boolean active) { status.setText(message); connect.setText(active ? "Parar transmissão" : "Conectar microfone"); connect.setEnabled(!probing); address.setEnabled(!active && !probing); code.setEnabled(!active && !probing); mute.setEnabled(active); mute.setText(MicService.muted ? "Ativar microfone" : "Silenciar microfone"); }
    private void begin() {
        String target = address.getText().toString().trim(); String enteredCode = code.getText().toString().trim();
        if (!enteredCode.matches("[0-9]{6}")) { code.setError("Digite os 6 dígitos exibidos no PC"); return; }
        String host = target; int port = 47321;
        try {
            if (target.contains(":")) { String[] parts = target.split(":", -1); if (parts.length != 2) throw new IllegalArgumentException(); host = parts[0]; port = Integer.parseInt(parts[1]); }
            if (!host.matches("[a-zA-Z0-9.-]+") || port < 1 || port > 65535) throw new IllegalArgumentException();
        } catch (Exception error) { address.setError("Use o IP do PC, por exemplo 192.168.1.10:47321"); return; }
        pendingHost = host; pendingPort = port; pendingCode = enteredCode;
        getPreferences(0).edit().putString("address", target).apply();
        probing = true; render("Verificando identidade do PC…", false);
        new Thread(() -> {
            try {
                String fingerprint = Identity.discover(pendingHost, pendingPort);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    probing = false; render("Confirme a identidade do PC", false);
                    new AlertDialog.Builder(this).setTitle("Este é o seu PC?")
                        .setMessage("No Linux, abra ‘Conferir identidade do PC’. Confirme somente se a impressão digital for igual:\n\n" + fingerprint)
                        .setNegativeButton("Cancelar", (d, w) -> render("Conexão cancelada", false))
                        .setPositiveButton("É igual · conectar", (d, w) -> { pendingFingerprint = fingerprint; permissionsAndStart(); }).show();
                });
            } catch (Exception error) { runOnUiThread(() -> { if (isDestroyed()) return; probing = false; render("Não foi possível conectar. Confira IP, Wi-Fi e firewall. " + error.getMessage(), false); }); }
        }, "micbridge-identity").start();
    }
    private void permissionsAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 7); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 8);
        else startMic();
    }
    private void startMic() {
        Intent intent = new Intent(this, MicService.class).putExtra("host", pendingHost).putExtra("port", pendingPort).putExtra("code", pendingCode).putExtra("fingerprint", pendingFingerprint);
        try { startForegroundService(intent); render("Conectando…", true); }
        catch (Exception error) { render("Não foi possível iniciar: " + error.getMessage(), false); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 7) { if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) permissionsAndStart(); else render("Permissão de microfone necessária", false); }
        else if (request == 8) startMic();
    }
}
