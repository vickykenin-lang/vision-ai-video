package com.vicky.personalai;

import android.app.AlertDialog;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

public class MainActivity extends Activity {
    private SecurePrefs prefs;
    private LinearLayout chatContainer;
    private EditText promptInput;
    private TextView statusView;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new SecurePrefs(this);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(Color.rgb(246, 247, 249));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Vicky AI");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button settings = new Button(this);
        settings.setText("Settings");
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings);
        root.addView(top);

        statusView = new TextView(this);
        statusView.setText(getConnectionLabel());
        statusView.setPadding(0, dp(6), 0, dp(12));
        root.addView(statusView);

        scrollView = new ScrollView(this);
        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(chatContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM);

        promptInput = new EditText(this);
        promptInput.setHint("Message Vicky AI…");
        promptInput.setMinLines(1);
        promptInput.setMaxLines(5);
        promptInput.setBackgroundColor(Color.WHITE);
        composer.addView(promptInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button send = new Button(this);
        send.setText("Send");
        send.setOnClickListener(v -> sendMessage());
        composer.addView(send);
        root.addView(composer);

        setContentView(root);
        addBubble("assistant", "Ready. Open Settings once, add your Bedrock API key, region and model ID, then start chatting.");
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        box.setPadding(p, p, p, p);

        EditText region = field("AWS Region", prefs.getString("region", "ap-south-1"));
        EditText model = field("Bedrock model ID / inference profile", prefs.getString("model", ""));
        EditText key = field("Bedrock API key", prefs.getSecret("api_key"));
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText system = field("Personal instruction", prefs.getString("system", "You are Vicky's private AI assistant. Be concise, practical and comfortable in Hinglish or English. Never claim a system or integration is verified unless there is current evidence."));
        system.setMinLines(4);

        box.addView(region);
        box.addView(model);
        box.addView(key);
        box.addView(system);

        new AlertDialog.Builder(this)
                .setTitle("Bedrock Settings")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.putString("region", region.getText().toString().trim());
                    prefs.putString("model", model.getText().toString().trim());
                    prefs.putSecret("api_key", key.getText().toString().trim());
                    prefs.putString("system", system.getText().toString().trim());
                    statusView.setText(getConnectionLabel());
                    Toast.makeText(this, "Settings saved securely on this device", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(false);
        e.setPadding(0, dp(6), 0, dp(6));
        return e;
    }

    private String getConnectionLabel() {
        String region = prefs.getString("region", "ap-south-1");
        String model = prefs.getString("model", "");
        boolean hasKey = !prefs.getSecret("api_key").isEmpty();
        return "Bedrock • " + region + " • " + (model.isEmpty() ? "model not set" : model) + " • " + (hasKey ? "key saved" : "key missing");
    }

    private void sendMessage() {
        String text = promptInput.getText().toString().trim();
        if (text.isEmpty()) return;
        String apiKey = prefs.getSecret("api_key");
        String model = prefs.getString("model", "").trim();
        if (apiKey.isEmpty() || model.isEmpty()) {
            Toast.makeText(this, "Open Settings and add API key + model ID first", Toast.LENGTH_LONG).show();
            return;
        }
        promptInput.setText("");
        addBubble("user", text);
        statusView.setText("Thinking…");

        new Thread(() -> {
            try {
                String answer = callBedrock(text);
                runOnUiThread(() -> {
                    addBubble("assistant", answer);
                    statusView.setText(getConnectionLabel());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    addBubble("assistant", "Request failed: " + e.getMessage());
                    statusView.setText("Bedrock request failed");
                });
            }
        }).start();
    }

    private String callBedrock(String userText) throws Exception {
        String region = prefs.getString("region", "ap-south-1").trim();
        String model = prefs.getString("model", "").trim();
        String apiKey = prefs.getSecret("api_key");
        String system = prefs.getString("system", "");
        String endpoint = "https://bedrock-runtime." + region + ".amazonaws.com/openai/v1/chat/completions";

        JSONArray messages = new JSONArray();
        if (!system.isEmpty()) messages.put(new JSONObject().put("role", "system").put("content", system));
        messages.put(new JSONObject().put("role", "user").put("content", userText));

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.4);

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder raw = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) raw.append(line);
        }
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + ": " + raw);
        }

        JSONObject json = new JSONObject(raw.toString());
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "No text returned.");
    }

    private void addBubble(String role, String text) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(16);
        bubble.setTextColor(role.equals("user") ? Color.WHITE : Color.rgb(25, 25, 25));
        bubble.setBackgroundColor(role.equals("user") ? Color.rgb(35, 82, 160) : Color.WHITE);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                role.equals("user") ? dp(300) : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        lp.gravity = role.equals("user") ? Gravity.END : Gravity.START;
        chatContainer.addView(bubble, lp);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
