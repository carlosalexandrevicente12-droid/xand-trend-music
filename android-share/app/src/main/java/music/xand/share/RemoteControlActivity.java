package music.xand.share;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/** Opt-in control channel; never touches posting, media, queue state or TikTok. */
public final class RemoteControlActivity extends Activity {
    private static final String BASE = "https://xand-music-analytics.carlosalexandrevicente12.workers.dev";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences credentials;
    private TextView state;
    private EditText pairingCode;
    private Button pairButton;
    private boolean foreground;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!foreground || credentials.getString("token", "").isEmpty()) return;
            executor.execute(() -> {
                try {
                    refreshStatus();
                    main.post(() -> state.setText("Motorola conectado à central. Último contato confirmado."));
                } catch (Exception error) {
                    main.post(() -> state.setText("Sem contato com a nuvem. Verifique conexão e implantação."));
                }
            });
            main.postDelayed(this, 20_000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        credentials = getSharedPreferences("xand_control", MODE_PRIVATE);
        final LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(38, 52, 38, 50);
        column.setBackgroundColor(Color.rgb(9, 9, 9));
        final ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);

        TextView title = text("XAND MUSIC  ·  MOTOROLA", 24, Color.rgb(255, 133, 0));
        column.addView(title);
        TextView description = text("Conexão opcional com o painel do iPhone. Apenas status e confirmação de atualização; nenhuma postagem é permitida.", 15, Color.WHITE);
        description.setPadding(0, 28, 0, 24);
        column.addView(description);

        state = text(credentials.getString("token", "").isEmpty()
            ? "Ainda não pareado." : "Pareado. Conexão será verificada.", 15, Color.rgb(190, 190, 190));
        state.setPadding(0, 0, 0, 28);
        column.addView(state);

        pairingCode = new EditText(this);
        pairingCode.setSingleLine(true);
        pairingCode.setTextColor(Color.WHITE);
        pairingCode.setHintTextColor(Color.LTGRAY);
        pairingCode.setHint("Código de 32 caracteres do iPhone");
        pairingCode.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        pairingCode.setMaxLines(1);
        column.addView(pairingCode);

        pairButton = new Button(this);
        pairButton.setText("Parear com código temporário");
        pairButton.setAllCaps(false);
        column.addView(pairButton);
        pairButton.setOnClickListener(v -> pair());

        TextView note = text("O código dura cinco minutos. Se o aplicativo ficar fechado, a consulta remota ficará temporariamente indisponível nesta primeira versão. Para revogar o acesso, use o painel do iPhone.", 13, Color.LTGRAY);
        note.setPadding(0, 28, 0, 0);
        column.addView(note);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void pair() {
        final String code = pairingCode.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (!code.matches("[a-f0-9]{32}")) {
            state.setText("Código inválido: confira os 32 caracteres.");
            return;
        }
        pairButton.setEnabled(false);
        state.setText("Pareando com a nuvem…");
        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject().put("code", code);
                JSONObject reply = request("POST", "/api/control/device/pair", payload, null);
                String token = reply.optString("token", "");
                String id = reply.optString("deviceId", "");
                if (!token.matches("[a-f0-9]{64}") || !id.matches("[0-9a-fA-F-]{36}")) {
                    throw new IllegalStateException("Resposta de pareamento inválida");
                }
                credentials.edit().putString("token", token).putString("device_id", id).apply();
                main.post(() -> {
                    pairingCode.setText("");
                    state.setText("Pareado. Aguardando primeiro contato.");
                    main.removeCallbacks(refresh);
                    if (foreground) main.post(refresh);
                });
            } catch (Exception error) {
                main.post(() -> state.setText("Falha no pareamento. Confira código, prazo e servidor."));
            } finally {
                main.post(() -> pairButton.setEnabled(true));
            }
        });
    }

    private void refreshStatus() throws Exception {
        String token = credentials.getString("token", "");
        if (token.isEmpty()) return;
        SharedPreferences queuePrefs = getSharedPreferences("xand_queue", MODE_PRIVATE);
        int count = new JSONArray(queuePrefs.getString("queue", "[]")).length();
        JSONObject heartbeat = new JSONObject().put("appVersion", "0.7").put("queueLength", count);
        request("POST", "/api/control/device/heartbeat", heartbeat, token);
        JSONArray commands = request("GET", "/api/control/device/commands", null, token).optJSONArray("commands");
        if (commands == null) return;
        for (int i = 0; i < commands.length(); i++) {
            JSONObject command = commands.optJSONObject(i);
            if (command == null || !"refresh_status".equals(command.optString("type", ""))) continue;
            String commandId = command.optString("id", "");
            if (!commandId.matches("[0-9a-fA-F-]{36}")) continue;
            JSONObject result = new JSONObject().put("commandId", commandId).put("state", "done");
            request("POST", "/api/control/device/result", result, token);
        }
    }

    private JSONObject request(String method, String path, JSONObject body, String token) throws Exception {
        URL url = new URL(BASE + path);
        if (!"https".equals(url.getProtocol()) || !"xand-music-analytics.carlosalexandrevicente12.workers.dev".equals(url.getHost())) {
            throw new SecurityException("Destino não autorizado");
        }
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(6_000);
        connection.setReadTimeout(6_000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream stream = connection.getOutputStream()) { stream.write(bytes); }
        }
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[1024];
                int received;
                while ((received = stream.read(chunk)) >= 0) {
                    if (buffer.size() + received > 16_384) throw new IllegalStateException("Resposta excessiva");
                    buffer.write(chunk, 0, received);
                }
                return new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        main.removeCallbacks(refresh);
        if (!credentials.getString("token", "").isEmpty()) main.post(refresh);
    }

    @Override protected void onPause() {
        foreground = false;
        main.removeCallbacks(refresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
