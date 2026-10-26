package app.micbridge;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;

public final class MainActivity extends Activity {
    private final int bg = Color.rgb(16,21,30), panel = Color.rgb(23,30,41), green = Color.rgb(183,247,107), ink = Color.rgb(237,243,249), gray = Color.rgb(145,160,179);
    private TextView status, address, approvalText;
    private Button start, mute;
    private LinearLayout approvalCard;
    private ProgressBar meter;
    private String displayedRequestId = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { @Override public void run() { render(); handler.postDelayed(this, 2000); } };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { render(); meter.setProgress(intent.getIntExtra("level", 0)); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(bg);
        LinearLayout root = column(); root.setPadding(dp(26), dp(34), dp(26), dp(32)); scroll.addView(root);
        root.addView(text("M ↗    MicBridge", 25, ink, true));
        TextView eyebrow = text("CELULAR → PC · CRIPTOGRAFADO", 11, green, true); eyebrow.setPadding(0,dp(32),0,dp(16)); root.addView(eyebrow);
        root.addView(text("Seu microfone.\nPronto para o PC.", 36, ink, true));
        TextView description = text("Inicie o servidor aqui. No PC, digite o IP mostrado abaixo e clique em Conectar.", 15, gray, false); description.setPadding(0,dp(16),0,dp(26)); root.addView(description);
        LinearLayout card = column(); card.setPadding(dp(20),dp(20),dp(20),dp(20)); card.setBackground(shape(panel)); root.addView(card);
        card.addView(text("IP DESTE CELULAR", 11, gray, true));
        address = text("Procurando rede…", 24, green, true); address.setTextIsSelectable(true); address.setPadding(0,dp(16),0,dp(16)); card.addView(address);
        card.addView(text("Use a mesma rede Wi-Fi no celular e no PC. Porta automática: 47321.", 13, gray, false));
        start = button("Iniciar servidor"); start.setTextColor(bg); start.setBackgroundTintList(android.content.res.ColorStateList.valueOf(green)); card.addView(start);
        start.setOnClickListener(v -> { if (MicService.active) { stopService(new Intent(this, MicService.class)); } else permissionsAndStart(); });
        Button identity = button("Identidade do celular"); card.addView(identity);
        identity.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Identidade do celular").setMessage(MicService.fingerprint.isEmpty() ? "Inicie o servidor primeiro." : "Na primeira conexão, compare estes números com os mostrados no PC:\n\n" + MicService.fingerprint).setPositiveButton("Fechar", null).show());
        approvalCard = column(); approvalCard.setPadding(0,dp(20),0,dp(10)); root.addView(approvalCard);
        approvalText = text("", 16, ink, true); approvalCard.addView(approvalText);
        Button allow = button("Permitir PC"), reject = button("Recusar"); approvalCard.addView(allow); approvalCard.addView(reject);
        allow.setOnClickListener(v -> decide("APPROVE")); reject.setOnClickListener(v -> decide("REJECT"));
        status = text("", 13, gray, false); status.setPadding(0,dp(24),0,dp(12)); root.addView(status);
        meter = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); meter.setMax(100); meter.setProgressTintList(android.content.res.ColorStateList.valueOf(green)); root.addView(meter);
        mute = button("Silenciar microfone"); root.addView(mute);
        mute.setOnClickListener(v -> startService(new Intent(this, MicService.class).setAction("MUTE")));
        TextView privacy = text("Você autoriza cada conexão.\nO microfone só é capturado depois de permitir o PC.", 11, gray, false); privacy.setGravity(Gravity.CENTER); privacy.setPadding(0,dp(25),0,0); root.addView(privacy);
        setContentView(scroll);
        root.setOnApplyWindowInsetsListener((view, insets) -> { if (Build.VERSION.SDK_INT >= 29) { android.graphics.Insets bars = insets.getSystemWindowInsets(); view.setPadding(dp(26)+bars.left, dp(24)+bars.top, dp(26)+bars.right, dp(24)+bars.bottom); } return insets; });
    }
    // On Android 8–12, our signature permission restricts who can send this broadcast.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, new IntentFilter(MicService.STATUS), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, new IntentFilter(MicService.STATUS), "app.micbridge.INTERNAL", null);
        refresh.run();
    }
    @Override protected void onStop() { handler.removeCallbacks(refresh); unregisterReceiver(receiver); super.onStop(); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private GradientDrawable shape(int color) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(16)); return d; }
    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private Button button(String title) { Button button = new Button(this); button.setText(title); return button; }
    private String addresses() {
        try {
            List<String> result = new ArrayList<>();
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.getName().startsWith("tun") || network.getName().startsWith("rmnet")) continue;
                for (InetAddress ip : Collections.list(network.getInetAddresses())) if (ip instanceof Inet4Address && ip.isSiteLocalAddress()) result.add(ip.getHostAddress());
            }
            return result.isEmpty() ? "Conecte-se ao Wi-Fi" : android.text.TextUtils.join("\n", result);
        } catch (Exception error) { return "IP indisponível · confira o Wi-Fi"; }
    }
    private void render() {
        address.setText(addresses()); status.setText(MicService.message);
        start.setText(MicService.active ? "Parar servidor" : "Iniciar servidor");
        mute.setEnabled(MicService.streaming); mute.setText(MicService.muted ? "Ativar microfone" : "Silenciar microfone");
        displayedRequestId = MicService.pendingId;
        approvalCard.setVisibility(displayedRequestId.isEmpty() ? View.GONE : View.VISIBLE);
        approvalText.setText(getString(R.string.allow_pc, MicService.peer));
        if (!MicService.streaming) meter.setProgress(0);
    }
    private void decide(String action) {
        String request = displayedRequestId;
        if (!request.isEmpty()) startService(new Intent(this, MicService.class).setAction(action).putExtra("requestId", request));
    }
    private void permissionsAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 7); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 8);
        else startMic();
    }
    private void startMic() {
        try { startForegroundService(new Intent(this, MicService.class)); }
        catch (Exception error) { status.setText(getString(R.string.start_error, error.getMessage())); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 7) { if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) permissionsAndStart(); else status.setText(R.string.microphone_permission); }
        else if (request == 8) startMic();
    }
}
