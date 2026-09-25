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
import android.widget.FrameLayout;
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
    private TextView statusView;
    private TextView modelChip;
    private ScrollView scrollView;
    private LinearLayout chatPanel;
    private LinearLayout workPanel;
    private TextView switchButton;
    private volatile boolean modelOperationRunning = false;

    private final int BG = Color.rgb(7, 9, 13);
    private final int PANEL = Color.rgb(18, 21, 28);
    private final int PANEL_2 = Color.rgb(26, 30, 39);
    private final int TEXT = Color.rgb(242, 245, 249);
    private final int MUTED = Color.rgb(145, 154, 168);
    private final int ACCENT = Color.rgb(110, 92, 255);
    private final int ACCENT_2 = Color.rgb(0, 197, 255);
    private final int GOOD = Color.rgb(70, 214, 139);

    private static final String DEFAULT_SYSTEM = "You are Vicky's private AI assistant. Be concise, practical and comfortable in Hinglish or English. Never claim a system or integration is verified unless there is current evidence.";

    // Start closest to the user, then fall back to major Bedrock regions.
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
        root.addView(buildModeSwitch());
        root.addView(buildModelBar());

        FrameLayout body = new FrameLayout(this);
        chatPanel = buildChatPanel();
        workPanel = buildWorkPanel();
        workPanel.setVisibility(View.GONE);
        body.addView(chatPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(workPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);

        if (prefs.getSecret("api_key").isEmpty()) {
            addBubble("assistant", "Add your Bedrock API key once. I’ll discover, test and connect to a working model automatically.");
        } else if (prefs.getString("model", "").isEmpty()) {
            addBubble("assistant", "Bedrock key saved. I’m checking available models and will connect only after a real test succeeds.");
        } else {
            addBubble("assistant", "Ready. Active model is verified and shown above. Use Switch if you want the next working model.");
        }
    }

    private View buildHeader() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, dp(4), 0, dp(8));

        TextView mark = new TextView(this);
        mark.setText("V");
        mark.setGravity(Gravity.CENTER);
        mark.setTextColor(Color.WHITE);
        mark.setTextSize(17);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setBackground(roundGradient(dp(18), ACCENT, ACCENT_2));
        top.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(12), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("Vicky AI");
        title.setTextSize(23);
        title.setTextColor(TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView sub = new TextView(this);
        sub.setText("Private AI workspace");
        sub.setTextSize(12);
        sub.setTextColor(MUTED);
        titleBlock.addView(title);
        titleBlock.addView(sub);
        top.addView(titleBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView settings = iconButton("⚙");
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return top;
    }

    private View buildModeSwitch() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setPadding(dp(4), dp(4), dp(4), dp(4));
        wrap.setBackground(round(PANEL, dp(24)));

        TextView chat = tabButton("Chat", true);
        TextView work = tabButton("Work", false);
        wrap.addView(chat, new LinearLayout.LayoutParams(0, dp(42), 1));
        wrap.addView(work, new LinearLayout.LayoutParams(0, dp(42), 1));

        chat.setOnClickListener(v -> {
            chatPanel.setVisibility(View.VISIBLE);
            workPanel.setVisibility(View.GONE);
            chat.setBackground(round(PANEL_2, dp(20)));
            work.setBackgroundColor(Color.TRANSPARENT);
            chat.setTextColor(TEXT);
            work.setTextColor(MUTED);
        });
        work.setOnClickListener(v -> {
            chatPanel.setVisibility(View.GONE);
            workPanel.setVisibility(View.VISIBLE);
            work.setBackground(round(PANEL_2, dp(20)));
            chat.setBackgroundColor(Color.TRANSPARENT);
            work.setTextColor(TEXT);
            chat.setTextColor(MUTED);
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(12));
        wrap.setLayoutParams(lp);
        return wrap;
    }

    private View buildModelBar() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(8), dp(9));
        row.setBackground(round(Color.rgb(13, 16, 22), dp(16)));

        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextColor(prefs.getString("model", "").isEmpty() ? MUTED : GOOD);
        dot.setTextSize(11);
        row.addView(dot);

        modelChip = new TextView(this);
        modelChip.setText(shortConnectionLabel());
        modelChip.setTextColor(TEXT);
        modelChip.setTextSize(11);
        modelChip.setSingleLine(true);
        modelChip.setPadding(dp(8), 0, dp(8), 0);
        row.addView(modelChip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        switchButton = new TextView(this);
        switchButton.setText("Switch");
        switchButton.setGravity(Gravity.CENTER);
        switchButton.setTextColor(Color.WHITE);
        switchButton.setTextSize(11);
        switchButton.setTypeface(Typeface.DEFAULT_BOLD);
        switchButton.setPadding(dp(11), dp(7), dp(11), dp(7));
        switchButton.setBackground(round(PANEL_2, dp(13)));
        switchButton.setOnClickListener(v -> switchToNextModel());
        row.addView(switchButton);

        statusView = new TextView(this);
        statusView.setText(initialStatus());
        statusView.setTextColor(MUTED);
        statusView.setTextSize(9);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setPadding(dp(10), 0, 0, 0);
        row.addView(statusView);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(lp);
        return row;
    }

    private LinearLayout buildChatPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(0, dp(10), 0, dp(14));
        scrollView.addView(chatContainer);
        panel.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        panel.addView(buildComposer());
        return panel;
    }

    private View buildComposer() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setGravity(Gravity.CENTER_VERTICAL);
        outer.setPadding(dp(10), dp(8), dp(8), dp(8));
        outer.setBackground(round(PANEL, dp(24)));

        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setGravity(Gravity.CENTER);
        plus.setTextColor(MUTED);
        plus.setTextSize(24);
        outer.addView(plus, new LinearLayout.LayoutParams(dp(40), dp(44)));

        promptInput = new EditText(this);
        promptInput.setHint("Message Vicky AI…");
        promptInput.setHintTextColor(Color.rgb(100, 110, 126));
        promptInput.setTextColor(TEXT);
        promptInput.setTextSize(16);
        promptInput.setMinLines(1);
        promptInput.setMaxLines(5);
        promptInput.setBackgroundColor(Color.TRANSPARENT);
        promptInput.setPadding(dp(6), 0, dp(8), 0);
        outer.addView(promptInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView send = new TextView(this);
        send.setText("↑");
        send.setGravity(Gravity.CENTER);
        send.setTextColor(Color.WHITE);
        send.setTextSize(22);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setBackground(roundGradient(dp(22), ACCENT, ACCENT_2));
        send.setOnClickListener(v -> sendMessage());
        outer.addView(send, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        outer.setLayoutParams(lp);
        return outer;
    }

    private LinearLayout buildWorkPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, dp(12), 0, 0);

        TextView heading = new TextView(this);
        heading.setText("Workspaces");
        heading.setTextColor(TEXT);
        heading.setTextSize(22);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(heading);

        TextView sub = new TextView(this);
        sub.setText("Keep project context separated and focused.");
        sub.setTextColor(MUTED);
        sub.setTextSize(13);
        sub.setPadding(0, dp(4), 0, dp(16));
        panel.addView(sub);

        panel.addView(workCard("General", "Everyday chat and quick tasks", "✦"));
        panel.addView(workCard("Projects", "Site, business and execution work", "▣"));
        panel.addView(workCard("Victor", "Governed technical workspace", "⚡"));
        panel.addView(workCard("RIO", "Revenue and business systems", "◎"));
        return panel;
    }

    private View workCard(String name, String desc, String icon) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(PANEL, dp(18)));

        TextView ico = new TextView(this);
        ico.setText(icon);
        ico.setTextColor(Color.rgb(170, 160, 255));
        ico.setTextSize(20);
        ico.setGravity(Gravity.CENTER);
        ico.setBackground(round(PANEL_2, dp(14)));
        card.addView(ico, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextColor(TEXT);
        n.setTextSize(16);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(MUTED);
        d.setTextSize(12);
        texts.addView(n);
        texts.addView(d);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(MUTED);
        arrow.setTextSize(28);
        card.addView(arrow);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);
        return card;
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(8));

        TextView info = new TextView(this);
        info.setText("Only your Bedrock API key is required. Region and model are detected and verified automatically.");
        info.setTextSize(13);
        info.setTextColor(Color.DKGRAY);
        info.setPadding(0, 0, 0, dp(12));
        box.addView(info);

        EditText key = field("Bedrock API key", prefs.getSecret("api_key"));
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(key);

        new AlertDialog.Builder(this)
                .setTitle("Bedrock Connection")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save & Auto Connect", (d, w) -> {
                    String newKey = key.getText().toString().trim();
                    if (newKey.isEmpty()) {
                        Toast.makeText(this, "Bedrock API key is required", Toast.LENGTH_LONG).show();
                        return;
                    }
                    prefs.putSecret("api_key", newKey);
                    prefs.putString("model", "");
                    prefs.putString("region", "");
                    prefs.putString("candidates", "");
                    prefs.putString("candidate_index", "-1");
                    updateConnectionUi("DISCOVERING");
                    autoConnect(false);
                })
                .show();
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(false);
        e.setPadding(0, dp(8), 0, dp(8));
        return e;
    }

    private String initialStatus() {
        if (prefs.getSecret("api_key").isEmpty()) return "KEY NEEDED";
        if (prefs.getString("model", "").isEmpty()) return "UNVERIFIED";
        return "VERIFIED";
    }

    private String shortConnectionLabel() {
        String region = prefs.getString("region", "");
        String model = prefs.getString("model", "");
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
                JSONArray candidates;
                String cached = prefs.getString("candidates", "");
                if (!forceRediscover && !cached.isEmpty()) {
                    candidates = new JSONArray(cached);
                } else {
                    candidates = discoverCandidates(apiKey);
                    prefs.putString("candidates", candidates.toString());
                    prefs.putString("candidate_index", "-1");
                }

                if (candidates.length() == 0) throw new Exception("No models were returned by Bedrock model discovery.");
                ModelChoice choice = findWorkingModel(apiKey, candidates, 0);
                saveChoice(choice, candidates);
                runOnUiThread(() -> {
                    updateConnectionUi("VERIFIED");
                    Toast.makeText(this, "Connected: " + choice.model, Toast.LENGTH_LONG).show();
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
                String cached = prefs.getString("candidates", "");
                JSONArray candidates = cached.isEmpty() ? discoverCandidates(apiKey) : new JSONArray(cached);
                if (cached.isEmpty()) prefs.putString("candidates", candidates.toString());

                int currentIndex = parseInt(prefs.getString("candidate_index", "-1"), -1);
                int start = currentIndex + 1;
                ModelChoice choice;
                try {
                    choice = findWorkingModel(apiKey, candidates, start);
                } catch (Exception end) {
                    // Wrap once so Switch cycles through the verified possibilities.
                    choice = findWorkingModel(apiKey, candidates, 0, Math.max(0, currentIndex));
                }
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

    private JSONArray discoverCandidates(String apiKey) throws Exception {
        JSONArray all = new JSONArray();
        Set<String> seen = new HashSet<>();
        Exception lastError = null;

        for (String region : AUTO_REGIONS) {
            try {
                JSONArray models = listMantleModels(apiKey, region);
                for (int i = 0; i < models.length(); i++) {
                    JSONObject item = models.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("id", "").trim();
                    if (id.isEmpty()) continue;
                    String key = region + "|" + id;
                    if (seen.add(key)) {
                        all.put(new JSONObject().put("region", region).put("model", id));
                    }
                }
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (all.length() == 0 && lastError != null) throw lastError;
        return all;
    }

    private JSONArray listMantleModels(String apiKey, String region) throws Exception {
        String endpoint = "https://bedrock-mantle." + region + ".api.aws/v1/models";
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        String raw = readResponse(conn, code);
        if (code < 200 || code >= 300) throw new Exception("Model discovery HTTP " + code + " in " + region);
        JSONObject json = new JSONObject(raw);
        return json.optJSONArray("data") == null ? new JSONArray() : json.getJSONArray("data");
    }

    private ModelChoice findWorkingModel(String apiKey, JSONArray candidates, int start) throws Exception {
        return findWorkingModel(apiKey, candidates, start, candidates.length());
    }

    private ModelChoice findWorkingModel(String apiKey, JSONArray candidates, int start, int endExclusive) throws Exception {
        int end = Math.min(endExclusive, candidates.length());
        Exception last = null;
        for (int i = Math.max(0, start); i < end; i++) {
            JSONObject c = candidates.getJSONObject(i);
            String region = c.optString("region");
            String model = c.optString("model");
            final String label = model;
            runOnUiThread(() -> {
                if (modelChip != null) modelChip.setText("Testing · " + label);
                if (statusView != null) statusView.setText("PING");
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
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "user").put("content", "Reply only OK"));
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0);
        body.put("max_tokens", 8);
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
        String apiKey = prefs.getSecret("api_key");
        String model = prefs.getString("model", "").trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Add your Bedrock API key first", Toast.LENGTH_LONG).show();
            showSettings();
            return;
        }
        if (model.isEmpty()) {
            Toast.makeText(this, "No verified model yet. Auto-connect is starting.", Toast.LENGTH_LONG).show();
            autoConnect(false);
            return;
        }

        promptInput.setText("");
        addBubble("user", text);
        statusView.setText("THINKING");

        new Thread(() -> {
            try {
                String answer = callBedrock(text);
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
        String region = prefs.getString("region", "").trim();
        String model = prefs.getString("model", "").trim();
        String apiKey = prefs.getSecret("api_key");

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", DEFAULT_SYSTEM));
        messages.put(new JSONObject().put("role", "user").put("content", userText));

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.4);
        JSONObject json = postChat(apiKey, region, body, 120000);
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "No text returned.");
    }

    private JSONObject postChat(String apiKey, String region, JSONObject body, int readTimeout) throws Exception {
        String endpoint = "https://bedrock-mantle." + region + ".api.aws/v1/chat/completions";
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(readTimeout);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }
        int code = conn.getResponseCode();
        String raw = readResponse(conn, code);
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + trimError(raw));
        return new JSONObject(raw);
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

    private String trimError(String raw) {
        if (raw == null) return "Unknown error";
        String value = raw.trim();
        if (value.length() > 420) return value.substring(0, 420) + "…";
        return value;
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; }
    }

    private void addBubble(String role, String text) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(15.5f);
        bubble.setLineSpacing(dp(2), 1.0f);
        bubble.setPadding(dp(15), dp(11), dp(15), dp(11));

        int width;
        if (role.equals("user")) {
            bubble.setTextColor(Color.WHITE);
            bubble.setBackground(roundGradient(dp(18), Color.rgb(83, 69, 205), Color.rgb(41, 116, 204)));
            width = dp(300);
        } else if (role.equals("error")) {
            bubble.setTextColor(Color.rgb(255, 191, 191));
            bubble.setBackground(round(Color.rgb(54, 25, 30), dp(18)));
            width = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            bubble.setTextColor(TEXT);
            bubble.setBackground(round(PANEL, dp(18)));
            width = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(role.equals("user") ? dp(42) : 0, dp(6), 0, dp(6));
        lp.gravity = role.equals("user") ? Gravity.END : Gravity.START;
        chatContainer.addView(bubble, lp);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private TextView iconButton(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(20);
        v.setTextColor(TEXT);
        v.setBackground(round(PANEL, dp(22)));
        return v;
    }

    private TextView tabButton(String text, boolean active) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(14);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(active ? TEXT : MUTED);
        v.setBackground(active ? round(PANEL_2, dp(20)) : null);
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
