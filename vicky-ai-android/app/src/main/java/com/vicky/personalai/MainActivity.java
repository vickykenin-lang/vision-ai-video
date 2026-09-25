package com.vicky.personalai;

import android.app.AlertDialog;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private SecurePrefs prefs;
    private LinearLayout chatContainer;
    private EditText promptInput;
    private TextView modelChip;
    private TextView statusView;
    private ScrollView scrollView;
    private volatile boolean modelOperationRunning = false;

    private final int BG = Color.rgb(7, 9, 13);
    private final int PANEL = Color.rgb(18, 21, 28);
    private final int PANEL2 = Color.rgb(27, 31, 40);
    private final int TEXT = Color.rgb(242, 245, 249);
    private final int MUTED = Color.rgb(145, 154, 168);
    private final int ACCENT = Color.rgb(110, 92, 255);
    private final int ACCENT2 = Color.rgb(0, 197, 255);

    private static final String DEFAULT_SYSTEM = "You are Vicky's private AI assistant. Be concise, practical and comfortable in Hinglish or English. Never claim a system or integration is verified unless there is current evidence.";

    private static final String[] AUTO_REGIONS = new String[]{
            "ap-south-1", "ap-south-2", "ap-southeast-1", "ap-southeast-2",
            "us-east-1", "us-west-2", "eu-west-1", "eu-west-2", "eu-central-1"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new SecurePrefs(this);
        buildUi();
        if (!prefs.getSecret("api_key").isEmpty() && prefs.getString("model", "").isEmpty()) {
            autoConnect(false);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(12));
        root.setBackgroundColor(BG);

        root.addView(buildHeader());
        root.addView(buildTabs());
        root.addView(buildModelBar());

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(0, dp(10), 0, dp(14));
        scrollView.addView(chatContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(buildComposer());

        setContentView(root);

        if (prefs.getSecret("api_key").isEmpty()) {
            addBubble("assistant", "Add your Bedrock API key once. I’ll discover, ping and connect to a working model automatically.");
        } else if (prefs.getString("model", "").isEmpty()) {
            addBubble("assistant", "Bedrock key saved. I’m checking models and will connect only after a real request succeeds.");
        } else {
            addBubble("assistant", "Ready. The active verified model is shown above. Tap Switch to move to the next working model.");
        }
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(8));

        TextView mark = new TextView(this);
        mark.setText("V");
        mark.setGravity(Gravity.CENTER);
        mark.setTextColor(Color.WHITE);
        mark.setTextSize(17);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setBackground(roundGradient(dp(18), ACCENT, ACCENT2));
        row.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, 0, 0);
        TextView title = label("Vicky AI", 23, TEXT, true);
        TextView sub = label("Private AI workspace", 12, MUTED, false);
        titles.addView(title);
        titles.addView(sub);
        row.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView settings = label("⚙", 20, TEXT, false);
        settings.setGravity(Gravity.CENTER);
        settings.setBackground(round(PANEL, dp(22)));
        settings.setOnClickListener(v -> showSettings());
        row.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return row;
    }

    private View buildTabs() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(round(PANEL, dp(24)));

        TextView chat = label("Chat", 14, TEXT, true);
        chat.setGravity(Gravity.CENTER);
        chat.setBackground(round(PANEL2, dp(20)));
        TextView work = label("Work", 14, MUTED, true);
        work.setGravity(Gravity.CENTER);
        tabs.addView(chat, new LinearLayout.LayoutParams(0, dp(42), 1));
        tabs.addView(work, new LinearLayout.LayoutParams(0, dp(42), 1));

        work.setOnClickListener(v -> Toast.makeText(this, "Work mode will remain isolated from chat until enabled.", Toast.LENGTH_SHORT).show());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(12));
        tabs.setLayoutParams(lp);
        return tabs;
    }

    private View buildModelBar() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(8), dp(9));
        row.setBackground(round(Color.rgb(13, 16, 22), dp(16)));

        TextView dot = label("●", 11, prefs.getString("model", "").isEmpty() ? MUTED : Color.rgb(70, 214, 139), false);
        row.addView(dot);

        modelChip = label(shortConnectionLabel(), 11, TEXT, false);
        modelChip.setSingleLine(true);
        modelChip.setPadding(dp(8), 0, dp(8), 0);
        row.addView(modelChip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView switchModel = label("Switch", 11, Color.WHITE, true);
        switchModel.setGravity(Gravity.CENTER);
        switchModel.setPadding(dp(11), dp(7), dp(11), dp(7));
        switchModel.setBackground(round(PANEL2, dp(13)));
        switchModel.setOnClickListener(v -> switchToNextModel());
        row.addView(switchModel);

        statusView = label(initialStatus(), 9, MUTED, true);
        statusView.setPadding(dp(10), 0, 0, 0);
        row.addView(statusView);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(lp);
        return row;
    }

    private View buildComposer() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(8), dp(8));
        row.setBackground(round(PANEL, dp(24)));

        TextView plus = label("＋", 24, MUTED, false);
        plus.setGravity(Gravity.CENTER);
        row.addView(plus, new LinearLayout.LayoutParams(dp(40), dp(44)));

        promptInput = new EditText(this);
        promptInput.setHint("Message Vicky AI…");
        promptInput.setHintTextColor(Color.rgb(100, 110, 126));
        promptInput.setTextColor(TEXT);
        promptInput.setTextSize(16);
        promptInput.setMinLines(1);
        promptInput.setMaxLines(5);
        promptInput.setBackgroundColor(Color.TRANSPARENT);
        row.addView(promptInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView send = label("↑", 22, Color.WHITE, true);
        send.setGravity(Gravity.CENTER);
        send.setBackground(roundGradient(dp(22), ACCENT, ACCENT2));
        send.setOnClickListener(v -> sendMessage());
        row.addView(send, new LinearLayout.LayoutParams(dp(46), dp(46)));
        return row;
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(8));

        TextView info = label("Only your Bedrock API key is required. Region and model are discovered and verified automatically.", 13, Color.DKGRAY, false);
        info.setPadding(0, 0, 0, dp(12));
        box.addView(info);

        EditText key = new EditText(this);
        key.setHint("Bedrock API key");
        key.setText(prefs.getSecret("api_key"));
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(key);

        new AlertDialog.Builder(this)
                .setTitle("Bedrock Connection")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save & Auto Connect", (d, w) -> {
                    String value = key.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(this, "Bedrock API key is required", Toast.LENGTH_LONG).show();
                        return;
                    }
                    prefs.putSecret("api_key", value);
                    prefs.putString("model", "");
                    prefs.putString("region", "");
                    prefs.putString("candidates", "");
                    prefs.putString("candidate_index", "-1");
                    updateConnectionUi("DISCOVERING");
                    autoConnect(true);
                }).show();
    }

    private String initialStatus() {
        if (prefs.getSecret("api_key").isEmpty()) return "KEY NEEDED";
        return prefs.getString("model", "").isEmpty() ? "UNVERIFIED" : "VERIFIED";
    }

    private String shortConnectionLabel() {
        String model = prefs.getString("model", "");
        String region = prefs.getString("region", "");
        if (prefs.getSecret("api_key").isEmpty()) return "Bedrock · API key not set";
        if (model.isEmpty()) return "Bedrock · finding a working model…";
        return model + " · " + region;
    }

    private void updateConnectionUi(String status) {
        if (modelChip != null) modelChip.setText(shortConnectionLabel());
        if (statusView != null) statusView.setText(status);
    }

    private void autoConnect(boolean forceRediscover) {
        if (modelOperationRunning) return;
        final String apiKey = prefs.getSecret("api_key");
        if (apiKey.isEmpty()) {
            showSettings();
            return;
        }
        modelOperationRunning = true;
        updateConnectionUi("DISCOVERING");

        new Thread(() -> {
            try {
                JSONArray candidates = loadOrDiscover(apiKey, forceRediscover);
                final ModelChoice choice = findWorkingModel(apiKey, candidates, 0, candidates.length());
                saveChoice(choice, candidates);
                runOnUiThread(() -> {
                    updateConnectionUi("VERIFIED");
                    addBubble("assistant", "Connected and verified.\n" + choice.model + "\nRegion: " + choice.region);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    prefs.putString("model", "");
                    prefs.putString("region", "");
                    updateConnectionUi("NO MODEL");
                    addBubble("error", "Bedrock auto-connect failed\n" + safeMessage(e));
                });
            } finally {
                modelOperationRunning = false;
            }
        }).start();
    }

    private void switchToNextModel() {
        if (modelOperationRunning) {
            Toast.makeText(this, "Model check already running", Toast.LENGTH_SHORT).show();
            return;
        }
        final String apiKey = prefs.getSecret("api_key");
        if (apiKey.isEmpty()) {
            showSettings();
            return;
        }

        modelOperationRunning = true;
        updateConnectionUi("SWITCHING");
        new Thread(() -> {
            try {
                JSONArray candidates = loadOrDiscover(apiKey, false);
                int current = parseInt(prefs.getString("candidate_index", "-1"), -1);
                ModelChoice found;
                try {
                    found = findWorkingModel(apiKey, candidates, current + 1, candidates.length());
                } catch (Exception noLater) {
                    found = findWorkingModel(apiKey, candidates, 0, Math.max(0, current));
                }
                final ModelChoice choice = found;
                saveChoice(choice, candidates);
                runOnUiThread(() -> {
                    updateConnectionUi("VERIFIED");
                    Toast.makeText(this, "Switched to " + choice.model, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    updateConnectionUi(prefs.getString("model", "").isEmpty() ? "NO MODEL" : "VERIFIED");
                    Toast.makeText(this, "No other working model found", Toast.LENGTH_LONG).show();
                });
            } finally {
                modelOperationRunning = false;
            }
        }).start();
    }

    private JSONArray loadOrDiscover(String apiKey, boolean force) throws Exception {
        String cached = prefs.getString("candidates", "");
        if (!force && !cached.isEmpty()) return new JSONArray(cached);
        JSONArray candidates = discoverCandidates(apiKey);
        prefs.putString("candidates", candidates.toString());
        prefs.putString("candidate_index", "-1");
        return candidates;
    }

    private JSONArray discoverCandidates(String apiKey) throws Exception {
        JSONArray all = new JSONArray();
        Set<String> seen = new HashSet<>();
        Exception last = null;
        for (String region : AUTO_REGIONS) {
            try {
                JSONArray models = listModels(apiKey, region);
                for (int i = 0; i < models.length(); i++) {
                    JSONObject item = models.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("id", "").trim();
                    if (id.isEmpty()) continue;
                    String unique = region + "|" + id;
                    if (seen.add(unique)) all.put(new JSONObject().put("region", region).put("model", id));
                }
            } catch (Exception e) {
                last = e;
            }
        }
        if (all.length() == 0) throw last == null ? new Exception("No models returned by Bedrock") : last;
        return all;
    }

    private JSONArray listModels(String apiKey, String region) throws Exception {
        String endpoint = "https://bedrock-mantle." + region + ".api.aws/v1/models";
        HttpURLConnection conn = open(endpoint, "GET", apiKey, 15000);
        int code = conn.getResponseCode();
        String raw = readResponse(conn, code);
        if (code < 200 || code >= 300) throw new Exception("Model discovery HTTP " + code + " in " + region);
        JSONObject json = new JSONObject(raw);
        JSONArray data = json.optJSONArray("data");
        return data == null ? new JSONArray() : data;
    }

    private ModelChoice findWorkingModel(String apiKey, JSONArray candidates, int start, int endExclusive) throws Exception {
        Exception last = null;
        int end = Math.min(endExclusive, candidates.length());
        for (int i = Math.max(0, start); i < end; i++) {
            JSONObject c = candidates.getJSONObject(i);
            String region = c.optString("region", "");
            String model = c.optString("model", "");
            final String display = model;
            runOnUiThread(() -> {
                modelChip.setText("Testing · " + display);
                statusView.setText("PING");
            });
            try {
                pingModel(apiKey, region, model);
                return new ModelChoice(region, model, i);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last == null ? new Exception("No working Chat Completions model found") : last;
    }

    private void pingModel(String apiKey, String region, String model) throws Exception {
        JSONArray messages = new JSONArray().put(new JSONObject().put("role", "user").put("content", "Reply only OK"));
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("temperature", 0)
                .put("max_tokens", 8);
        postChat(apiKey, region, body, 20000);
    }

    private void saveChoice(ModelChoice choice, JSONArray candidates) {
        prefs.putString("region", choice.region);
        prefs.putString("model", choice.model);
        prefs.putString("candidate_index", Integer.toString(choice.index));
        prefs.putString("candidates", candidates.toString());
    }

    private void sendMessage() {
        String text = promptInput.getText().toString().trim();
        if (text.isEmpty()) return;
        if (prefs.getSecret("api_key").isEmpty()) {
            showSettings();
            return;
        }
        if (prefs.getString("model", "").isEmpty()) {
            Toast.makeText(this, "Finding a working Bedrock model first", Toast.LENGTH_SHORT).show();
            autoConnect(false);
            return;
        }

        promptInput.setText("");
        addBubble("user", text);
        statusView.setText("THINKING");
        new Thread(() -> {
            try {
                final String answer = callBedrock(text);
                runOnUiThread(() -> {
                    addBubble("assistant", answer);
                    updateConnectionUi("VERIFIED");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    addBubble("error", "Request failed\n" + safeMessage(e));
                    statusView.setText("FAILED");
                });
            }
        }).start();
    }

    private String callBedrock(String userText) throws Exception {
        String region = prefs.getString("region", "");
        String model = prefs.getString("model", "");
        String apiKey = prefs.getSecret("api_key");

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", DEFAULT_SYSTEM));
        messages.put(new JSONObject().put("role", "user").put("content", userText));
        JSONObject body = new JSONObject().put("model", model).put("messages", messages).put("temperature", 0.4);
        JSONObject json = postChat(apiKey, region, body, 120000);
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "No text returned.");
    }

    private JSONObject postChat(String apiKey, String region, JSONObject body, int timeout) throws Exception {
        String endpoint = "https://bedrock-mantle." + region + ".api.aws/v1/chat/completions";
        HttpURLConnection conn = open(endpoint, "POST", apiKey, timeout);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) { os.write(payload); }
        int code = conn.getResponseCode();
        String raw = readResponse(conn, code);
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + trimError(raw));
        return new JSONObject(raw);
    }

    private HttpURLConnection open(String endpoint, String method, String apiKey, int timeout) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(timeout);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    private String readResponse(HttpURLConnection conn, int code) throws Exception {
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) return "";
        StringBuilder raw = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) raw.append(line);
        }
        return raw.toString();
    }

    private void addBubble(String role, String text) {
        TextView bubble = label(text, 15, role.equals("error") ? Color.rgb(255, 191, 191) : TEXT, false);
        bubble.setLineSpacing(dp(2), 1.0f);
        bubble.setPadding(dp(15), dp(11), dp(15), dp(11));
        int width = role.equals("user") ? dp(300) : ViewGroup.LayoutParams.MATCH_PARENT;
        bubble.setBackground(role.equals("user")
                ? roundGradient(dp(18), Color.rgb(83, 69, 205), Color.rgb(41, 116, 204))
                : round(role.equals("error") ? Color.rgb(54, 25, 30) : PANEL, dp(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(role.equals("user") ? dp(42) : 0, dp(6), 0, dp(6));
        lp.gravity = role.equals("user") ? Gravity.END : Gravity.START;
        chatContainer.addView(bubble, lp);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private GradientDrawable roundGradient(int radius, int c1, int c2) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        d.setCornerRadius(radius);
        return d;
    }

    private String trimError(String raw) {
        if (raw == null) return "Unknown error";
        String s = raw.trim();
        return s.length() > 420 ? s.substring(0, 420) + "…" : s;
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class ModelChoice {
        final String region;
        final String model;
        final int index;
        ModelChoice(String region, String model, int index) {
            this.region = region;
            this.model = model;
            this.index = index;
        }
    }
}
