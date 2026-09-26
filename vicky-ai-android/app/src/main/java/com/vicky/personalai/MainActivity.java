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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private SecurePrefs prefs;
    private LinearLayout chatContainer;
    private EditText promptInput;
    private TextView modelChip;
    private TextView statusView;
    private TextView sandboxChip;
    private ScrollView scrollView;
    private volatile boolean modelOperationRunning = false;

    private final int BG = Color.rgb(7, 9, 13);
    private final int PANEL = Color.rgb(18, 21, 28);
    private final int PANEL2 = Color.rgb(27, 31, 40);
    private final int TEXT = Color.rgb(242, 245, 249);
    private final int MUTED = Color.rgb(145, 154, 168);
    private final int ACCENT = Color.rgb(110, 92, 255);
    private final int ACCENT2 = Color.rgb(0, 197, 255);
    private final int SAFE = Color.rgb(57, 194, 123);
    private final int DANGER = Color.rgb(235, 84, 84);

    private static final String DEFAULT_SYSTEM = "You are Vicky's private AI assistant. Be concise, practical and comfortable in Hinglish or English. Never claim a system or integration is verified unless there is current evidence.";
    private static final String PREF_SANDBOX_KILL = "sandbox_kill";
    private static final String PREF_AUDIT = "sandbox_audit";
    private static final int AUDIT_MAX_CHARS = 14000;

    private static final String[] AUTO_REGIONS = new String[]{
            "ap-south-1", "ap-south-2", "ap-southeast-1", "ap-southeast-2",
            "us-east-1", "us-west-2", "eu-west-1", "eu-west-2", "eu-central-1"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new SecurePrefs(this);
        if (prefs.getString(PREF_SANDBOX_KILL, "").isEmpty()) prefs.putString(PREF_SANDBOX_KILL, "0");
        audit("APP_START", "sandbox=safe");
        buildUi();
        if (!isKillSwitchOn() && !prefs.getSecret("api_key").isEmpty() && prefs.getString("model", "").isEmpty()) {
            autoConnect(false);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(12));
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

        if (isKillSwitchOn()) {
            addBubble("error", "Sandbox kill switch is ON. AI/network actions are blocked until you re-enable them.");
        } else if (prefs.getSecret("api_key").isEmpty()) {
            addBubble("assistant", "Sandbox SAFE is active. Add your Bedrock API key once; only Bedrock endpoints are permitted.");
        } else if (prefs.getString("model", "").isEmpty()) {
            addBubble("assistant", "Sandbox SAFE is active. I’m checking Bedrock models and will connect only after a real request succeeds.");
        } else {
            addBubble("assistant", "Sandbox SAFE is active. The verified model is shown above. Tap Switch to move to the next working model.");
        }
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(6));

        TextView mark = new TextView(this);
        mark.setText("V");
        mark.setGravity(Gravity.CENTER);
        mark.setTextColor(Color.WHITE);
        mark.setTextSize(16);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setBackground(roundGradient(dp(17), ACCENT, ACCENT2));
        row.addView(mark, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(11), 0, 0, 0);
        TextView title = label("Vicky AI", 21, TEXT, true);
        TextView sub = label("Private AI workspace", 11, MUTED, false);
        titles.addView(title);
        titles.addView(sub);
        row.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        sandboxChip = label(isKillSwitchOn() ? "KILLED" : "SAFE", 10, Color.WHITE, true);
        sandboxChip.setGravity(Gravity.CENTER);
        sandboxChip.setPadding(dp(10), dp(7), dp(10), dp(7));
        sandboxChip.setBackground(round(isKillSwitchOn() ? DANGER : SAFE, dp(13)));
        sandboxChip.setOnClickListener(v -> showSandbox());
        row.addView(sandboxChip);

        TextView settings = label("⚙", 18, TEXT, false);
        settings.setGravity(Gravity.CENTER);
        settings.setBackground(round(PANEL, dp(20)));
        settings.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(40), dp(40));
        slp.setMargins(dp(8), 0, 0, 0);
        row.addView(settings, slp);
        return row;
    }

    private View buildTabs() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(round(PANEL, dp(22)));

        TextView chat = label("Chat", 13, TEXT, true);
        chat.setGravity(Gravity.CENTER);
        chat.setBackground(round(PANEL2, dp(18)));
        TextView work = label("Work", 13, MUTED, true);
        work.setGravity(Gravity.CENTER);
        tabs.addView(chat, new LinearLayout.LayoutParams(0, dp(38), 1));
        tabs.addView(work, new LinearLayout.LayoutParams(0, dp(38), 1));
        work.setOnClickListener(v -> Toast.makeText(this, "Work mode remains isolated until tools are explicitly enabled.", Toast.LENGTH_SHORT).show());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(7), 0, dp(10));
        tabs.setLayoutParams(lp);
        return tabs;
    }

    private View buildModelBar() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(8), dp(8));
        row.setBackground(round(Color.rgb(13, 16, 22), dp(15)));

        TextView dot = label("●", 11, prefs.getString("model", "").isEmpty() ? MUTED : SAFE, false);
        row.addView(dot);

        modelChip = label(shortConnectionLabel(), 10.5f, TEXT, false);
        modelChip.setSingleLine(true);
        modelChip.setPadding(dp(8), 0, dp(8), 0);
        row.addView(modelChip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView switchModel = label("Switch", 10.5f, Color.WHITE, true);
        switchModel.setGravity(Gravity.CENTER);
        switchModel.setPadding(dp(10), dp(6), dp(10), dp(6));
        switchModel.setBackground(round(PANEL2, dp(12)));
        switchModel.setOnClickListener(v -> switchToNextModel());
        row.addView(switchModel);

        statusView = label(initialStatus(), 8.5f, MUTED, true);
        statusView.setPadding(dp(9), 0, 0, 0);
        row.addView(statusView);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
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

    private void showSandbox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(8));

        TextView summary = label(
                "MODE: SAFE\n\n" +
                "• Device permissions: INTERNET only\n" +
                "• Network: Bedrock endpoints only\n" +
                "• Contacts/SMS/location/camera/mic: blocked by manifest\n" +
                "• External writes/tools: not enabled\n" +
                "• API key: encrypted with Android Keystore\n" +
                "• Audit log: enabled\n\n" +
                "Kill switch: " + (isKillSwitchOn() ? "ON — AI/network blocked" : "OFF — restricted Bedrock access allowed"),
                13, Color.DKGRAY, false);
        box.addView(summary);

        new AlertDialog.Builder(this)
                .setTitle("Sandbox Controls")
                .setView(box)
                .setNeutralButton("Audit Log", (d, w) -> showAuditLog())
                .setNegativeButton("Close", null)
                .setPositiveButton(isKillSwitchOn() ? "Enable AI" : "KILL SWITCH", (d, w) -> {
                    boolean next = !isKillSwitchOn();
                    prefs.putString(PREF_SANDBOX_KILL, next ? "1" : "0");
                    audit(next ? "KILL_SWITCH_ON" : "KILL_SWITCH_OFF", "user_action");
                    refreshSandboxUi();
                    Toast.makeText(this, next ? "AI/network actions blocked" : "Restricted Bedrock access enabled", Toast.LENGTH_LONG).show();
                }).show();
    }

    private void showAuditLog() {
        String log = prefs.getString(PREF_AUDIT, "No audit events yet.");
        TextView view = label(log, 12, Color.DKGRAY, false);
        view.setTextIsSelectable(true);
        view.setPadding(dp(14), dp(10), dp(14), dp(10));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);
        new AlertDialog.Builder(this)
                .setTitle("Sandbox Audit Log")
                .setView(scroll)
                .setNegativeButton("Close", null)
                .setPositiveButton("Clear", (d, w) -> {
                    prefs.putString(PREF_AUDIT, "");
                    audit("AUDIT_CLEARED", "user_action");
                }).show();
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(8));

        TextView info = label("Sandbox SAFE is enforced. Only your Bedrock API key is required; model and region are discovered automatically.", 13, Color.DKGRAY, false);
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
                    audit("KEY_SAVED", "encrypted_keystore");
                    updateConnectionUi("DISCOVERING");
                    autoConnect(true);
                }).show();
    }

    private boolean isKillSwitchOn() {
        return "1".equals(prefs.getString(PREF_SANDBOX_KILL, "0"));
    }

    private void refreshSandboxUi() {
        if (sandboxChip != null) {
            sandboxChip.setText(isKillSwitchOn() ? "KILLED" : "SAFE");
            sandboxChip.setBackground(round(isKillSwitchOn() ? DANGER : SAFE, dp(13)));
        }
        updateConnectionUi(isKillSwitchOn() ? "BLOCKED" : initialStatus());
    }

    private void ensureSandboxAllowsNetwork() throws Exception {
        if (isKillSwitchOn()) {
            audit("BLOCKED", "kill_switch");
            throw new SecurityException("Sandbox kill switch is ON");
        }
    }

    private void validateEndpoint(String endpoint) throws Exception {
        URL url = new URL(endpoint);
        String host = url.getHost();
        boolean allowed = "https".equalsIgnoreCase(url.getProtocol())
                && host.startsWith("bedrock-mantle.")
                && host.endsWith(".api.aws");
        if (!allowed) {
            audit("BLOCKED_ENDPOINT", host);
            throw new SecurityException("Sandbox blocked non-Bedrock endpoint: " + host);
        }
    }

    private void audit(String action, String detail) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String clean = detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' ');
        String line = ts + " | " + action + " | " + clean + "\n";
        String old = prefs.getString(PREF_AUDIT, "");
        String combined = line + old;
        if (combined.length() > AUDIT_MAX_CHARS) combined = combined.substring(0, AUDIT_MAX_CHARS);
        prefs.putString(PREF_AUDIT, combined);
    }

    private String initialStatus() {
        if (isKillSwitchOn()) return "BLOCKED";
        if (prefs.getSecret("api_key").isEmpty()) return "KEY NEEDED";
        return prefs.getString("model", "").isEmpty() ? "UNVERIFIED" : "VERIFIED";
    }

    private String shortConnectionLabel() {
        String model = prefs.getString("model", "");
        String region = prefs.getString("region", "");
        if (isKillSwitchOn()) return "Sandbox · network blocked";
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
        if (isKillSwitchOn()) {
            audit("AUTO_CONNECT_BLOCKED", "kill_switch");
            refreshSandboxUi();
            return;
        }
        final String apiKey = prefs.getSecret("api_key");
        if (apiKey.isEmpty()) {
            showSettings();
            return;
        }
        modelOperationRunning = true;
        updateConnectionUi("DISCOVERING");
        audit("AUTO_CONNECT_START", forceRediscover ? "rediscover" : "cached_allowed");

        new Thread(() -> {
            try {
                JSONArray candidates = loadOrDiscover(apiKey, forceRediscover);
                final ModelChoice choice = findWorkingModel(apiKey, candidates, 0, candidates.length());
                saveChoice(choice, candidates);
                audit("MODEL_VERIFIED", choice.model + " @ " + choice.region);
                runOnUiThread(() -> {
                    updateConnectionUi("VERIFIED");
                    addBubble("assistant", "Connected and verified.\n" + choice.model + "\nRegion: " + choice.region);
                });
            } catch (Exception e) {
                audit("AUTO_CONNECT_FAILED", safeMessage(e));
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
        if (isKillSwitchOn()) {
            Toast.makeText(this, "Sandbox kill switch is ON", Toast.LENGTH_SHORT).show();
            audit("MODEL_SWITCH_BLOCKED", "kill_switch");
            return;
        }
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
        audit("MODEL_SWITCH_START", prefs.getString("model", "none"));
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
                audit("MODEL_SWITCHED", choice.model + " @ " + choice.region);
                runOnUiThread(() -> {
                    updateConnectionUi("VERIFIED");
                    Toast.makeText(this, "Switched to " + choice.model, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                audit("MODEL_SWITCH_FAILED", safeMessage(e));
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
        audit("MODEL_DISCOVERY", "candidates=" + all.length());
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
        if (isKillSwitchOn()) {
            audit("CHAT_BLOCKED", "kill_switch");
            Toast.makeText(this, "Sandbox kill switch is ON", Toast.LENGTH_SHORT).show();
            return;
        }
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
        audit("CHAT_REQUEST", "model=" + prefs.getString("model", ""));
        new Thread(() -> {
            try {
                final String answer = callBedrock(text);
                audit("CHAT_SUCCESS", "chars=" + answer.length());
                runOnUiThread(() -> {
                    addBubble("assistant", answer);
                    updateConnectionUi("VERIFIED");
                });
            } catch (Exception e) {
                audit("CHAT_FAILED", safeMessage(e));
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
        ensureSandboxAllowsNetwork();
        validateEndpoint(endpoint);
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
