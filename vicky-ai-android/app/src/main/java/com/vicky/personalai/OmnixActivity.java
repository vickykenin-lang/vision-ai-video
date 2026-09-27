package com.vicky.personalai;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.WeakHashMap;

/**
 * OMNIX presentation shell over the stable MainActivity runtime.
 * Keeps the existing Bedrock/team/sandbox logic intact while adding:
 * - OMNIX branding
 * - selectable/copyable result text
 * - lightweight Markdown rendering
 * - Sandbox WebView zoom controls + pinch zoom
 */
public final class OmnixActivity extends MainActivity {
    private final WeakHashMap<TextView, Boolean> selectionPrepared = new WeakHashMap<>();
    private final WeakHashMap<TextView, String> renderedPlain = new WeakHashMap<>();
    private final WeakHashMap<TextView, TextView> copyActions = new WeakHashMap<>();
    private final WeakHashMap<WebView, View> zoomBars = new WeakHashMap<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        View root = getWindow().getDecorView();
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> enhanceTree(root));
        root.post(() -> enhanceTree(root));
    }

    private void enhanceTree(View view) {
        if (view instanceof EditText) {
            EditText e = (EditText) view;
            CharSequence hint = e.getHint();
            if (hint != null && hint.toString().contains("Vicky AI")) {
                e.setHint(hint.toString().replace("Vicky AI", "OMNIX"));
            }
        } else if (view instanceof TextView) {
            enhanceText((TextView) view);
        }

        if (view instanceof WebView) enhanceWebView((WebView) view);

        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) enhanceTree(g.getChildAt(i));
        }
    }

    private void enhanceText(TextView tv) {
        String current = tv.getText() == null ? "" : tv.getText().toString();

        if ("Vicky AI".equals(current)) {
            tv.setText("OMNIX");
            current = "OMNIX";
        } else if ("V".equals(current) && tv.getWidth() <= dpLocal(60)) {
            tv.setText("");
            tv.setBackgroundResource(R.drawable.omnix_logo);
            current = "";
        }

        String lastRendered = renderedPlain.get(tv);
        if ((lastRendered == null || !current.equals(lastRendered)) && looksLikeMarkdown(current)) {
            CharSequence styled = renderMarkdown(current);
            tv.setText(styled);
            current = styled.toString();
            renderedPlain.put(tv, current);
        }

        boolean copyEligible = !current.isEmpty()
                && tv.getBackground() != null
                && !tv.hasOnClickListeners()
                && !(tv instanceof EditText);

        if (copyEligible) {
            if (!Boolean.TRUE.equals(selectionPrepared.get(tv))) {
                tv.setTextIsSelectable(true);
                selectionPrepared.put(tv, true);
            }
            ensureCopyAction(tv);
        }
    }

    private void ensureCopyAction(TextView source) {
        if (copyActions.containsKey(source)) return;
        if (!(source.getParent() instanceof LinearLayout)) return;

        LinearLayout parent = (LinearLayout) source.getParent();
        TextView copy = new TextView(this);
        copy.setText("Copy");
        copy.setTextSize(10f);
        copy.setTextColor(Color.rgb(145,154,168));
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(dpLocal(10), dpLocal(3), dpLocal(10), dpLocal(5));
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            CharSequence value = source.getText();
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("OMNIX result", value == null ? "" : value));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        });

        copyActions.put(source, copy);
        int index = parent.indexOfChild(source);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END;
        lp.setMargins(0, 0, dpLocal(4), dpLocal(2));
        parent.addView(copy, Math.min(index + 1, parent.getChildCount()), lp);
    }

    private boolean looksLikeMarkdown(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.contains("**") || s.contains("__") || s.contains("```") || s.contains("`")
                || s.startsWith("# ") || s.startsWith("## ") || s.startsWith("### ")
                || s.contains("\n# ") || s.contains("\n## ") || s.contains("\n### ")
                || s.startsWith("- ") || s.contains("\n- ") || s.startsWith("* ") || s.contains("\n* ");
    }

    private CharSequence renderMarkdown(String src) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = src.replace("\r\n", "\n").split("\n", -1);
        boolean codeBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                codeBlock = !codeBlock;
                continue;
            }

            int lineStart = out.length();
            int heading = 0;

            if (codeBlock) {
                out.append(line);
                int end = out.length();
                if (end > lineStart) {
                    out.setSpan(new TypefaceSpan("monospace"), lineStart, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(Color.rgb(24,28,36)), lineStart, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                while (heading < 3 && heading < line.length() && line.charAt(heading) == '#') heading++;
                if (heading > 0 && line.length() > heading && line.charAt(heading) == ' ') {
                    line = line.substring(heading + 1);
                } else {
                    heading = 0;
                }

                if (line.startsWith("- ") || line.startsWith("* ")) line = "• " + line.substring(2);
                appendInline(out, line);

                int end = out.length();
                if (heading > 0 && end > lineStart) {
                    out.setSpan(new StyleSpan(Typeface.BOLD), lineStart, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    float scale = heading == 1 ? 1.28f : heading == 2 ? 1.18f : 1.10f;
                    out.setSpan(new RelativeSizeSpan(scale), lineStart, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            if (i < lines.length - 1) out.append('\n');
        }
        return out;
    }

    private void appendInline(SpannableStringBuilder out, String line) {
        int i = 0;
        while (i < line.length()) {
            if (line.startsWith("**", i) || line.startsWith("__", i)) {
                String marker = line.substring(i, i + 2);
                int close = line.indexOf(marker, i + 2);
                if (close > i + 2) {
                    int start = out.length();
                    out.append(line, i + 2, close);
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 2;
                    continue;
                }
            }

            if (line.charAt(i) == '`') {
                int close = line.indexOf('`', i + 1);
                if (close > i + 1) {
                    int start = out.length();
                    out.append(line, i + 1, close);
                    out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(Color.rgb(24,28,36)), start, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 1;
                    continue;
                }
            }

            out.append(line.charAt(i));
            i++;
        }
    }

    private void enhanceWebView(WebView web) {
        WebSettings s = web.getSettings();
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);

        if (zoomBars.containsKey(web)) return;
        if (!(web.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) web.getParent();

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        bar.setPadding(dpLocal(4), dpLocal(2), dpLocal(4), dpLocal(4));

        TextView minus = zoomButton("−");
        TextView reset = zoomButton("100%");
        TextView plus = zoomButton("+");
        TextView fit = zoomButton("Fit");

        minus.setOnClickListener(v -> web.zoomOut());
        plus.setOnClickListener(v -> web.zoomIn());
        reset.setOnClickListener(v -> {
            web.setInitialScale(100);
            web.getSettings().setLoadWithOverviewMode(false);
            web.reload();
        });
        fit.setOnClickListener(v -> {
            web.setInitialScale(0);
            web.getSettings().setLoadWithOverviewMode(true);
            web.reload();
        });

        bar.addView(minus);
        bar.addView(reset);
        bar.addView(plus);
        bar.addView(fit);

        zoomBars.put(web, bar);
        int index = parent.indexOfChild(web);
        parent.addView(bar, Math.max(0, index));
    }

    private TextView zoomButton(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(11f);
        b.setTextColor(Color.rgb(242,245,249));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dpLocal(10), dpLocal(5), dpLocal(10), dpLocal(5));
        return b;
    }

    private int dpLocal(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
