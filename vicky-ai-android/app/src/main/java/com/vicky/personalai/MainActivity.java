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
import android.widget.Button;
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

public class MainActivity extends Activity {
    private SecurePrefs prefs;
    private LinearLayout chatContainer;
    private EditText promptInput;
    private TextView statusView;
    private TextView modelChip;
    private ScrollView scrollView;
    private LinearLayout chatPanel;
    private LinearLayout workPanel;

    private final int BG = Color.rgb(7, 9, 13);
    private final int PANEL = Color.rgb(18, 21, 28);
    private final int PANEL_2 = Color.rgb(26, 30, 39);
    private final int TEXT = Color.rgb(242, 245, 249);
    private final int MUTED = Color.rgb(145, 154, 168);
    private final int ACCENT = Color.rgb(110, 92, 255);
    private final int ACCENT_2 = Color.rgb(0, 197, 255);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new SecurePrefs(this);
        buildUi();
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
        addBubble("assistant", "Hi. I’m ready when you are.\n\nAsk anything, or open Settings to choose your Bedrock region and model.");
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
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(round(Color.rgb(13, 16, 22), dp(16)));

        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextColor(Color.rgb(70, 214, 139));
        dot.setTextSize(11);
        row.addView(dot);

        modelChip = new TextView(this);
        modelChip.setText(shortConnectionLabel());
        modelChip.setTextColor(TEXT);
        modelChip.setTextSize(12);
        modelChip.setSingleLine(true);
        modelChip.setPadding(dp(8), 0, 0, 0);
        row.addView(modelChip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        statusView = new TextView(this);
        statusView.setText("READY");
        statusView.setTextColor(MUTED);
        statusView.setTextSize(10);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
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
                .setTitle("Vicky AI Settings")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.putString("region", region.getText().toString().trim());
                    prefs.putString("model", model.getText().toString().trim());
                    prefs.putSecret("api_key", key.getText().toString().trim());
                    prefs.putString("system", system.getText().toString().trim());
                    modelChip.setText(shortConnectionLabel());
                    statusView.setText("READY");
                    Toast.makeText(this, "Settings saved securely on this device", Toast.LENGTH_SHORT).show();
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

    private String shortConnectionLabel() {
        String region = prefs.getString("region", "ap-south-1");
        String model = prefs.getString("model", "");
        return model.isEmpty() ? "Bedrock  ·  " + region + "  ·  model not set" : model + "  ·  " + region;
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
        statusView.setText("THINKING");

        new Thread(() -> {
            try {
                String answer = callBedrock(text);
                runOnUiThread(() -> {
                    addBubble("assistant", answer);
                    statusView.setText("READY");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    addBubble("error", "Request failed\n" + e.getMessage());
                    statusView.setText("FAILED");
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
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + raw);

        JSONObject json = new JSONObject(raw.toString());
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "No text returned.");
    }

    private void addBubble(String role, String text) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(role.equals("user") ? Gravity.END : Gravity.START);

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(15.5f);
        bubble.setLineSpacing(0, 1.12f);
        bubble.setTextColor(role.equals("error") ? Color.rgb(255, 175, 175) : TEXT);
        int bg = role.equals("user") ? Color.rgb(74, 65, 170) : role.equals("error") ? Color.rgb(62, 28, 34) : PANEL;
        bubble.setBackground(round(bg, dp(18)));
        bubble.setPadding(dp(15), dp(12), dp(15), dp(12));

        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(
                role.equals("user") ? dp(300) : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bubble.setLayoutParams(bubbleLp);
        line.addView(bubble);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        chatContainer.addView(line, lp);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private TextView iconButton(String symbol) {
        TextView v = new TextView(this);
        v.setText(symbol);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(20);
        v.setTextColor(TEXT);
        v.setBackground(round(PANEL, dp(22)));
        return v;
    }

    private TextView tabButton(String label, boolean active) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(15);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(active ? TEXT : MUTED);
        v.setBackground(active ? round(PANEL_2, dp(20)) : null);
        return v;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable roundGradient(int radius, int start, int end) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        g.setCornerRadius(radius);
        return g;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
