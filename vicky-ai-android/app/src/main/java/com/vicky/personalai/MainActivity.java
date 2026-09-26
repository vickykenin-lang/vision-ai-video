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
import java.util.TimeZone;

public class MainActivity extends Activity {
    private SecurePrefs prefs;
    private LinearLayout chatContainer;
    private EditText promptInput;
    private TextView modelChip, statusView, sandboxChip, teamStatus, chatTab, teamTab;
    private ScrollView scrollView;
    private final TextView[] teamRows = new TextView[4];
    private volatile boolean modelOperationRunning = false;
    private volatile boolean teamOperationRunning = false;
    private boolean teamMode = false;

    private final int BG=Color.rgb(7,9,13), PANEL=Color.rgb(18,21,28), PANEL2=Color.rgb(27,31,40);
    private final int TEXT=Color.rgb(242,245,249), MUTED=Color.rgb(145,154,168), ACCENT=Color.rgb(110,92,255), ACCENT2=Color.rgb(0,197,255);
    private final int SAFE=Color.rgb(57,194,123), DANGER=Color.rgb(235,84,84), AMBER=Color.rgb(234,179,74);

    private static final String SYSTEM="You are one member of Vicky AI. Be concise, practical and comfortable in Hinglish or English. Never claim live/current information unless supplied by runtime context or a connected live source. When working in Team mode, collaborate through the shared team state and do not behave as a permanently assigned specialist.";
    private static final String PREF_KILL="sandbox_kill", PREF_AUDIT="sandbox_audit", PREF_TEAM="council_models", PREF_HISTORY="chat_history_v2", PREF_MEMORY="local_memory_v1";
    private static final int HISTORY_MAX=60, AUDIT_MAX=18000;
    private static final String[] AUTO_REGIONS={"ap-south-1","ap-south-2","ap-southeast-1","ap-southeast-2","us-east-1","us-west-2","eu-west-1","eu-west-2","eu-central-1"};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); prefs=new SecurePrefs(this);
        if(prefs.getString(PREF_KILL,"").isEmpty())prefs.putString(PREF_KILL,"0");
        audit("APP_START","team=self-organizing-v1 history=local memory=local");
        buildUi(); loadHistoryIntoUi();
        if(historyLength()==0)addBubble("assistant","Ready. Team mode is self-organizing: all four models decide responsibilities for each task.",false);
        if(!isKilled()&&!prefs.getSecret("api_key").isEmpty()&&prefs.getString("model","").isEmpty())autoConnect(false);
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(12),dp(18),dp(12)); root.setBackgroundColor(BG);
        root.addView(buildHeader()); root.addView(buildTabs()); root.addView(buildModelBar()); root.addView(buildTeamPanel());
        scrollView=new ScrollView(this); scrollView.setFillViewport(true); chatContainer=new LinearLayout(this); chatContainer.setOrientation(LinearLayout.VERTICAL); chatContainer.setPadding(0,dp(8),0,dp(12)); scrollView.addView(chatContainer);
        root.addView(scrollView,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1)); root.addView(buildComposer()); setContentView(root);
    }

    private View buildHeader(){
        LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0,dp(2),0,dp(6));
        TextView mark=label("V",16,Color.WHITE,true); mark.setGravity(Gravity.CENTER); mark.setBackground(roundGradient(dp(17),ACCENT,ACCENT2)); r.addView(mark,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout t=new LinearLayout(this); t.setOrientation(LinearLayout.VERTICAL); t.setPadding(dp(11),0,0,0); t.addView(label("Vicky AI",21,TEXT,true)); t.addView(label("Private AI workspace",11,MUTED,false)); r.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        sandboxChip=label(isKilled()?"KILLED":"SAFE",10,Color.WHITE,true); sandboxChip.setGravity(Gravity.CENTER); sandboxChip.setPadding(dp(10),dp(7),dp(10),dp(7)); sandboxChip.setBackground(round(isKilled()?DANGER:SAFE,dp(13))); sandboxChip.setOnClickListener(v->showSandbox()); r.addView(sandboxChip);
        TextView settings=label("⚙",18,TEXT,false); settings.setGravity(Gravity.CENTER); settings.setBackground(round(PANEL,dp(20))); settings.setOnClickListener(v->showSettings()); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(40),dp(40)); sp.setMargins(dp(8),0,0,0); r.addView(settings,sp); return r;
    }

    private View buildTabs(){
        LinearLayout tabs=new LinearLayout(this); tabs.setPadding(dp(4),dp(4),dp(4),dp(4)); tabs.setBackground(round(PANEL,dp(22)));
        chatTab=label("Chat",13,TEXT,true); teamTab=label("Team",13,MUTED,true); chatTab.setGravity(Gravity.CENTER); teamTab.setGravity(Gravity.CENTER); chatTab.setBackground(round(PANEL2,dp(18)));
        tabs.addView(chatTab,new LinearLayout.LayoutParams(0,dp(38),1)); tabs.addView(teamTab,new LinearLayout.LayoutParams(0,dp(38),1)); chatTab.setOnClickListener(v->setTeamMode(false)); teamTab.setOnClickListener(v->setTeamMode(true));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,dp(7),0,dp(9)); tabs.setLayoutParams(lp); return tabs;
    }

    private View buildModelBar(){
        LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(12),dp(8),dp(8),dp(8)); r.setBackground(round(Color.rgb(13,16,22),dp(15)));
        r.addView(label("●",11,prefs.getString("model","").isEmpty()?MUTED:SAFE,false)); modelChip=label(shortConnectionLabel(),10.2f,TEXT,false); modelChip.setSingleLine(true); modelChip.setPadding(dp(8),0,dp(8),0); r.addView(modelChip,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView sw=label("Switch",10.5f,Color.WHITE,true); sw.setGravity(Gravity.CENTER); sw.setPadding(dp(10),dp(6),dp(10),dp(6)); sw.setBackground(round(PANEL2,dp(12))); sw.setOnClickListener(v->switchSingleModel()); r.addView(sw);
        statusView=label(initialStatus(),8.5f,MUTED,true); statusView.setPadding(dp(8),0,0,0); r.addView(statusView); return r;
    }

    private View buildTeamPanel(){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(9),dp(8),dp(9),dp(7)); box.setBackground(round(Color.rgb(13,16,22),dp(15)));
        teamStatus=label("TEAM MODELS · self-organizing · tap row to Search / Switch",9.5f,MUTED,true); teamStatus.setPadding(dp(3),0,0,dp(5)); box.addView(teamStatus);
        for(int i=0;i<4;i++){ final int slot=i; teamRows[i]=label("M"+(i+1)+" · AVAILABLE · NOT SET",10.3f,TEXT,false); teamRows[i].setPadding(dp(8),dp(5),dp(8),dp(5)); teamRows[i].setBackground(round(PANEL2,dp(10))); teamRows[i].setOnClickListener(v->showSlotDialog(slot)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(0,dp(2),0,dp(2)); box.addView(teamRows[i],p); }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,dp(7),0,dp(2)); box.setLayoutParams(lp); refreshTeamUi(); return box;
    }

    private View buildComposer(){
        LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(10),dp(8),dp(8),dp(8)); r.setBackground(round(PANEL,dp(24)));
        TextView plus=label("＋",24,MUTED,false); plus.setGravity(Gravity.CENTER); plus.setOnClickListener(v->showStorage()); r.addView(plus,new LinearLayout.LayoutParams(dp(40),dp(44)));
        promptInput=new EditText(this); promptInput.setHint("Message Vicky AI…"); promptInput.setHintTextColor(Color.rgb(100,110,126)); promptInput.setTextColor(TEXT); promptInput.setTextSize(16); promptInput.setMinLines(1); promptInput.setMaxLines(5); promptInput.setBackgroundColor(Color.TRANSPARENT); r.addView(promptInput,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView send=label("↑",22,Color.WHITE,true); send.setGravity(Gravity.CENTER); send.setBackground(roundGradient(dp(22),ACCENT,ACCENT2)); send.setOnClickListener(v->sendMessage()); r.addView(send,new LinearLayout.LayoutParams(dp(46),dp(46))); return r;
    }

    private void setTeamMode(boolean enabled){
        teamMode=enabled; chatTab.setTextColor(enabled?MUTED:TEXT); teamTab.setTextColor(enabled?TEXT:MUTED); chatTab.setBackground(enabled?round(Color.TRANSPARENT,dp(18)):round(PANEL2,dp(18))); teamTab.setBackground(enabled?round(PANEL2,dp(18)):round(Color.TRANSPARENT,dp(18))); promptInput.setHint(enabled?"Give one task to the AI team…":"Message Vicky AI…"); audit("MODE",enabled?"team":"chat"); if(enabled)ensureTeam(false);
    }

    private void showStorage(){
        String memory=prefs.getString(PREF_MEMORY,"").trim(); String msg="Persistent messages: "+historyLength()+"\nMemory: "+(memory.isEmpty()?"empty":"active")+"\n\nHistory reloads after restart. Long-term memory stays local.";
        new AlertDialog.Builder(this).setTitle("Chat History & Memory").setMessage(msg).setNeutralButton("View Memory",(d,w)->showMemory()).setNegativeButton("Close",null).setPositiveButton("New Chat",(d,w)->{prefs.putString(PREF_HISTORY,"");chatContainer.removeAllViews();addBubble("assistant","New chat started. Long-term memory retained.",false);audit("NEW_CHAT","history_cleared");}).show();
    }

    private void showMemory(){
        String m=prefs.getString(PREF_MEMORY,"").trim(); if(m.isEmpty())m="No long-term memory yet."; TextView v=label(m,13,Color.DKGRAY,false); v.setTextIsSelectable(true); v.setPadding(dp(14),dp(10),dp(14),dp(10)); ScrollView s=new ScrollView(this); s.addView(v); new AlertDialog.Builder(this).setTitle("Local Memory").setView(s).setNegativeButton("Close",null).setPositiveButton("Clear",(d,w)->prefs.putString(PREF_MEMORY,"")).show();
    }

    private void showSandbox(){
        String msg="MODE: SAFE\n\n• Bedrock endpoints only\n• INTERNET permission only\n• Local persistent history\n• Local compact memory\n• Runtime date/time injected\n• Live web research: NOT CONNECTED\n\nKill switch: "+(isKilled()?"ON":"OFF");
        new AlertDialog.Builder(this).setTitle("Sandbox Controls").setMessage(msg).setNeutralButton("Audit Log",(d,w)->showAudit()).setNegativeButton("Close",null).setPositiveButton(isKilled()?"Enable AI":"KILL SWITCH",(d,w)->{prefs.putString(PREF_KILL,isKilled()?"0":"1");refreshSandbox();}).show();
    }

    private void showAudit(){
        TextView v=label(prefs.getString(PREF_AUDIT,"No audit events yet."),12,Color.DKGRAY,false); v.setTextIsSelectable(true); v.setPadding(dp(14),dp(10),dp(14),dp(10)); ScrollView s=new ScrollView(this); s.addView(v); new AlertDialog.Builder(this).setTitle("Sandbox Audit Log").setView(s).setNegativeButton("Close",null).setPositiveButton("Clear",(d,w)->prefs.putString(PREF_AUDIT,"")).show();
    }

    private void showSettings(){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(12),dp(18),dp(8)); box.addView(label("One Bedrock API key is used for Chat and Team. Team models are verified independently.",13,Color.DKGRAY,false)); EditText key=new EditText(this); key.setHint("Bedrock API key"); key.setText(prefs.getSecret("api_key")); key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); box.addView(key);
        new AlertDialog.Builder(this).setTitle("Bedrock Connection").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save & Auto Connect",(d,w)->{String x=key.getText().toString().trim();if(x.isEmpty())return;prefs.putSecret("api_key",x);prefs.putString("model","");prefs.putString("region","");prefs.putString("candidates","");prefs.putString("candidate_index","-1");prefs.putString(PREF_TEAM,"");refreshTeamUi();autoConnect(true);}).show();
    }

    private void showSlotDialog(int slot){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(8),dp(18),dp(8)); box.addView(label(currentSlotLabel(slot),12.5f,Color.DKGRAY,false)); EditText search=new EditText(this); search.setHint("Search model name"); search.setSingleLine(true); box.addView(search);
        new AlertDialog.Builder(this).setTitle("M"+(slot+1)+" · Search / Switch").setView(box).setNeutralButton("Auto Next",(d,w)->switchTeamSlot(slot,"",true)).setNegativeButton("Close",null).setPositiveButton("Search & Switch",(d,w)->switchTeamSlot(slot,search.getText().toString().trim(),false)).show();
    }

    private String currentSlotLabel(int slot){try{JSONArray a=getTeam();if(slot<a.length()){JSONObject x=a.getJSONObject(slot);return "Current: "+x.optString("model")+"\nRegion: "+x.optString("region")+"\nNo permanent role. Responsibility is decided per task.";}}catch(Exception ignored){}return "No model locked yet.";}

    private boolean isKilled(){return "1".equals(prefs.getString(PREF_KILL,"0"));}
    private void refreshSandbox(){if(sandboxChip!=null){sandboxChip.setText(isKilled()?"KILLED":"SAFE");sandboxChip.setBackground(round(isKilled()?DANGER:SAFE,dp(13)));}updateConnectionUi(isKilled()?"BLOCKED":initialStatus());}
    private JSONArray getTeam(){try{String raw=prefs.getString(PREF_TEAM,"");return raw.isEmpty()?new JSONArray():new JSONArray(raw);}catch(Exception e){return new JSONArray();}}

    private void refreshTeamUi(){
        JSONArray a=getTeam(); if(teamStatus!=null)teamStatus.setText(a.length()==4?"TEAM MODELS · 4/4 VERIFIED · self-organizing":"TEAM MODELS · "+a.length()+"/4 VERIFIED · tap Team to prepare");
        for(int i=0;i<4;i++){if(teamRows[i]==null)continue;if(i<a.length()){JSONObject x=a.optJSONObject(i);String m=x==null?"":x.optString("model");if(m.length()>31)m=m.substring(0,31)+"…";teamRows[i].setText("M"+(i+1)+" · AVAILABLE · "+m+" · VERIFIED");teamRows[i].setTextColor(TEXT);}else{teamRows[i].setText("M"+(i+1)+" · AVAILABLE · NOT SET");teamRows[i].setTextColor(MUTED);}}
    }

    private String runtimeContext(){Date now=new Date();SimpleDateFormat d=new SimpleDateFormat("EEEE, dd MMMM yyyy",Locale.US);SimpleDateFormat t=new SimpleDateFormat("HH:mm:ss",Locale.US);return "RUNTIME FACTS — AUTHORITATIVE\nCurrent local date: "+d.format(now)+"\nCurrent local time: "+t.format(now)+"\nTimezone: "+TimeZone.getDefault().getID()+"\nLive web/news/market research: NOT CONNECTED\nNever fabricate current research, sources, prices or news.";}
    private String systemContext(){String m=prefs.getString(PREF_MEMORY,"").trim();return SYSTEM+"\n\n"+runtimeContext()+(m.isEmpty()?"":"\n\nLOCAL LONG-TERM MEMORY:\n"+m);}

    private void audit(String action,String detail){String ts=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());String clean=detail==null?"":detail.replace('\n',' ').replace('\r',' ');String all=ts+" | "+action+" | "+clean+"\n"+prefs.getString(PREF_AUDIT,"");if(all.length()>AUDIT_MAX)all=all.substring(0,AUDIT_MAX);prefs.putString(PREF_AUDIT,all);}
    private String initialStatus(){if(isKilled())return "BLOCKED";if(prefs.getSecret("api_key").isEmpty())return "KEY NEEDED";return prefs.getString("model","").isEmpty()?"UNVERIFIED":"VERIFIED";}
    private String shortConnectionLabel(){String m=prefs.getString("model","");String r=prefs.getString("region","");if(isKilled())return "Sandbox · network blocked";if(prefs.getSecret("api_key").isEmpty())return "Bedrock · API key not set";return m.isEmpty()?"Bedrock · finding a working model…":m+" · "+r;}
    private void updateConnectionUi(String s){if(modelChip!=null)modelChip.setText(shortConnectionLabel());if(statusView!=null)statusView.setText(s);}

    private void autoConnect(boolean force){if(modelOperationRunning||isKilled())return;final String key=prefs.getSecret("api_key");if(key.isEmpty()){showSettings();return;}modelOperationRunning=true;updateConnectionUi("DISCOVERING");new Thread(()->{try{JSONArray c=loadOrDiscover(key,force);ModelChoice x=findWorkingModel(key,c,0,c.length());saveChoice(x,c);audit("MODEL_VERIFIED",x.model+" @ "+x.region);runOnUiThread(()->updateConnectionUi("VERIFIED"));}catch(Exception e){audit("AUTO_CONNECT_FAILED",safeMessage(e));runOnUiThread(()->updateConnectionUi("NO MODEL"));}finally{modelOperationRunning=false;}}).start();}

    private void switchSingleModel(){if(modelOperationRunning||isKilled())return;final String key=prefs.getSecret("api_key");if(key.isEmpty()){showSettings();return;}modelOperationRunning=true;updateConnectionUi("SWITCHING");new Thread(()->{try{JSONArray c=loadOrDiscover(key,false);int cur=parseInt(prefs.getString("candidate_index","-1"),-1);ModelChoice x;try{x=findWorkingModel(key,c,cur+1,c.length());}catch(Exception e){x=findWorkingModel(key,c,0,Math.max(0,cur));}saveChoice(x,c);runOnUiThread(()->updateConnectionUi("VERIFIED"));}catch(Exception e){runOnUiThread(()->updateConnectionUi(initialStatus()));}finally{modelOperationRunning=false;}}).start();}

    private void ensureTeam(boolean force){
        if(teamOperationRunning||isKilled())return;final String key=prefs.getSecret("api_key");if(key.isEmpty()){showSettings();return;}if(!force&&getTeam().length()==4){refreshTeamUi();return;}teamOperationRunning=true;audit("TEAM_VERIFY_START",force?"force":"normal");
        new Thread(()->{try{JSONArray candidates=loadOrDiscover(key,force);JSONArray locked=findFourUniqueWorkingModels(key,candidates);prefs.putString(PREF_TEAM,locked.toString());JSONObject first=locked.getJSONObject(0);prefs.putString("model",first.optString("model"));prefs.putString("region",first.optString("region"));audit("TEAM_VERIFIED","models=4");runOnUiThread(()->{refreshTeamUi();updateConnectionUi("VERIFIED");addTeamEvent("Team ready","Four unique models verified. Roles are not fixed; responsibilities will be negotiated for each task.",SAFE);});}catch(Exception e){prefs.putString(PREF_TEAM,"");audit("TEAM_VERIFY_FAILED",safeMessage(e));runOnUiThread(()->{refreshTeamUi();addBubble("error","Team setup failed\n"+safeMessage(e),false);});}finally{teamOperationRunning=false;}}).start();
    }

    private JSONArray findFourUniqueWorkingModels(String key,JSONArray candidates)throws Exception{JSONArray locked=new JSONArray();Set<String> used=new HashSet<>();Exception last=null;for(int i=0;i<candidates.length()&&locked.length()<4;i++){JSONObject c=candidates.getJSONObject(i);String r=c.optString("region"),m=c.optString("model");if(m.isEmpty()||used.contains(m))continue;int slot=locked.length();final int s=slot;runOnUiThread(()->teamRows[s].setText("M"+(s+1)+" · PINGING · "+m));try{pingModel(key,r,m);used.add(m);locked.put(new JSONObject().put("region",r).put("model",m).put("candidate_index",i));}catch(Exception e){last=e;}}if(locked.length()<4)throw last==null?new Exception("Only "+locked.length()+" unique working models found; 4 required"):new Exception("Only "+locked.length()+" unique models verified. "+safeMessage(last));return locked;}

    private void switchTeamSlot(int slot,String query,boolean autoNext){if(teamOperationRunning||isKilled())return;final String key=prefs.getSecret("api_key");if(key.isEmpty()){showSettings();return;}teamOperationRunning=true;teamRows[slot].setText("M"+(slot+1)+" · SEARCHING / PINGING…");new Thread(()->{try{JSONArray c=loadOrDiscover(key,false),team=getTeam();Set<String> used=new HashSet<>();for(int i=0;i<team.length();i++)if(i!=slot)used.add(team.getJSONObject(i).optString("model"));String q=query==null?"":query.toLowerCase(Locale.US);int start=0;if(autoNext&&slot<team.length())start=team.getJSONObject(slot).optInt("candidate_index",-1)+1;JSONObject selected=null;Exception last=null;for(int pass=0;pass<2&&selected==null;pass++){int from=pass==0?Math.max(0,start):0,to=pass==0?c.length():Math.max(0,start);for(int i=from;i<to;i++){JSONObject x=c.getJSONObject(i);String m=x.optString("model");if(m.isEmpty()||used.contains(m)||(!q.isEmpty()&&!m.toLowerCase(Locale.US).contains(q)))continue;try{pingModel(key,x.optString("region"),m);selected=new JSONObject().put("model",m).put("region",x.optString("region")).put("candidate_index",i);break;}catch(Exception e){last=e;}}}if(selected==null)throw last==null?new Exception("No working replacement found"):last;while(team.length()<4)team.put(new JSONObject());team.put(slot,selected);prefs.putString(PREF_TEAM,team.toString());if(slot==0){prefs.putString("model",selected.optString("model"));prefs.putString("region",selected.optString("region"));}String name=selected.optString("model");audit("TEAM_SLOT_SWITCHED","M"+(slot+1)+" "+name);runOnUiThread(()->{refreshTeamUi();Toast.makeText(this,"M"+(slot+1)+" switched to "+name,Toast.LENGTH_LONG).show();});}catch(Exception e){audit("TEAM_SLOT_SWITCH_FAILED",safeMessage(e));runOnUiThread(()->{refreshTeamUi();Toast.makeText(this,safeMessage(e),Toast.LENGTH_LONG).show();});}finally{teamOperationRunning=false;}}).start();}

    private void sendMessage(){String text=promptInput.getText().toString().trim();if(text.isEmpty())return;if(isKilled()){Toast.makeText(this,"Sandbox kill switch is ON",Toast.LENGTH_SHORT).show();return;}if(prefs.getSecret("api_key").isEmpty()){showSettings();return;}promptInput.setText("");addBubble("user",text,true);if(teamMode)runTeamTask(text);else runSingleTask(text);}

    private void runSingleTask(String text){if(prefs.getString("model","").isEmpty()){autoConnect(false);addBubble("error","No verified chat model yet.",false);return;}statusView.setText("THINKING");new Thread(()->{try{String answer=callBedrock();appendHistory("assistant",answer);runOnUiThread(()->{addBubble("assistant",answer,false);updateConnectionUi("VERIFIED");});updateMemoryAsync(text,answer);}catch(Exception e){runOnUiThread(()->{addBubble("error","Request failed\n"+safeMessage(e),false);statusView.setText("FAILED");});}}).start();}

    private void runTeamTask(String task){
        if(teamOperationRunning){addBubble("error","Team is already working.",false);return;}JSONArray team=getTeam();if(team.length()!=4){addBubble("assistant","Team needs four verified models first. Verification started; send the task again when all four are VERIFIED.",false);ensureTeam(false);return;}final JSONArray roster=team;teamOperationRunning=true;audit("TEAM_TASK_START","chars="+task.length());
        addTeamEvent("Team huddle","All four models are reading the same task and proposing responsibilities. No fixed roles.",ACCENT2);
        if(needsLiveResearch(task))addTeamEvent("Research capability","Live web research is not connected in this build. Team may reason from model knowledge and local context only; it must not claim live research.",AMBER);
        new Thread(()->{try{
            JSONArray proposals=new JSONArray(); String rosterText=rosterText(roster);
            for(int i=0;i<4;i++){JSONObject member=roster.getJSONObject(i);updateTeamRow(i,"HUDDLE · proposing responsibility",member.optString("model"),AMBER);String instruction="You are teammate M"+(i+1)+" in a self-organizing 4-model team. Read the task and roster. Propose the most useful responsibility YOU should take for THIS task, and nominate the teammate best suited to coordinate the final synthesis. Do not assume any permanent role. Return STRICT JSON only: {\"responsibility\":\"short task-specific responsibility\",\"coordinator_vote\":\"M1|M2|M3|M4\",\"reason\":\"very short reason\"}.";String out=memberCall(member,task,rosterText,instruction,260);JSONObject p=parseJsonObject(out);p.put("slot","M"+(i+1));p.put("model",member.optString("model"));proposals.put(p);}
            int coordinatorIndex=chooseCoordinator(proposals); JSONObject coordinator=roster.getJSONObject(coordinatorIndex); addTeamEvent("Huddle decision","Coordinator for this task: M"+(coordinatorIndex+1)+" · "+coordinator.optString("model"),SAFE);
            String planInstruction="You are the temporary coordinator chosen by the team for THIS task only. Using the four proposals below, assign a distinct concise responsibility to each teammate. Avoid duplicate work. The assignments must match the actual task and can be completely different on the next task. Return STRICT JSON only: {\"assignments\":[{\"slot\":\"M1\",\"task\":\"...\"},{\"slot\":\"M2\",\"task\":\"...\"},{\"slot\":\"M3\",\"task\":\"...\"},{\"slot\":\"M4\",\"task\":\"...\"}]}.";
            String planRaw=memberCall(coordinator,task,proposals.toString(),planInstruction,420);JSONObject plan=parseJsonObject(planRaw);JSONArray assignments=plan.optJSONArray("assignments");if(assignments==null||assignments.length()<4)assignments=fallbackAssignments(proposals);
            for(int i=0;i<4;i++){String assigned=findAssignment(assignments,i);final int row=i;runOnUiThread(()->teamRows[row].setText("M"+(row+1)+" · "+shortText(assigned,42)+" · ASSIGNED"));}
            addTeamEvent("Team plan",assignmentsSummary(assignments),ACCENT2);
            String shared=runtimeContext()+"\n\nORIGINAL TASK:\n"+task+"\n\nTEAM PLAN:\n"+assignments.toString()+"\n\nSHARED FINDINGS:\n";
            for(int i=0;i<4;i++){JSONObject member=roster.getJSONObject(i);String assigned=findAssignment(assignments,i);updateTeamRow(i,"WORKING · "+shortText(assigned,28),member.optString("model"),AMBER);String workInstruction="You are teammate M"+(i+1)+". Your temporary responsibility for THIS task is: "+assigned+". Read the existing shared findings from teammates. Do your part, correct earlier findings if needed, and contribute only concise useful findings. Do not produce the final polished answer. If the task asks for live research, remember live web retrieval is NOT connected and explicitly mark claims that cannot be verified live.";String finding=memberCall(member,task,shared,workInstruction,520);shared+="\nM"+(i+1)+" ("+assigned+"):\n"+finding+"\n";updateTeamRow(i,"DONE · "+shortText(assigned,28),member.optString("model"),SAFE);}
            String finalInstruction="You are the temporary coordinator for this task. Read the full shared team findings and produce ONE concise unified answer for the user. Remove duplicates and disagreements that were resolved. If a material disagreement remains, mention it briefly. Never say the team performed live research because live web retrieval is NOT connected. If the user explicitly requested research/current evidence, clearly state that limitation before giving the best evidence-based answer available from internal knowledge. Answer in the user's language/style.";
            String finalAnswer=memberCall(coordinator,task,shared,finalInstruction,900);appendHistory("assistant",finalAnswer);audit("TEAM_TASK_SUCCESS","coordinator=M"+(coordinatorIndex+1)+" chars="+finalAnswer.length());runOnUiThread(()->{refreshTeamUi();addTeamEvent("Team complete","Self-organized work finished; one concise team answer produced.",SAFE);addBubble("assistant",finalAnswer,false);});updateMemoryAsync(task,finalAnswer);
        }catch(Exception e){audit("TEAM_TASK_FAILED",safeMessage(e));runOnUiThread(()->{refreshTeamUi();addBubble("error","Team failed\n"+safeMessage(e),false);});}finally{teamOperationRunning=false;}}).start();
    }

    private boolean needsLiveResearch(String s){String x=s.toLowerCase(Locale.US);return x.contains("research")||x.contains("latest")||x.contains("current")||x.contains("today")||x.contains("aaj")||x.contains("news")||x.contains("price")||x.contains("market")||x.contains("search");}
    private String rosterText(JSONArray team)throws Exception{StringBuilder b=new StringBuilder("TEAM ROSTER:\n");for(int i=0;i<team.length();i++){JSONObject x=team.getJSONObject(i);b.append("M").append(i+1).append(" = ").append(x.optString("model")).append(" @ ").append(x.optString("region")).append("\n");}return b.toString();}
    private JSONObject parseJsonObject(String raw)throws Exception{String s=raw.trim();int a=s.indexOf('{'),b=s.lastIndexOf('}');if(a<0||b<=a)throw new Exception("Team model returned invalid planning JSON");return new JSONObject(s.substring(a,b+1));}
    private int chooseCoordinator(JSONArray p){int[] votes={0,0,0,0};for(int i=0;i<p.length();i++){String v=p.optJSONObject(i)==null?"":p.optJSONObject(i).optString("coordinator_vote","").toUpperCase(Locale.US);if(v.matches("M[1-4]"))votes[Integer.parseInt(v.substring(1))-1]++;}int best=0;for(int i=1;i<4;i++)if(votes[i]>votes[best])best=i;return best;}
    private JSONArray fallbackAssignments(JSONArray proposals)throws Exception{JSONArray a=new JSONArray();for(int i=0;i<4;i++){JSONObject p=proposals.getJSONObject(i);a.put(new JSONObject().put("slot","M"+(i+1)).put("task",p.optString("responsibility","Contribute useful independent analysis")));}return a;}
    private String findAssignment(JSONArray a,int slot){String key="M"+(slot+1);for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&key.equalsIgnoreCase(x.optString("slot")))return x.optString("task","Contribute useful analysis");}return "Contribute useful analysis";}
    private String assignmentsSummary(JSONArray a){StringBuilder b=new StringBuilder();for(int i=0;i<4;i++)b.append("M").append(i+1).append(" → ").append(findAssignment(a,i)).append(i==3?"":"\n");return b.toString();}
    private String shortText(String s,int n){if(s==null)return "";return s.length()<=n?s:s.substring(0,n)+"…";}
    private void updateTeamRow(int slot,String state,String model,int color){runOnUiThread(()->{if(teamRows[slot]!=null){teamRows[slot].setText("M"+(slot+1)+" · "+state+" · "+shortText(model,27));teamRows[slot].setTextColor(color);}});}

    private String memberCall(JSONObject member,String task,String shared,String instruction,int maxTokens)throws Exception{String key=prefs.getSecret("api_key"),model=member.optString("model"),region=member.optString("region");JSONArray messages=new JSONArray();messages.put(new JSONObject().put("role","system").put("content",SYSTEM+"\n\n"+runtimeContext()+"\n\n"+instruction));messages.put(new JSONObject().put("role","user").put("content","ORIGINAL TASK:\n"+task+"\n\nTEAM STATE:\n"+trim(shared,10000)));JSONObject body=new JSONObject().put("model",model).put("messages",messages).put("temperature",0.2).put("max_tokens",maxTokens);JSONObject json=postChat(key,region,body,120000);String out=json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","").trim();if(out.isEmpty())throw new Exception("Empty response from "+model);return out;}

    private String callBedrock()throws Exception{String key=prefs.getSecret("api_key"),model=prefs.getString("model",""),region=prefs.getString("region","");JSONArray messages=new JSONArray();messages.put(new JSONObject().put("role","system").put("content",systemContext()));JSONArray h=getHistory();int start=Math.max(0,h.length()-12);for(int i=start;i<h.length();i++){JSONObject x=h.optJSONObject(i);if(x==null)continue;String role=x.optString("role","");if(role.equals("user")||role.equals("assistant"))messages.put(new JSONObject().put("role",role).put("content",x.optString("text","")));}JSONObject body=new JSONObject().put("model",model).put("messages",messages).put("temperature",0.35).put("max_tokens",900);JSONObject json=postChat(key,region,body,120000);return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","No text returned.");}

    private void updateMemoryAsync(String user,String assistant){final String key=prefs.getSecret("api_key"),model=prefs.getString("model",""),region=prefs.getString("region","");if(key.isEmpty()||model.isEmpty()||isKilled())return;new Thread(()->{try{String old=prefs.getString(PREF_MEMORY,"").trim();JSONArray m=new JSONArray();m.put(new JSONObject().put("role","system").put("content","Maintain compact long-term memory. Keep only durable user preferences, projects, decisions, recurring instructions and stable facts explicitly stated by the user. Ignore transient questions and model claims. Return concise bullets, max 300 words."));m.put(new JSONObject().put("role","user").put("content","EXISTING MEMORY:\n"+old+"\n\nLATEST EXCHANGE:\nUser: "+user+"\nAssistant: "+assistant));JSONObject body=new JSONObject().put("model",model).put("messages",m).put("temperature",0).put("max_tokens",400);JSONObject j=postChat(key,region,body,60000);String u=j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","").trim();if(!u.isEmpty())prefs.putString(PREF_MEMORY,u);}catch(Exception e){audit("MEMORY_UPDATE_FAILED",safeMessage(e));}}).start();}

    private JSONArray getHistory(){try{String raw=prefs.getString(PREF_HISTORY,"");return raw.isEmpty()?new JSONArray():new JSONArray(raw);}catch(Exception e){return new JSONArray();}}
    private int historyLength(){return getHistory().length();}
    private void appendHistory(String role,String text){try{JSONArray old=getHistory(),out=new JSONArray();int start=Math.max(0,old.length()-(HISTORY_MAX-1));for(int i=start;i<old.length();i++)out.put(old.getJSONObject(i));out.put(new JSONObject().put("role",role).put("text",text).put("ts",System.currentTimeMillis()));prefs.putString(PREF_HISTORY,out.toString());}catch(Exception e){audit("HISTORY_SAVE_FAILED",safeMessage(e));}}
    private void loadHistoryIntoUi(){JSONArray a=getHistory();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)addBubble(x.optString("role","assistant"),x.optString("text",""),false);}}

    private JSONArray loadOrDiscover(String key,boolean force)throws Exception{String cached=prefs.getString("candidates","");if(!force&&!cached.isEmpty())return new JSONArray(cached);JSONArray c=discoverCandidates(key);prefs.putString("candidates",c.toString());prefs.putString("candidate_index","-1");return c;}
    private JSONArray discoverCandidates(String key)throws Exception{JSONArray all=new JSONArray();Set<String> seen=new HashSet<>();Exception last=null;for(String region:AUTO_REGIONS){try{JSONArray models=listModels(key,region);for(int i=0;i<models.length();i++){JSONObject x=models.optJSONObject(i);if(x==null)continue;String id=x.optString("id","").trim();if(id.isEmpty())continue;String u=region+"|"+id;if(seen.add(u))all.put(new JSONObject().put("region",region).put("model",id));}}catch(Exception e){last=e;}}if(all.length()==0)throw last==null?new Exception("No models returned by Bedrock"):last;return all;}
    private JSONArray listModels(String key,String region)throws Exception{String endpoint="https://bedrock-mantle."+region+".api.aws/v1/models";HttpURLConnection c=open(endpoint,"GET",key,15000);int code=c.getResponseCode();String raw=readResponse(c,code);if(code<200||code>=300)throw new Exception("Model discovery HTTP "+code+" in "+region);JSONArray data=new JSONObject(raw).optJSONArray("data");return data==null?new JSONArray():data;}
    private ModelChoice findWorkingModel(String key,JSONArray c,int start,int end)throws Exception{Exception last=null;for(int i=Math.max(0,start);i<Math.min(end,c.length());i++){JSONObject x=c.getJSONObject(i);try{pingModel(key,x.optString("region"),x.optString("model"));return new ModelChoice(x.optString("region"),x.optString("model"),i);}catch(Exception e){last=e;}}throw last==null?new Exception("No working Chat Completions model found"):last;}
    private void pingModel(String key,String region,String model)throws Exception{JSONArray m=new JSONArray().put(new JSONObject().put("role","user").put("content","Reply only OK"));JSONObject body=new JSONObject().put("model",model).put("messages",m).put("temperature",0).put("max_tokens",8);postChat(key,region,body,20000);}
    private void saveChoice(ModelChoice x,JSONArray c){prefs.putString("region",x.region);prefs.putString("model",x.model);prefs.putString("candidate_index",Integer.toString(x.index));prefs.putString("candidates",c.toString());}

    private JSONObject postChat(String key,String region,JSONObject body,int timeout)throws Exception{String endpoint="https://bedrock-mantle."+region+".api.aws/v1/chat/completions";HttpURLConnection c=open(endpoint,"POST",key,timeout);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();String raw=readResponse(c,code);if(code<200||code>=300)throw new Exception("HTTP "+code+": "+trimError(raw));return new JSONObject(raw);}
    private HttpURLConnection open(String endpoint,String method,String key,int timeout)throws Exception{if(isKilled())throw new SecurityException("Sandbox kill switch is ON");URL u=new URL(endpoint);String host=u.getHost();if(!"https".equalsIgnoreCase(u.getProtocol())||!host.startsWith("bedrock-mantle.")||!host.endsWith(".api.aws")){audit("BLOCKED_ENDPOINT",host);throw new SecurityException("Sandbox blocked non-Bedrock endpoint");}HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(timeout);c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Accept","application/json");return c;}
    private String readResponse(HttpURLConnection c,int code)throws Exception{InputStream s=code>=200&&code<300?c.getInputStream():c.getErrorStream();if(s==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(s,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)b.append(line);}return b.toString();}

    private void addTeamEvent(String title,String detail,int accent){TextView v=label(title+"\n"+detail,12.5f,TEXT,false);v.setPadding(dp(13),dp(9),dp(13),dp(9));GradientDrawable bg=round(Color.rgb(15,19,27),dp(15));bg.setStroke(dp(1),accent);v.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,dp(4),0,dp(4));chatContainer.addView(v,lp);scrollView.post(()->scrollView.fullScroll(View.FOCUS_DOWN));}
    private void addBubble(String role,String text,boolean persist){if(persist)appendHistory(role,text);TextView v=label(text,15,role.equals("error")?Color.rgb(255,191,191):TEXT,false);v.setLineSpacing(dp(2),1f);v.setPadding(dp(15),dp(11),dp(15),dp(11));int width=role.equals("user")?dp(300):ViewGroup.LayoutParams.MATCH_PARENT;v.setBackground(role.equals("user")?roundGradient(dp(18),Color.rgb(83,69,205),Color.rgb(41,116,204)):round(role.equals("error")?Color.rgb(54,25,30):PANEL,dp(18)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(role.equals("user")?dp(42):0,dp(6),0,dp(6));lp.gravity=role.equals("user")?Gravity.END:Gravity.START;chatContainer.addView(v,lp);scrollView.post(()->scrollView.fullScroll(View.FOCUS_DOWN));}
    private TextView label(String s,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private GradientDrawable roundGradient(int radius,int a,int b){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});d.setCornerRadius(radius);return d;}
    private String trim(String s,int n){if(s==null)return "";return s.length()<=n?s:s.substring(0,n);}
    private String trimError(String s){if(s==null)return "Unknown error";s=s.trim();return s.length()>420?s.substring(0,420)+"…":s;}
    private String safeMessage(Exception e){String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;}
    private int parseInt(String s,int fallback){try{return Integer.parseInt(s);}catch(Exception e){return fallback;}}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static class ModelChoice{final String region,model;final int index;ModelChoice(String r,String m,int i){region=r;model=m;index=i;}}
}
