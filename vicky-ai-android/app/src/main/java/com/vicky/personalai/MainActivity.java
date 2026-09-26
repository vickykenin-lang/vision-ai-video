package com.vicky.personalai;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private SecurePrefs prefs;
    private LinearLayout root, chatContainer, bodyHost;
    private EditText promptInput;
    private TextView modelChip, statusView, sandboxChip, teamStatus;
    private TextView chatTab, teamTab, sandboxTab;
    private TextView browserTab, networkTab, auditTab, networkView, auditView, browserUrl;
    private ScrollView scrollView;
    private WebView webView;
    private View modelBar, teamPanel, composer;
    private final TextView[] teamRows = new TextView[4];

    private volatile boolean modelOperationRunning = false;
    private volatile boolean teamOperationRunning = false;
    private boolean teamMode = false;
    private boolean teamPanelExpanded = false;
    private String mainMode = "chat";
    private String sandboxMode = "browser";

    private final int BG=Color.rgb(7,9,13), PANEL=Color.rgb(18,21,28), PANEL2=Color.rgb(27,31,40);
    private final int TEXT=Color.rgb(242,245,249), MUTED=Color.rgb(145,154,168), ACCENT=Color.rgb(110,92,255), ACCENT2=Color.rgb(0,197,255);
    private final int SAFE=Color.rgb(57,194,123), DANGER=Color.rgb(235,84,84), AMBER=Color.rgb(234,179,74);

    private static final String SYSTEM =
            "You are one member of Vicky AI. Be concise, practical and comfortable in Hinglish or English. " +
            "Never claim live/current information unless supplied by runtime context or a connected live source. " +
            "When working in Team mode, collaborate through the shared team state and do not behave as a permanently assigned specialist.";

    private static final String PREF_KILL="sandbox_kill", PREF_AUDIT="sandbox_audit",
            PREF_NETWORK="sandbox_network", PREF_LAST_URL="sandbox_last_url",
            PREF_TEAM="council_models", PREF_HISTORY="chat_history_v2", PREF_MEMORY="local_memory_v1";
    private static final int HISTORY_MAX=60, AUDIT_MAX=22000, NETWORK_MAX=22000;
    private static final String[] AUTO_REGIONS={
            "ap-south-1","ap-south-2","ap-southeast-1","ap-southeast-2",
            "us-east-1","us-west-2","eu-west-1","eu-west-2","eu-central-1"
    };
    private static final Pattern GITHUB_REPO = Pattern.compile(
            "https?://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)", Pattern.CASE_INSENSITIVE);

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        prefs=new SecurePrefs(this);
        if(prefs.getString(PREF_KILL,"").isEmpty()) prefs.putString(PREF_KILL,"0");
        teamPanelExpanded=false;
        audit("APP_START","v1.1 sandbox_browser=enabled github_reader=read_only team=self_organizing");
        buildUi();
        loadHistoryIntoUi();
        if(historyLength()==0) addBubble("assistant",
                "Ready. Chat, self-organizing Team, and visual Sandbox are available. Public GitHub repos can be studied read-only.", false);
        String key=prefs.getSecret("api_key");
        if(!isKilled()&&!key.isEmpty()&&prefs.getString("model","").isEmpty()) autoConnect(false);
    }

    @Override protected void onDestroy(){
        if(webView!=null){
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    private void buildUi(){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(3),dp(10),dp(8));
        root.setBackgroundColor(BG);

        root.addView(buildHeader());
        root.addView(buildMainTabs());

        modelBar=buildModelBar();
        teamPanel=buildTeamPanel();
        root.addView(modelBar);
        root.addView(teamPanel);

        bodyHost=new LinearLayout(this);
        bodyHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(bodyHost,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        composer=buildComposer();
        root.addView(composer);

        setContentView(root);
        showChatBody();
    }

    private View buildHeader(){
        LinearLayout r=new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0,0,0,dp(1));

        TextView mark=label("V",13,Color.WHITE,true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(roundGradient(dp(14),ACCENT,ACCENT2));
        r.addView(mark,new LinearLayout.LayoutParams(dp(30),dp(30)));

        LinearLayout t=new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(8),0,0,0);
        t.addView(label("Vicky AI",17.5f,TEXT,true));
        t.addView(label("Private multi-model workspace",8.5f,MUTED,false));
        r.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        sandboxChip=label(isKilled()?"KILLED":"SAFE",8.5f,Color.WHITE,true);
        sandboxChip.setGravity(Gravity.CENTER);
        sandboxChip.setPadding(dp(7),dp(4),dp(7),dp(4));
        sandboxChip.setBackground(round(isKilled()?DANGER:SAFE,dp(11)));
        sandboxChip.setOnClickListener(v->showSandboxControls());
        r.addView(sandboxChip);

        TextView settings=label("⚙",14.5f,TEXT,false);
        settings.setGravity(Gravity.CENTER);
        settings.setBackground(round(PANEL,dp(15)));
        settings.setOnClickListener(v->showSettings());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(30),dp(30));
        sp.setMargins(dp(6),0,0,0);
        r.addView(settings,sp);
        return r;
    }

    private View buildMainTabs(){
        LinearLayout tabs=new LinearLayout(this);
        tabs.setPadding(dp(2),dp(2),dp(2),dp(2));
        tabs.setBackground(round(PANEL,dp(15)));

        chatTab=label("Chat",11,TEXT,true);
        teamTab=label("Team",11,MUTED,true);
        sandboxTab=label("Sandbox",11,MUTED,true);
        chatTab.setGravity(Gravity.CENTER);
        teamTab.setGravity(Gravity.CENTER);
        sandboxTab.setGravity(Gravity.CENTER);
        chatTab.setBackground(round(PANEL2,dp(13)));

        tabs.addView(chatTab,new LinearLayout.LayoutParams(0,dp(28),1));
        tabs.addView(teamTab,new LinearLayout.LayoutParams(0,dp(28),1));
        tabs.addView(sandboxTab,new LinearLayout.LayoutParams(0,dp(28),1));

        chatTab.setOnClickListener(v->setMainMode("chat"));
        teamTab.setOnClickListener(v->setMainMode("team"));
        sandboxTab.setOnClickListener(v->setMainMode("sandbox"));

        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,dp(2),0,dp(4));
        tabs.setLayoutParams(lp);
        return tabs;
    }

    private View buildModelBar(){
        LinearLayout r=new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12),dp(7),dp(8),dp(7));
        r.setBackground(round(Color.rgb(13,16,22),dp(15)));

        r.addView(label("●",11,prefs.getString("model","").isEmpty()?MUTED:SAFE,false));
        modelChip=label(shortConnectionLabel(),10.2f,TEXT,false);
        modelChip.setSingleLine(true);
        modelChip.setPadding(dp(8),0,dp(8),0);
        r.addView(modelChip,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        TextView sw=label("Switch",10.5f,Color.WHITE,true);
        sw.setGravity(Gravity.CENTER);
        sw.setPadding(dp(10),dp(6),dp(10),dp(6));
        sw.setBackground(round(PANEL2,dp(12)));
        sw.setOnClickListener(v->switchSingleModel());
        r.addView(sw);

        statusView=label(initialStatus(),8.5f,MUTED,true);
        statusView.setPadding(dp(8),0,0,0);
        r.addView(statusView);
        return r;
    }

    private View buildTeamPanel(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),dp(4),dp(8),dp(4));
        box.setBackground(round(Color.rgb(13,16,22),dp(13)));

        teamStatus=label("TEAM · 0/4 VERIFIED · tap to expand ▼",9.3f,MUTED,true);
        teamStatus.setGravity(Gravity.CENTER_VERTICAL);
        teamStatus.setPadding(dp(3),dp(4),dp(3),dp(4));
        teamStatus.setOnClickListener(v->toggleTeamPanel());
        box.addView(teamStatus);

        for(int i=0;i<4;i++){
            final int slot=i;
            teamRows[i]=label("M"+(i+1)+" · AVAILABLE · NOT SET",10.3f,TEXT,false);
            teamRows[i].setPadding(dp(8),dp(5),dp(8),dp(5));
            teamRows[i].setBackground(round(PANEL2,dp(10)));
            teamRows[i].setOnClickListener(v->showSlotDialog(slot));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0,dp(2),0,dp(2));
            box.addView(teamRows[i],p);
        }

        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,dp(5),0,dp(2));
        box.setLayoutParams(lp);
        refreshTeamUi();
        return box;
    }

    private void toggleTeamPanel(){
        teamPanelExpanded=!teamPanelExpanded;
        refreshTeamUi();
    }

    private View buildComposer(){
        LinearLayout r=new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(10),dp(7),dp(8),dp(7));
        r.setBackground(round(PANEL,dp(24)));

        TextView plus=label("＋",24,MUTED,false);
        plus.setGravity(Gravity.CENTER);
        plus.setOnClickListener(v->showStorage());
        r.addView(plus,new LinearLayout.LayoutParams(dp(40),dp(44)));

        promptInput=new EditText(this);
        promptInput.setHint("Message Vicky AI…");
        promptInput.setHintTextColor(Color.rgb(100,110,126));
        promptInput.setTextColor(TEXT);
        promptInput.setTextSize(16);
        promptInput.setMinLines(1);
        promptInput.setMaxLines(5);
        promptInput.setBackgroundColor(Color.TRANSPARENT);
        r.addView(promptInput,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

        TextView send=label("↑",22,Color.WHITE,true);
        send.setGravity(Gravity.CENTER);
        send.setBackground(roundGradient(dp(22),ACCENT,ACCENT2));
        send.setOnClickListener(v->sendMessage());
        r.addView(send,new LinearLayout.LayoutParams(dp(46),dp(46)));
        return r;
    }

    private void setMainMode(String mode){
        mainMode=mode;
        teamMode="team".equals(mode);
        refreshMainTabs();

        if("sandbox".equals(mode)){
            modelBar.setVisibility(View.GONE);
            teamPanel.setVisibility(View.GONE);
            composer.setVisibility(View.GONE);
            showSandboxBody();
            audit("MODE","sandbox");
        }else{
            modelBar.setVisibility(View.VISIBLE);
            teamPanel.setVisibility(View.VISIBLE);
            composer.setVisibility(View.VISIBLE);
            promptInput.setHint(teamMode?"Give one task to the AI team…":"Message Vicky AI…");
            showChatBody();
            audit("MODE",teamMode?"team":"chat");
            if(teamMode) ensureTeam(false);
        }
    }

    private void refreshMainTabs(){
        TextView[] tabs={chatTab,teamTab,sandboxTab};
        String[] modes={"chat","team","sandbox"};
        for(int i=0;i<tabs.length;i++){
            boolean active=modes[i].equals(mainMode);
            tabs[i].setTextColor(active?TEXT:MUTED);
            tabs[i].setBackground(active?round(PANEL2,dp(13)):round(Color.TRANSPARENT,dp(13)));
        }
    }

    private void showChatBody(){
        bodyHost.removeAllViews();
        if(scrollView==null){
            scrollView=new ScrollView(this);
            scrollView.setFillViewport(true);
            chatContainer=new LinearLayout(this);
            chatContainer.setOrientation(LinearLayout.VERTICAL);
            chatContainer.setPadding(0,dp(8),0,dp(12));
            scrollView.addView(chatContainer);
        }else if(scrollView.getParent()!=null){
            ((ViewGroup)scrollView.getParent()).removeView(scrollView);
        }
        bodyHost.addView(scrollView,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1));
    }

    private void showSandboxBody(){
        bodyHost.removeAllViews();
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout subTabs=new LinearLayout(this);
        subTabs.setPadding(dp(2),dp(2),dp(2),dp(2));
        subTabs.setBackground(round(PANEL,dp(14)));
        browserTab=label("Browser",10.5f,TEXT,true);
        networkTab=label("Network",10.5f,MUTED,true);
        auditTab=label("Audit",10.5f,MUTED,true);
        for(TextView v:new TextView[]{browserTab,networkTab,auditTab}) v.setGravity(Gravity.CENTER);
        browserTab.setBackground(round(PANEL2,dp(12)));
        subTabs.addView(browserTab,new LinearLayout.LayoutParams(0,dp(28),1));
        subTabs.addView(networkTab,new LinearLayout.LayoutParams(0,dp(28),1));
        subTabs.addView(auditTab,new LinearLayout.LayoutParams(0,dp(28),1));
        browserTab.setOnClickListener(v->setSandboxMode("browser",box));
        networkTab.setOnClickListener(v->setSandboxMode("network",box));
        auditTab.setOnClickListener(v->setSandboxMode("audit",box));
        box.addView(subTabs);

        LinearLayout content=new LinearLayout(this);
        content.setId(10001);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1);
        cp.setMargins(0,dp(5),0,0);
        box.addView(content,cp);
        bodyHost.addView(box,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        setSandboxMode(sandboxMode,box);
    }

    private void setSandboxMode(String mode, LinearLayout box){
        sandboxMode=mode;
        browserTab.setTextColor("browser".equals(mode)?TEXT:MUTED);
        networkTab.setTextColor("network".equals(mode)?TEXT:MUTED);
        auditTab.setTextColor("audit".equals(mode)?TEXT:MUTED);
        browserTab.setBackground("browser".equals(mode)?round(PANEL2,dp(12)):round(Color.TRANSPARENT,dp(12)));
        networkTab.setBackground("network".equals(mode)?round(PANEL2,dp(12)):round(Color.TRANSPARENT,dp(12)));
        auditTab.setBackground("audit".equals(mode)?round(PANEL2,dp(12)):round(Color.TRANSPARENT,dp(12)));

        LinearLayout content=box.findViewById(10001);
        content.removeAllViews();
        if("browser".equals(mode)) buildBrowserPane(content);
        else if("network".equals(mode)) buildNetworkPane(content);
        else buildAuditPane(content);
    }

    private void buildBrowserPane(LinearLayout content){
        LinearLayout urlRow=new LinearLayout(this);
        urlRow.setGravity(Gravity.CENTER_VERTICAL);
        browserUrl=label(prefs.getString(PREF_LAST_URL,"No page opened yet"),9.5f,MUTED,false);
        browserUrl.setSingleLine(true);
        browserUrl.setPadding(dp(10),0,dp(8),0);
        urlRow.addView(browserUrl,new LinearLayout.LayoutParams(0,dp(32),1));

        TextView reload=label("↻",17,TEXT,true);
        reload.setGravity(Gravity.CENTER);
        reload.setBackground(round(PANEL2,dp(12)));
        reload.setOnClickListener(v->{if(webView!=null) webView.reload();});
        urlRow.addView(reload,new LinearLayout.LayoutParams(dp(36),dp(32)));
        content.addView(urlRow);

        if(webView==null){
            webView=new WebView(this);
            webView.setBackgroundColor(Color.rgb(250,250,250));
            webView.getSettings().setJavaScriptEnabled(false);
            webView.getSettings().setDomStorageEnabled(false);
            webView.getSettings().setAllowFileAccess(false);
            webView.getSettings().setAllowContentAccess(false);
            webView.setDownloadListener((url,userAgent,contentDisposition,mimetype,contentLength)->{
                logNetwork("BLOCKED DOWNLOAD "+safeUrl(url));
                Toast.makeText(this,"Downloads are blocked in Sandbox",Toast.LENGTH_SHORT).show();
            });
            webView.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request){
                    String url=request.getUrl().toString();
                    if(!"GET".equalsIgnoreCase(request.getMethod()) || !isAllowedBrowserUrl(url)){
                        logNetwork("BLOCKED NAV "+safeUrl(url));
                        return true;
                    }
                    recordBrowserUrl(url,"USER/NAV");
                    return false;
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request){
                    if(!"GET".equalsIgnoreCase(request.getMethod())){
                        logNetwork("BLOCKED NON-GET "+request.getMethod()+" "+safeUrl(request.getUrl().toString()));
                        return emptyWebResponse();
                    }
                    return super.shouldInterceptRequest(view,request);
                }
                @Override public void onPageFinished(WebView view,String url){
                    recordBrowserUrl(url,"PAGE_READY");
                }
            });
        }else if(webView.getParent()!=null){
            ((ViewGroup)webView.getParent()).removeView(webView);
        }
        content.addView(webView,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        String last=prefs.getString(PREF_LAST_URL,"");
        if(!last.isEmpty() && webView.getUrl()==null) webView.loadUrl(last);
    }

    private void buildNetworkPane(LinearLayout content){
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(label("Read-only network activity",11,TEXT,true),
                new LinearLayout.LayoutParams(0,dp(34),1));
        TextView refresh=label("Refresh",10,Color.WHITE,true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(round(PANEL2,dp(12)));
        refresh.setOnClickListener(v->refreshNetworkView());
        top.addView(refresh,new LinearLayout.LayoutParams(dp(70),dp(30)));
        content.addView(top);

        networkView=label("",10.5f,TEXT,false);
        networkView.setTextIsSelectable(true);
        networkView.setPadding(dp(8),dp(6),dp(8),dp(8));
        ScrollView s=new ScrollView(this);
        s.addView(networkView);
        content.addView(s,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        refreshNetworkView();
    }

    private void buildAuditPane(LinearLayout content){
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(label("Sandbox audit trail",11,TEXT,true),
                new LinearLayout.LayoutParams(0,dp(34),1));
        TextView refresh=label("Refresh",10,Color.WHITE,true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(round(PANEL2,dp(12)));
        refresh.setOnClickListener(v->refreshAuditView());
        top.addView(refresh,new LinearLayout.LayoutParams(dp(70),dp(30)));
        content.addView(top);

        auditView=label("",10.5f,TEXT,false);
        auditView.setTextIsSelectable(true);
        auditView.setPadding(dp(8),dp(6),dp(8),dp(8));
        ScrollView s=new ScrollView(this);
        s.addView(auditView);
        content.addView(s,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        refreshAuditView();
    }

    private void refreshNetworkView(){
        if(networkView!=null) networkView.setText(prefs.getString(PREF_NETWORK,"No network events yet."));
    }

    private void refreshAuditView(){
        if(auditView!=null) auditView.setText(prefs.getString(PREF_AUDIT,"No audit events yet."));
    }

    private void showStorage(){
        String memory=prefs.getString(PREF_MEMORY,"").trim();
        String msg="Persistent messages: "+historyLength()+"\nMemory: "+(memory.isEmpty()?"empty":"active")+
                "\n\nHistory reloads after restart. Long-term memory stays local.";
        new AlertDialog.Builder(this).setTitle("Chat History & Memory").setMessage(msg)
                .setNeutralButton("View Memory",(d,w)->showMemory())
                .setNegativeButton("Close",null)
                .setPositiveButton("New Chat",(d,w)->{
                    prefs.putString(PREF_HISTORY,"");
                    chatContainer.removeAllViews();
                    addBubble("assistant","New chat started. Long-term memory retained.",false);
                    audit("NEW_CHAT","history_cleared");
                }).show();
    }

    private void showMemory(){
        String m=prefs.getString(PREF_MEMORY,"").trim();
        if(m.isEmpty())m="No long-term memory yet.";
        TextView v=label(m,13,Color.DKGRAY,false);
        v.setTextIsSelectable(true);
        v.setPadding(dp(14),dp(10),dp(14),dp(10));
        ScrollView s=new ScrollView(this);
        s.addView(v);
        new AlertDialog.Builder(this).setTitle("Local Memory").setView(s)
                .setNegativeButton("Close",null)
                .setPositiveButton("Clear",(d,w)->prefs.putString(PREF_MEMORY,"")).show();
    }

    private void showSandboxControls(){
        String msg="MODE: SAFE\n\n"+
                "• Bedrock: allowed\n"+
                "• Public GitHub read-only: allowed\n"+
                "• github.com visual browser: allowed\n"+
                "• Downloads / POST / uploads: blocked\n"+
                "• Local history + memory\n"+
                "• General web/news/market search: NOT CONNECTED\n\n"+
                "Kill switch: "+(isKilled()?"ON":"OFF");
        new AlertDialog.Builder(this).setTitle("Sandbox Controls").setMessage(msg)
                .setNeutralButton("Open Sandbox",(d,w)->setMainMode("sandbox"))
                .setNegativeButton("Close",null)
                .setPositiveButton(isKilled()?"Enable AI":"KILL SWITCH",(d,w)->{
                    prefs.putString(PREF_KILL,isKilled()?"0":"1");
                    refreshSandboxChip();
                }).show();
    }

    private void showSettings(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(12),dp(18),dp(8));
        box.addView(label("One Bedrock API key is used for Chat and Team. Public GitHub study does not need a GitHub token.",13,Color.DKGRAY,false));
        EditText key=new EditText(this);
        key.setHint("Bedrock API key");
        key.setText(prefs.getSecret("api_key"));
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(key);

        new AlertDialog.Builder(this).setTitle("Bedrock Connection").setView(box)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Save & Auto Connect",(d,w)->{
                    String x=key.getText().toString().trim();
                    prefs.putSecret("api_key",x);
                    if(prefs.getSecret("api_key").isEmpty()){
                        Toast.makeText(this,"Invalid API key. Paste only the single-line Bedrock key.",Toast.LENGTH_LONG).show();
                        updateConnectionUi("KEY NEEDED");
                        return;
                    }
                    prefs.putString("model","");
                    prefs.putString("region","");
                    prefs.putString("candidates","");
                    prefs.putString("candidate_index","-1");
                    prefs.putString(PREF_TEAM,"");
                    refreshTeamUi();
                    autoConnect(true);
                }).show();
    }

    private void showSlotDialog(int slot){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(8),dp(18),dp(8));
        box.addView(label(currentSlotLabel(slot),12.5f,Color.DKGRAY,false));
        EditText search=new EditText(this);
        search.setHint("Search model name");
        search.setSingleLine(true);
        box.addView(search);

        new AlertDialog.Builder(this).setTitle("M"+(slot+1)+" · Search / Switch")
                .setView(box)
                .setNeutralButton("Auto Next",(d,w)->switchTeamSlot(slot,"",true))
                .setNegativeButton("Close",null)
                .setPositiveButton("Search & Switch",(d,w)->
                        switchTeamSlot(slot,search.getText().toString().trim(),false))
                .show();
    }

    private String currentSlotLabel(int slot){
        try{
            JSONArray a=getTeam();
            if(slot<a.length()){
                JSONObject x=a.getJSONObject(slot);
                return "Current: "+x.optString("model")+"\nRegion: "+x.optString("region")+
                        "\nNo permanent role. Responsibility is decided per task.";
            }
        }catch(Exception ignored){}
        return "No model locked yet.";
    }

    private boolean isKilled(){return "1".equals(prefs.getString(PREF_KILL,"0"));}

    private void refreshSandboxChip(){
        if(sandboxChip!=null){
            sandboxChip.setText(isKilled()?"KILLED":"SAFE");
            sandboxChip.setBackground(round(isKilled()?DANGER:SAFE,dp(13)));
        }
        updateConnectionUi(isKilled()?"BLOCKED":initialStatus());
    }

    private JSONArray getTeam(){
        try{
            String raw=prefs.getString(PREF_TEAM,"");
            return raw.isEmpty()?new JSONArray():new JSONArray(raw);
        }catch(Exception e){return new JSONArray();}
    }

    private void refreshTeamUi(){
        JSONArray a=getTeam();
        if(teamStatus!=null){
            String base="TEAM · "+a.length()+"/4 VERIFIED";
            teamStatus.setText(base+(teamPanelExpanded?" · tap to collapse ▲":" · tap to expand ▼"));
        }
        for(int i=0;i<4;i++){
            if(teamRows[i]==null)continue;
            if(i<a.length()){
                JSONObject x=a.optJSONObject(i);
                String m=x==null?"":x.optString("model");
                if(m.length()>31)m=m.substring(0,31)+"…";
                teamRows[i].setText("M"+(i+1)+" · AVAILABLE · "+m+" · VERIFIED");
                teamRows[i].setTextColor(TEXT);
            }else{
                teamRows[i].setText("M"+(i+1)+" · AVAILABLE · NOT SET");
                teamRows[i].setTextColor(MUTED);
            }
            teamRows[i].setVisibility(teamPanelExpanded?View.VISIBLE:View.GONE);
        }
    }

    private String runtimeContext(){
        Date now=new Date();
        SimpleDateFormat d=new SimpleDateFormat("EEEE, dd MMMM yyyy",Locale.US);
        SimpleDateFormat t=new SimpleDateFormat("HH:mm:ss",Locale.US);
        return "RUNTIME FACTS — AUTHORITATIVE\n"+
                "Current local date: "+d.format(now)+"\n"+
                "Current local time: "+t.format(now)+"\n"+
                "Timezone: "+TimeZone.getDefault().getID()+"\n"+
                "Public GitHub repository reader: CONNECTED, READ-ONLY\n"+
                "Visual GitHub sandbox browser: CONNECTED, READ-ONLY\n"+
                "General web/news/market search: NOT CONNECTED\n"+
                "Never fabricate current research, sources, prices or news.";
    }

    private String systemContext(){
        String m=prefs.getString(PREF_MEMORY,"").trim();
        return SYSTEM+"\n\n"+runtimeContext()+(m.isEmpty()?"":"\n\nLOCAL LONG-TERM MEMORY:\n"+m);
    }

    private void audit(String action,String detail){
        String ts=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());
        String clean=detail==null?"":detail.replace('\n',' ').replace('\r',' ');
        String all=ts+" | "+action+" | "+clean+"\n"+prefs.getString(PREF_AUDIT,"");
        if(all.length()>AUDIT_MAX)all=all.substring(0,AUDIT_MAX);
        prefs.putString(PREF_AUDIT,all);
        runOnUiThread(this::refreshAuditView);
    }

    private void logNetwork(String detail){
        String ts=new SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date());
        String clean=detail==null?"":detail.replace('\n',' ').replace('\r',' ');
        String all=ts+" | "+clean+"\n"+prefs.getString(PREF_NETWORK,"");
        if(all.length()>NETWORK_MAX)all=all.substring(0,NETWORK_MAX);
        prefs.putString(PREF_NETWORK,all);
        runOnUiThread(this::refreshNetworkView);
    }

    private String initialStatus(){
        if(isKilled())return "BLOCKED";
        if(prefs.getSecret("api_key").isEmpty())return "KEY NEEDED";
        return prefs.getString("model","").isEmpty()?"UNVERIFIED":"VERIFIED";
    }

    private String shortConnectionLabel(){
        String m=prefs.getString("model","");
        String r=prefs.getString("region","");
        if(isKilled())return "Sandbox · network blocked";
        if(prefs.getSecret("api_key").isEmpty())return "Bedrock · API key not set";
        return m.isEmpty()?"Bedrock · finding a working model…":m+" · "+r;
    }

    private void updateConnectionUi(String s){
        if(modelChip!=null)modelChip.setText(shortConnectionLabel());
        if(statusView!=null)statusView.setText(s);
    }

    private void autoConnect(boolean force){
        if(modelOperationRunning||isKilled())return;
        final String key=prefs.getSecret("api_key");
        if(key.isEmpty()){showSettings();return;}
        modelOperationRunning=true;
        updateConnectionUi("DISCOVERING");
        new Thread(()->{
            try{
                JSONArray c=loadOrDiscover(key,force);
                ModelChoice x=findWorkingModel(key,c,0,c.length());
                saveChoice(x,c);
                audit("MODEL_VERIFIED",x.model+" @ "+x.region);
                runOnUiThread(()->updateConnectionUi("VERIFIED"));
            }catch(Exception e){
                audit("AUTO_CONNECT_FAILED",safeMessage(e));
                runOnUiThread(()->updateConnectionUi("NO MODEL"));
            }finally{modelOperationRunning=false;}
        }).start();
    }

    private void switchSingleModel(){
        if(modelOperationRunning||isKilled())return;
        final String key=prefs.getSecret("api_key");
        if(key.isEmpty()){showSettings();return;}
        modelOperationRunning=true;
        updateConnectionUi("SWITCHING");
        new Thread(()->{
            try{
                JSONArray c=loadOrDiscover(key,false);
                int cur=parseInt(prefs.getString("candidate_index","-1"),-1);
                ModelChoice x;
                try{x=findWorkingModel(key,c,cur+1,c.length());}
                catch(Exception e){x=findWorkingModel(key,c,0,Math.max(0,cur));}
                saveChoice(x,c);
                runOnUiThread(()->updateConnectionUi("VERIFIED"));
            }catch(Exception e){
                audit("MODEL_SWITCH_FAILED",safeMessage(e));
                runOnUiThread(()->updateConnectionUi(initialStatus()));
            }finally{modelOperationRunning=false;}
        }).start();
    }

    private void ensureTeam(boolean force){
        if(teamOperationRunning||isKilled())return;
        final String key=prefs.getSecret("api_key");
        if(key.isEmpty()){showSettings();return;}
        if(!force&&getTeam().length()==4){refreshTeamUi();return;}
        teamOperationRunning=true;
        audit("TEAM_VERIFY_START",force?"force":"normal");
        new Thread(()->{
            try{
                JSONArray candidates=loadOrDiscover(key,force);
                JSONArray locked=findFourUniqueWorkingModels(key,candidates);
                prefs.putString(PREF_TEAM,locked.toString());
                JSONObject first=locked.getJSONObject(0);
                prefs.putString("model",first.optString("model"));
                prefs.putString("region",first.optString("region"));
                audit("TEAM_VERIFIED","models=4");
                runOnUiThread(()->{
                    refreshTeamUi();
                    updateConnectionUi("VERIFIED");
                    addTeamEvent("Team ready","Four unique models verified. Responsibilities will be negotiated per task.",SAFE);
                });
            }catch(Exception e){
                prefs.putString(PREF_TEAM,"");
                audit("TEAM_VERIFY_FAILED",safeMessage(e));
                runOnUiThread(()->{
                    refreshTeamUi();
                    addBubble("error","Team setup failed\n"+safeMessage(e),false);
                });
            }finally{teamOperationRunning=false;}
        }).start();
    }

    private JSONArray findFourUniqueWorkingModels(String key,JSONArray candidates)throws Exception{
        JSONArray locked=new JSONArray();
        Set<String> used=new HashSet<>();
        Exception last=null;
        for(int i=0;i<candidates.length()&&locked.length()<4;i++){
            JSONObject c=candidates.getJSONObject(i);
            String r=c.optString("region"),m=c.optString("model");
            if(m.isEmpty()||r.isEmpty()||used.contains(m))continue;
            int slot=locked.length();
            final int s=slot;
            runOnUiThread(()->{
                if(teamRows[s]!=null)teamRows[s].setText("M"+(s+1)+" · PINGING · "+m);
            });
            try{
                pingModel(key,r,m);
                used.add(m);
                locked.put(new JSONObject().put("region",r).put("model",m).put("candidate_index",i));
            }catch(Exception e){last=e;}
        }
        if(locked.length()<4)throw last==null
                ?new Exception("Only "+locked.length()+" unique working models found; 4 required")
                :new Exception("Only "+locked.length()+" unique models verified. "+safeMessage(last));
        return locked;
    }

    private void switchTeamSlot(int slot,String query,boolean autoNext){
        if(teamOperationRunning||isKilled())return;
        final String key=prefs.getSecret("api_key");
        if(key.isEmpty()){showSettings();return;}
        teamOperationRunning=true;
        if(teamRows[slot]!=null)teamRows[slot].setText("M"+(slot+1)+" · SEARCHING / PINGING…");
        new Thread(()->{
            try{
                JSONArray c=loadOrDiscover(key,false),team=getTeam();
                Set<String> used=new HashSet<>();
                for(int i=0;i<team.length();i++)if(i!=slot)used.add(team.getJSONObject(i).optString("model"));
                String q=query==null?"":query.toLowerCase(Locale.US);
                int start=0;
                if(autoNext&&slot<team.length())start=team.getJSONObject(slot).optInt("candidate_index",-1)+1;
                JSONObject selected=null;
                Exception last=null;
                for(int pass=0;pass<2&&selected==null;pass++){
                    int from=pass==0?Math.max(0,start):0;
                    int to=pass==0?c.length():Math.max(0,start);
                    for(int i=from;i<to;i++){
                        JSONObject x=c.getJSONObject(i);
                        String m=x.optString("model"),r=x.optString("region");
                        if(m.isEmpty()||r.isEmpty()||used.contains(m)||
                                (!q.isEmpty()&&!m.toLowerCase(Locale.US).contains(q)))continue;
                        try{
                            pingModel(key,r,m);
                            selected=new JSONObject().put("model",m).put("region",r).put("candidate_index",i);
                            break;
                        }catch(Exception e){last=e;}
                    }
                }
                if(selected==null)throw last==null?new Exception("No working replacement found"):last;
                while(team.length()<4)team.put(new JSONObject());
                team.put(slot,selected);
                prefs.putString(PREF_TEAM,team.toString());
                if(slot==0){
                    prefs.putString("model",selected.optString("model"));
                    prefs.putString("region",selected.optString("region"));
                }
                String name=selected.optString("model");
                audit("TEAM_SLOT_SWITCHED","M"+(slot+1)+" "+name);
                runOnUiThread(()->{
                    refreshTeamUi();
                    Toast.makeText(this,"M"+(slot+1)+" switched to "+name,Toast.LENGTH_LONG).show();
                });
            }catch(Exception e){
                audit("TEAM_SLOT_SWITCH_FAILED",safeMessage(e));
                runOnUiThread(()->{
                    refreshTeamUi();
                    Toast.makeText(this,safeMessage(e),Toast.LENGTH_LONG).show();
                });
            }finally{teamOperationRunning=false;}
        }).start();
    }

    private void sendMessage(){
        String text=promptInput.getText().toString().trim();
        if(text.isEmpty())return;
        if(isKilled()){
            Toast.makeText(this,"Sandbox kill switch is ON",Toast.LENGTH_SHORT).show();
            return;
        }
        if(prefs.getSecret("api_key").isEmpty()){
            showSettings();
            return;
        }
        promptInput.setText("");
        addBubble("user",text,true);
        if(teamMode) runTeamTask(text); else runSingleTask(text);
    }

    private void runSingleTask(String text){
        if(prefs.getString("model","").isEmpty()){
            autoConnect(false);
            addBubble("error","No verified chat model yet.",false);
            return;
        }
        statusView.setText("THINKING");
        new Thread(()->{
            try{
                String repoContext=maybeFetchGitHubContext(text);
                String answer=callBedrock(repoContext);
                appendHistory("assistant",answer);
                runOnUiThread(()->{
                    addBubble("assistant",answer,false);
                    updateConnectionUi("VERIFIED");
                });
                updateMemoryAsync(text,answer);
            }catch(Exception e){
                audit("CHAT_FAILED",safeMessage(e));
                runOnUiThread(()->{
                    addBubble("error","Request failed\n"+safeMessage(e),false);
                    statusView.setText("FAILED");
                });
            }
        }).start();
    }

    private void runTeamTask(String task){
        if(teamOperationRunning){addBubble("error","Team is already working.",false);return;}
        JSONArray team=getTeam();
        if(team.length()!=4){
            addBubble("assistant","Team needs four verified models first. Verification started; send the task again when all four are VERIFIED.",false);
            ensureTeam(false);
            return;
        }
        final JSONArray roster=team;
        teamOperationRunning=true;
        audit("TEAM_TASK_START","chars="+task.length());
        addTeamEvent("Team huddle","All four models are reading the same task and proposing responsibilities. No fixed roles.",ACCENT2);

        new Thread(()->{
            try{
                String repoContext=maybeFetchGitHubContext(task);
                if(needsGeneralLiveResearch(task) && repoContext.isEmpty()){
                    runOnUiThread(()->addTeamEvent("Research capability",
                            "General live web/news/market search is not connected. Public GitHub repo study is available read-only.",AMBER));
                }

                JSONArray proposals=new JSONArray();
                String rosterText=rosterText(roster);
                for(int i=0;i<4;i++){
                    JSONObject member=roster.getJSONObject(i);
                    updateTeamRow(i,"HUDDLE · proposing responsibility",member.optString("model"),AMBER);
                    String instruction=
                            "You are teammate M"+(i+1)+" in a self-organizing 4-model team. Read the task, roster, and any verified GitHub repository context. " +
                            "Propose the most useful responsibility YOU should take for THIS task, and nominate the teammate best suited to coordinate the final synthesis. " +
                            "Do not assume any permanent role. Return STRICT JSON only: " +
                            "{\"responsibility\":\"short task-specific responsibility\",\"coordinator_vote\":\"M1|M2|M3|M4\",\"reason\":\"very short reason\"}.";
                    String state=rosterText+(repoContext.isEmpty()?"":"\n\nVERIFIED GITHUB CONTEXT:\n"+trim(repoContext,24000));
                    String out=memberCall(member,task,state,instruction,300);
                    JSONObject p=parseJsonObject(out);
                    p.put("slot","M"+(i+1));
                    p.put("model",member.optString("model"));
                    proposals.put(p);
                }

                int coordinatorIndex=chooseCoordinator(proposals);
                JSONObject coordinator=roster.getJSONObject(coordinatorIndex);
                runOnUiThread(()->addTeamEvent("Huddle decision",
                        "Coordinator for this task: M"+(coordinatorIndex+1)+" · "+coordinator.optString("model"),SAFE));

                String planInstruction=
                        "You are the temporary coordinator chosen by the team for THIS task only. " +
                        "Using the four proposals, assign a distinct concise responsibility to each teammate. Avoid duplicate work. " +
                        "If verified GitHub context exists, make assignments use it. Return STRICT JSON only: " +
                        "{\"assignments\":[{\"slot\":\"M1\",\"task\":\"...\"},{\"slot\":\"M2\",\"task\":\"...\"},{\"slot\":\"M3\",\"task\":\"...\"},{\"slot\":\"M4\",\"task\":\"...\"}]}";
                String planRaw=memberCall(coordinator,task,proposals.toString(),planInstruction,460);
                JSONObject plan=parseJsonObject(planRaw);
                JSONArray assignments=plan.optJSONArray("assignments");
                if(assignments==null||assignments.length()<4)assignments=fallbackAssignments(proposals);

                for(int i=0;i<4;i++){
                    String assigned=findAssignment(assignments,i);
                    final int row=i;
                    runOnUiThread(()->teamRows[row].setText("M"+(row+1)+" · "+shortText(assigned,42)+" · ASSIGNED"));
                }
                JSONArray finalAssignments=assignments;
                runOnUiThread(()->addTeamEvent("Team plan",assignmentsSummary(finalAssignments),ACCENT2));

                String shared=runtimeContext()+"\n\nORIGINAL TASK:\n"+task+
                        "\n\nTEAM PLAN:\n"+assignments.toString()+
                        (repoContext.isEmpty()?"":"\n\nVERIFIED GITHUB REPOSITORY CONTEXT:\n"+trim(repoContext,28000))+
                        "\n\nSHARED FINDINGS:\n";

                for(int i=0;i<4;i++){
                    JSONObject member=roster.getJSONObject(i);
                    String assigned=findAssignment(assignments,i);
                    updateTeamRow(i,"WORKING · "+shortText(assigned,28),member.optString("model"),AMBER);
                    String workInstruction=
                            "You are teammate M"+(i+1)+". Your temporary responsibility for THIS task is: "+assigned+". " +
                            "Read the shared findings and verified GitHub context. Do your part, correct earlier findings if needed, and contribute only concise useful findings. " +
                            "Do not produce the final polished answer. Treat repository text marked VERIFIED GITHUB REPOSITORY CONTEXT as live read-only source material.";
                    String finding=memberCall(member,task,shared,workInstruction,620);
                    shared+="\nM"+(i+1)+" ("+assigned+"):\n"+finding+"\n";
                    updateTeamRow(i,"DONE · "+shortText(assigned,28),member.optString("model"),SAFE);
                }

                String finalInstruction=
                        "You are the temporary coordinator for this task. Read the full shared team findings and produce ONE concise unified answer. " +
                        "Remove duplicates. Mention unresolved material disagreement briefly. If verified GitHub context is present, base repo claims on it and do not tell the user to make the repo public. " +
                        "Do not claim general live web research because only public GitHub read-only retrieval is connected. Answer in the user's language/style.";
                String finalAnswer=memberCall(coordinator,task,shared,finalInstruction,1000);
                appendHistory("assistant",finalAnswer);
                audit("TEAM_TASK_SUCCESS","coordinator=M"+(coordinatorIndex+1)+" chars="+finalAnswer.length());
                runOnUiThread(()->{
                    refreshTeamUi();
                    addTeamEvent("Team complete","Self-organized work finished; one concise team answer produced.",SAFE);
                    addBubble("assistant",finalAnswer,false);
                });
                updateMemoryAsync(task,finalAnswer);
            }catch(Exception e){
                audit("TEAM_TASK_FAILED",safeMessage(e));
                runOnUiThread(()->{
                    refreshTeamUi();
                    addBubble("error","Team failed\n"+safeMessage(e),false);
                });
            }finally{teamOperationRunning=false;}
        }).start();
    }

    private String maybeFetchGitHubContext(String task)throws Exception{
        String url=extractGitHubRepoUrl(task);
        if(url.isEmpty())return "";
        runOnUiThread(()->addTeamEvent("GitHub Repo Study",
                "Opening public repository read-only. Browser/Network/Audit are visible in Sandbox.",ACCENT2));
        return fetchGitHubRepoContext(url);
    }

    private String extractGitHubRepoUrl(String text){
        if(text==null)return "";
        Matcher m=GITHUB_REPO.matcher(text);
        if(!m.find())return "";
        String repo=m.group(2);
        if(repo.endsWith(".git"))repo=repo.substring(0,repo.length()-4);
        return "https://github.com/"+m.group(1)+"/"+repo;
    }

    private RepoRef parseRepo(String repoUrl)throws Exception{
        Matcher m=GITHUB_REPO.matcher(repoUrl);
        if(!m.find())throw new Exception("Invalid GitHub repository URL");
        String repo=m.group(2);
        if(repo.endsWith(".git"))repo=repo.substring(0,repo.length()-4);
        return new RepoRef(m.group(1),repo);
    }

    private String fetchGitHubRepoContext(String repoUrl)throws Exception{
        if(isKilled())throw new SecurityException("Sandbox kill switch is ON");
        RepoRef rr=parseRepo(repoUrl);
        String repoPage="https://github.com/"+rr.owner+"/"+rr.repo;
        recordBrowserUrl(repoPage,"AI_OPEN_REPO");

        String apiBase="https://api.github.com/repos/"+enc(rr.owner)+"/"+enc(rr.repo);
        JSONObject meta=new JSONObject(httpGetReadOnly(apiBase,20000));
        if(meta.optBoolean("private",true))throw new Exception("Only public GitHub repositories are allowed");
        String branch=meta.optString("default_branch","main");
        StringBuilder out=new StringBuilder();
        out.append("SOURCE: PUBLIC GITHUB READ-ONLY\n");
        out.append("Repository: ").append(meta.optString("full_name",rr.owner+"/"+rr.repo)).append("\n");
        out.append("Default branch: ").append(branch).append("\n");
        out.append("Description: ").append(meta.optString("description","")).append("\n");
        out.append("Updated at: ").append(meta.optString("updated_at","")).append("\n\n");

        String treeUrl=apiBase+"/git/trees/"+enc(branch)+"?recursive=1";
        JSONObject treeJson=new JSONObject(httpGetReadOnly(treeUrl,25000));
        JSONArray tree=treeJson.optJSONArray("tree");
        if(tree==null)tree=new JSONArray();

        JSONArray selected=new JSONArray();
        addPreferred(tree,selected,"README.md");
        addPreferred(tree,selected,"README");
        addPreferred(tree,selected,"package.json");
        addPreferred(tree,selected,"pyproject.toml");
        addPreferred(tree,selected,"requirements.txt");
        addPreferred(tree,selected,"pom.xml");
        addPreferred(tree,selected,"build.gradle");
        addPreferred(tree,selected,"build.gradle.kts");

        for(int i=0;i<tree.length()&&selected.length()<14;i++){
            JSONObject x=tree.optJSONObject(i);
            if(x==null||!"blob".equals(x.optString("type")))continue;
            String p=x.optString("path","");
            if(isSafeStudyPath(p)&&isStudyTextFile(p)&&!containsPath(selected,p))selected.put(p);
        }

        out.append("Repository files sampled (read-only):\n");
        for(int i=0;i<selected.length();i++)out.append("- ").append(selected.optString(i)).append("\n");
        out.append("\n");

        int charBudget=42000;
        for(int i=0;i<selected.length()&&out.length()<charBudget;i++){
            String path=selected.optString(i);
            String raw="https://raw.githubusercontent.com/"+encPath(rr.owner)+"/"+encPath(rr.repo)+"/"+
                    encPath(branch)+"/"+encPath(path);
            try{
                String text=httpGetReadOnly(raw,20000);
                text=trim(text,Math.min(6500,charBudget-out.length()));
                out.append("\n===== FILE: ").append(path).append(" =====\n").append(text).append("\n");
            }catch(Exception e){
                out.append("\n===== FILE: ").append(path).append(" =====\n[Fetch failed: ")
                        .append(safeMessage(e)).append("]\n");
            }
        }
        audit("GITHUB_STUDY_OK",rr.owner+"/"+rr.repo+" branch="+branch+" sampled="+selected.length());
        return out.toString();
    }

    private void addPreferred(JSONArray tree,JSONArray selected,String name){
        for(int i=0;i<tree.length();i++){
            JSONObject x=tree.optJSONObject(i);
            if(x==null||!"blob".equals(x.optString("type")))continue;
            String p=x.optString("path","");
            if(p.equalsIgnoreCase(name)&&isSafeStudyPath(p)&&!containsPath(selected,p)){
                selected.put(p);return;
            }
        }
    }

    private boolean containsPath(JSONArray a,String p){
        for(int i=0;i<a.length();i++)if(p.equals(a.optString(i)))return true;
        return false;
    }

    private boolean isSafeStudyPath(String p){
        String x=p.toLowerCase(Locale.US);
        return !(x.contains(".env")||x.contains("secret")||x.contains("credential")||
                x.endsWith(".pem")||x.endsWith(".key")||x.endsWith(".p12")||
                x.contains("node_modules/")||x.contains("vendor/")||x.contains("dist/")||
                x.contains("build/")||x.contains(".git/"));
    }

    private boolean isStudyTextFile(String p){
        String x=p.toLowerCase(Locale.US);
        return x.endsWith(".md")||x.endsWith(".txt")||x.endsWith(".json")||
                x.endsWith(".yml")||x.endsWith(".yaml")||x.endsWith(".toml")||
                x.endsWith(".py")||x.endsWith(".js")||x.endsWith(".ts")||
                x.endsWith(".java")||x.endsWith(".kt")||x.endsWith(".go")||
                x.endsWith(".rs")||x.endsWith(".sh")||x.endsWith(".xml")||
                x.endsWith(".gradle")||x.endsWith(".kts");
    }

    private boolean needsGeneralLiveResearch(String s){
        String x=s.toLowerCase(Locale.US);
        boolean github=x.contains("github.com/");
        return !github&&(x.contains("research")||x.contains("latest")||x.contains("current")||
                x.contains("today")||x.contains("aaj")||x.contains("news")||
                x.contains("price")||x.contains("market")||x.contains("search web"));
    }

    private String rosterText(JSONArray team)throws Exception{
        StringBuilder b=new StringBuilder("TEAM ROSTER:\n");
        for(int i=0;i<team.length();i++){
            JSONObject x=team.getJSONObject(i);
            b.append("M").append(i+1).append(" = ").append(x.optString("model"))
                    .append(" @ ").append(x.optString("region")).append("\n");
        }
        return b.toString();
    }

    private JSONObject parseJsonObject(String raw)throws Exception{
        String s=raw.trim();
        int a=s.indexOf('{'),b=s.lastIndexOf('}');
        if(a<0||b<=a)throw new Exception("Team model returned invalid planning JSON");
        return new JSONObject(s.substring(a,b+1));
    }

    private int chooseCoordinator(JSONArray p){
        int[] votes={0,0,0,0};
        for(int i=0;i<p.length();i++){
            JSONObject po=p.optJSONObject(i);
            String v=po==null?"":po.optString("coordinator_vote","").toUpperCase(Locale.US);
            if(v.matches("M[1-4]"))votes[Integer.parseInt(v.substring(1))-1]++;
        }
        int best=0;
        for(int i=1;i<4;i++)if(votes[i]>votes[best])best=i;
        return best;
    }

    private JSONArray fallbackAssignments(JSONArray proposals)throws Exception{
        JSONArray a=new JSONArray();
        for(int i=0;i<4;i++){
            JSONObject p=proposals.getJSONObject(i);
            a.put(new JSONObject().put("slot","M"+(i+1))
                    .put("task",p.optString("responsibility","Contribute useful independent analysis")));
        }
        return a;
    }

    private String findAssignment(JSONArray a,int slot){
        String key="M"+(slot+1);
        for(int i=0;i<a.length();i++){
            JSONObject x=a.optJSONObject(i);
            if(x!=null&&key.equalsIgnoreCase(x.optString("slot")))
                return x.optString("task","Contribute useful analysis");
        }
        return "Contribute useful analysis";
    }

    private String assignmentsSummary(JSONArray a){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<4;i++)
            b.append("M").append(i+1).append(" → ").append(findAssignment(a,i)).append(i==3?"":"\n");
        return b.toString();
    }

    private String shortText(String s,int n){
        if(s==null)return "";
        return s.length()<=n?s:s.substring(0,n)+"…";
    }

    private void updateTeamRow(int slot,String state,String model,int color){
        runOnUiThread(()->{
            if(teamRows[slot]!=null){
                teamRows[slot].setText("M"+(slot+1)+" · "+state+" · "+shortText(model,27));
                teamRows[slot].setTextColor(color);
            }
        });
    }

    private String memberCall(JSONObject member,String task,String shared,String instruction,int maxTokens)throws Exception{
        String key=prefs.getSecret("api_key"),model=member.optString("model"),region=member.optString("region");
        validateRegionModel(region,model);
        JSONArray messages=new JSONArray();
        messages.put(new JSONObject().put("role","system")
                .put("content",SYSTEM+"\n\n"+runtimeContext()+"\n\n"+instruction));
        messages.put(new JSONObject().put("role","user")
                .put("content","ORIGINAL TASK:\n"+task+"\n\nTEAM STATE:\n"+trim(shared,36000)));
        JSONObject body=new JSONObject().put("model",model).put("messages",messages)
                .put("temperature",0.2).put("max_tokens",maxTokens);
        JSONObject json=postChat(key,region,body,120000);
        String out=json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content","").trim();
        if(out.isEmpty())throw new Exception("Empty response from "+model);
        return out;
    }

    private String callBedrock(String repoContext)throws Exception{
        String key=prefs.getSecret("api_key"),model=prefs.getString("model",""),region=prefs.getString("region","");
        validateRegionModel(region,model);
        JSONArray messages=new JSONArray();
        String sys=systemContext();
        if(repoContext!=null&&!repoContext.isEmpty())
            sys+="\n\nVERIFIED PUBLIC GITHUB REPOSITORY CONTEXT (read-only live fetch):\n"+trim(repoContext,32000);
        messages.put(new JSONObject().put("role","system").put("content",sys));
        JSONArray h=getHistory();
        int start=Math.max(0,h.length()-12);
        for(int i=start;i<h.length();i++){
            JSONObject x=h.optJSONObject(i);
            if(x==null)continue;
            String role=x.optString("role","");
            if(role.equals("user")||role.equals("assistant"))
                messages.put(new JSONObject().put("role",role).put("content",x.optString("text","")));
        }
        JSONObject body=new JSONObject().put("model",model).put("messages",messages)
                .put("temperature",0.35).put("max_tokens",1000);
        JSONObject json=postChat(key,region,body,120000);
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                .optString("content","No text returned.");
    }

    private void updateMemoryAsync(String user,String assistant){
        final String key=prefs.getSecret("api_key"),model=prefs.getString("model",""),region=prefs.getString("region","");
        if(key.isEmpty()||model.isEmpty()||region.isEmpty()||isKilled())return;
        new Thread(()->{
            try{
                String old=prefs.getString(PREF_MEMORY,"").trim();
                JSONArray m=new JSONArray();
                m.put(new JSONObject().put("role","system").put("content",
                        "Maintain compact long-term memory. Keep only durable user preferences, projects, decisions, recurring instructions and stable facts explicitly stated by the user. " +
                        "Ignore transient questions and model claims. Return concise bullets, max 300 words."));
                m.put(new JSONObject().put("role","user").put("content",
                        "EXISTING MEMORY:\n"+old+"\n\nLATEST EXCHANGE:\nUser: "+user+"\nAssistant: "+assistant));
                JSONObject body=new JSONObject().put("model",model).put("messages",m)
                        .put("temperature",0).put("max_tokens",400);
                JSONObject j=postChat(key,region,body,60000);
                String u=j.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                        .optString("content","").trim();
                if(!u.isEmpty())prefs.putString(PREF_MEMORY,u);
            }catch(Exception e){audit("MEMORY_UPDATE_FAILED",safeMessage(e));}
        }).start();
    }

    private JSONArray getHistory(){
        try{
            String raw=prefs.getString(PREF_HISTORY,"");
            return raw.isEmpty()?new JSONArray():new JSONArray(raw);
        }catch(Exception e){return new JSONArray();}
    }

    private int historyLength(){return getHistory().length();}

    private void appendHistory(String role,String text){
        try{
            JSONArray old=getHistory(),out=new JSONArray();
            int start=Math.max(0,old.length()-(HISTORY_MAX-1));
            for(int i=start;i<old.length();i++)out.put(old.getJSONObject(i));
            out.put(new JSONObject().put("role",role).put("text",text).put("ts",System.currentTimeMillis()));
            prefs.putString(PREF_HISTORY,out.toString());
        }catch(Exception e){audit("HISTORY_SAVE_FAILED",safeMessage(e));}
    }

    private void loadHistoryIntoUi(){
        if(chatContainer==null){
            scrollView=new ScrollView(this);
            scrollView.setFillViewport(true);
            chatContainer=new LinearLayout(this);
            chatContainer.setOrientation(LinearLayout.VERTICAL);
            chatContainer.setPadding(0,dp(8),0,dp(12));
            scrollView.addView(chatContainer);
        }
        JSONArray a=getHistory();
        for(int i=0;i<a.length();i++){
            JSONObject x=a.optJSONObject(i);
            if(x!=null)addBubble(x.optString("role","assistant"),x.optString("text",""),false);
        }
    }

    private JSONArray loadOrDiscover(String key,boolean force)throws Exception{
        String cached=prefs.getString("candidates","");
        if(!force&&!cached.isEmpty()){
            JSONArray a=new JSONArray(cached);
            if(a.length()>0)return a;
        }
        JSONArray c=discoverCandidates(key);
        prefs.putString("candidates",c.toString());
        prefs.putString("candidate_index","-1");
        return c;
    }

    private JSONArray discoverCandidates(String key)throws Exception{
        JSONArray all=new JSONArray();
        Set<String> seen=new HashSet<>();
        Exception last=null;
        for(String region:AUTO_REGIONS){
            try{
                JSONArray models=listModels(key,region);
                for(int i=0;i<models.length();i++){
                    JSONObject x=models.optJSONObject(i);
                    if(x==null)continue;
                    String id=x.optString("id","").trim();
                    if(id.isEmpty())continue;
                    String u=region+"|"+id;
                    if(seen.add(u))all.put(new JSONObject().put("region",region).put("model",id));
                }
            }catch(Exception e){last=e;}
        }
        if(all.length()==0)throw last==null?new Exception("No models returned by Bedrock"):last;
        return all;
    }

    private JSONArray listModels(String key,String region)throws Exception{
        validateRegion(region);
        String endpoint="https://bedrock-mantle."+region+".api.aws/v1/models";
        HttpURLConnection c=openBedrock(endpoint,"GET",key,15000);
        int code=c.getResponseCode();
        String raw=readResponse(c,code);
        logNetwork("BEDROCK GET /v1/models "+region+" → HTTP "+code);
        if(code<200||code>=300)throw new Exception("Model discovery HTTP "+code+" in "+region);
        JSONArray data=new JSONObject(raw).optJSONArray("data");
        return data==null?new JSONArray():data;
    }

    private ModelChoice findWorkingModel(String key,JSONArray c,int start,int end)throws Exception{
        Exception last=null;
        for(int i=Math.max(0,start);i<Math.min(end,c.length());i++){
            JSONObject x=c.getJSONObject(i);
            String r=x.optString("region"),m=x.optString("model");
            if(r.isEmpty()||m.isEmpty())continue;
            try{
                pingModel(key,r,m);
                return new ModelChoice(r,m,i);
            }catch(Exception e){last=e;}
        }
        throw last==null?new Exception("No working Chat Completions model found"):last;
    }

    private void pingModel(String key,String region,String model)throws Exception{
        validateRegionModel(region,model);
        JSONArray m=new JSONArray().put(new JSONObject().put("role","user").put("content","Reply only OK"));
        JSONObject body=new JSONObject().put("model",model).put("messages",m)
                .put("temperature",0).put("max_tokens",8);
        postChat(key,region,body,20000);
    }

    private void saveChoice(ModelChoice x,JSONArray c){
        prefs.putString("region",x.region);
        prefs.putString("model",x.model);
        prefs.putString("candidate_index",Integer.toString(x.index));
        prefs.putString("candidates",c.toString());
    }

    private JSONObject postChat(String key,String region,JSONObject body,int timeout)throws Exception{
        validateRegion(region);
        if(key==null||key.trim().isEmpty())throw new Exception("Bedrock API key required");
        String endpoint="https://bedrock-mantle."+region+".api.aws/v1/chat/completions";
        HttpURLConnection c=openBedrock(endpoint,"POST",key,timeout);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json");
        try(OutputStream os=c.getOutputStream()){
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code=c.getResponseCode();
        String raw=readResponse(c,code);
        logNetwork("BEDROCK POST /v1/chat/completions "+region+" model="+
                shortText(body.optString("model"),32)+" → HTTP "+code);
        if(code<200||code>=300)throw new Exception("HTTP "+code+": "+trimError(raw));
        return new JSONObject(raw);
    }

    private HttpURLConnection openBedrock(String endpoint,String method,String key,int timeout)throws Exception{
        if(isKilled())throw new SecurityException("Sandbox kill switch is ON");
        URL u=new URL(endpoint);
        String host=u.getHost();
        if(!"https".equalsIgnoreCase(u.getProtocol())||
                !host.startsWith("bedrock-mantle.")||!host.endsWith(".api.aws")){
            audit("BLOCKED_ENDPOINT",host);
            throw new SecurityException("Sandbox blocked non-Bedrock endpoint");
        }
        HttpURLConnection c=(HttpURLConnection)u.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(12000);
        c.setReadTimeout(timeout);
        c.setRequestProperty("Authorization","Bearer "+key);
        c.setRequestProperty("Accept","application/json");
        return c;
    }

    private String httpGetReadOnly(String url,int timeout)throws Exception{
        if(isKilled())throw new SecurityException("Sandbox kill switch is ON");
        URL u=new URL(url);
        if(!"https".equalsIgnoreCase(u.getProtocol())||!isAllowedReadOnlyHost(u.getHost())){
            audit("BLOCKED_READ_URL",safeUrl(url));
            throw new SecurityException("Sandbox blocked read-only URL");
        }
        logNetwork("GET "+safeUrl(url)+" → START");
        HttpURLConnection c=(HttpURLConnection)u.openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(12000);
        c.setReadTimeout(timeout);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept","application/vnd.github+json, text/plain, */*");
        c.setRequestProperty("User-Agent","VickyAI-ReadOnly/1.1");
        int code=c.getResponseCode();
        String raw=readResponse(c,code);
        logNetwork("GET "+safeUrl(url)+" → HTTP "+code+" bytes="+raw.length());
        if(code<200||code>=300)throw new Exception("GitHub/read-only HTTP "+code);
        return raw;
    }

    private boolean isAllowedReadOnlyHost(String host){
        if(host==null)return false;
        String h=host.toLowerCase(Locale.US);
        return h.equals("api.github.com")||h.equals("raw.githubusercontent.com")||h.equals("github.com");
    }

    private boolean isAllowedBrowserUrl(String url){
        try{
            URL u=new URL(url);
            if(!"https".equalsIgnoreCase(u.getProtocol()))return false;
            String h=u.getHost().toLowerCase(Locale.US);
            return h.equals("github.com")||h.equals("raw.githubusercontent.com")||h.equals("api.github.com");
        }catch(Exception e){return false;}
    }

    private void recordBrowserUrl(String url,String source){
        if(url==null||url.isEmpty())return;
        if(!isAllowedBrowserUrl(url))return;
        prefs.putString(PREF_LAST_URL,url);
        logNetwork("BROWSER "+source+" "+safeUrl(url));
        runOnUiThread(()->{
            if(browserUrl!=null)browserUrl.setText(url);
            if("sandbox".equals(mainMode)&&"browser".equals(sandboxMode)&&webView!=null){
                String current=webView.getUrl();
                if(current==null||!current.equals(url)){
                    webView.loadUrl(url);
                }
            }
        });
    }

    private WebResourceResponse emptyWebResponse(){
        return new WebResourceResponse("text/plain","UTF-8",
                new ByteArrayInputStream(new byte[0]));
    }

    private String readResponse(HttpURLConnection c,int code)throws Exception{
        InputStream s=code>=200&&code<300?c.getInputStream():c.getErrorStream();
        if(s==null)return "";
        StringBuilder b=new StringBuilder();
        try(BufferedReader br=new BufferedReader(new InputStreamReader(s,StandardCharsets.UTF_8))){
            String line;
            while((line=br.readLine())!=null)b.append(line).append('\n');
        }
        return b.toString();
    }

    private void addTeamEvent(String title,String detail,int accent){
        if(chatContainer==null)return;
        TextView v=label(title+"\n"+detail,12.5f,TEXT,false);
        v.setPadding(dp(13),dp(9),dp(13),dp(9));
        GradientDrawable bg=round(Color.rgb(15,19,27),dp(15));
        bg.setStroke(dp(1),accent);
        v.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,dp(4),0,dp(4));
        chatContainer.addView(v,lp);
        if(scrollView!=null)scrollView.post(()->scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void addBubble(String role,String text,boolean persist){
        if(persist)appendHistory(role,text);
        if(chatContainer==null)return;
        TextView v=label(text,15,role.equals("error")?Color.rgb(255,191,191):TEXT,false);
        v.setLineSpacing(dp(2),1f);
        v.setPadding(dp(15),dp(11),dp(15),dp(11));
        int width=role.equals("user")?dp(300):ViewGroup.LayoutParams.MATCH_PARENT;
        v.setBackground(role.equals("user")
                ?roundGradient(dp(18),Color.rgb(83,69,205),Color.rgb(41,116,204))
                :round(role.equals("error")?Color.rgb(54,25,30):PANEL,dp(18)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(role.equals("user")?dp(42):0,dp(6),0,dp(6));
        lp.gravity=role.equals("user")?Gravity.END:Gravity.START;
        chatContainer.addView(v,lp);
        if(scrollView!=null)scrollView.post(()->scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void validateRegion(String region)throws Exception{
        if(region==null||region.trim().isEmpty())throw new Exception("Bedrock region is missing. Reconnect models.");
        if(!region.matches("[a-z]{2}(-gov)?-[a-z]+-\\d"))throw new Exception("Invalid Bedrock region: "+region);
    }

    private void validateRegionModel(String region,String model)throws Exception{
        validateRegion(region);
        if(model==null||model.trim().isEmpty())throw new Exception("Bedrock model is missing. Reconnect models.");
    }

    private TextView label(String s,float size,int color,boolean bold){
        TextView v=new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private GradientDrawable round(int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private GradientDrawable roundGradient(int radius,int a,int b){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});
        d.setCornerRadius(radius);
        return d;
    }

    private String enc(String s)throws Exception{
        return URLEncoder.encode(s,StandardCharsets.UTF_8.toString()).replace("+","%20");
    }

    private String encPath(String s)throws Exception{
        String[] parts=s.split("/");
        StringBuilder b=new StringBuilder();
        for(int i=0;i<parts.length;i++){
            if(i>0)b.append("/");
            b.append(enc(parts[i]));
        }
        return b.toString();
    }

    private String safeUrl(String s){
        if(s==null)return "";
        try{
            URL u=new URL(s);
            String q=u.getQuery();
            String base=u.getProtocol()+"://"+u.getHost()+u.getPath();
            return q==null?base:base+"?…";
        }catch(Exception e){return shortText(s,120);}
    }

    private String trim(String s,int n){
        if(s==null)return "";
        return s.length()<=n?s:s.substring(0,n);
    }

    private String trimError(String s){
        if(s==null)return "Unknown error";
        s=s.trim();
        return s.length()>420?s.substring(0,420)+"…":s;
    }

    private String safeMessage(Exception e){
        String m=e.getMessage();
        return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;
    }

    private int parseInt(String s,int fallback){
        try{return Integer.parseInt(s);}catch(Exception e){return fallback;}
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private static class ModelChoice{
        final String region,model;
        final int index;
        ModelChoice(String r,String m,int i){region=r;model=m;index=i;}
    }

    private static class RepoRef{
        final String owner,repo;
        RepoRef(String o,String r){owner=o;repo=r;}
    }
}
