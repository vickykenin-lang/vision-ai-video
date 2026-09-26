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

public class TeamActivity extends Activity {
    private SecurePrefs prefs;
    private LinearLayout chat;
    private ScrollView scroll;
    private EditText input;
    private TextView teamStatus;
    private TextView singleStatus;
    private final TextView[] rows = new TextView[4];
    private volatile boolean busy = false;
    private boolean councilMode = true;

    private final int BG=Color.rgb(7,9,13), PANEL=Color.rgb(18,21,28), PANEL2=Color.rgb(27,31,40);
    private final int TEXT=Color.rgb(242,245,249), MUTED=Color.rgb(145,154,168), ACCENT=Color.rgb(110,92,255), ACCENT2=Color.rgb(0,197,255);
    private final int SAFE=Color.rgb(57,194,123), DANGER=Color.rgb(235,84,84), AMBER=Color.rgb(234,179,74);

    private static final String PREF_COUNCIL="council_models";
    private static final String PREF_HISTORY="chat_history_v2";
    private static final String PREF_MEMORY="local_memory_v1";
    private static final String PREF_KILL="sandbox_kill";
    private static final String PREF_AUDIT="sandbox_audit";
    private static final String SYSTEM="You are one teammate inside Vicky AI. Work collaboratively, share useful information, avoid duplication, be concise, and never invent current/live facts.";
    private static final String[] REGIONS=new String[]{"ap-south-1","ap-south-2","ap-southeast-1","ap-southeast-2","us-east-1","us-west-2","eu-west-1","eu-west-2","eu-central-1"};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); prefs=new SecurePrefs(this); build(); loadHistory();
        if(getHistory().length()==0) bubble("assistant","Team mode ready. The four models are teammates, not fixed roles. For each task they will huddle, choose responsibilities, nominate a coordinator, share findings, and return one concise team answer.",false);
        refreshRows();
        if(!isKilled() && !prefs.getSecret("api_key").isEmpty()) ensureTeam(false);
    }

    private void build(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(10),dp(16),dp(10)); root.setBackgroundColor(BG);
        root.addView(header()); root.addView(modeBar()); root.addView(singleBar()); root.addView(teamPanel());
        scroll=new ScrollView(this); scroll.setFillViewport(true); chat=new LinearLayout(this); chat.setOrientation(LinearLayout.VERTICAL); chat.setPadding(0,dp(8),0,dp(10)); scroll.addView(chat); root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        root.addView(composer()); setContentView(root);
    }

    private View header(){
        LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0,0,0,dp(8));
        TextView v=t("V",17,Color.WHITE,true); v.setGravity(Gravity.CENTER); v.setBackground(grad(dp(18),ACCENT,ACCENT2)); r.addView(v,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout tt=new LinearLayout(this); tt.setOrientation(LinearLayout.VERTICAL); tt.setPadding(dp(12),0,0,0); tt.addView(t("Vicky AI",22,TEXT,true)); tt.addView(t("Self-organizing AI team",11,MUTED,false)); r.addView(tt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView safe=t(isKilled()?"KILLED":"SAFE",10,Color.WHITE,true); safe.setPadding(dp(10),dp(7),dp(10),dp(7)); safe.setBackground(round(isKilled()?DANGER:SAFE,dp(13))); safe.setOnClickListener(x->sandboxDialog()); r.addView(safe); return r;
    }

    private View modeBar(){
        LinearLayout r=new LinearLayout(this); r.setPadding(dp(4),dp(4),dp(4),dp(4)); r.setBackground(round(PANEL,dp(22)));
        TextView chatBtn=t("Chat",13,MUTED,true), teamBtn=t("Team",13,TEXT,true); chatBtn.setGravity(Gravity.CENTER); teamBtn.setGravity(Gravity.CENTER); teamBtn.setBackground(round(PANEL2,dp(18)));
        r.addView(chatBtn,new LinearLayout.LayoutParams(0,dp(38),1)); r.addView(teamBtn,new LinearLayout.LayoutParams(0,dp(38),1));
        chatBtn.setOnClickListener(v->{councilMode=false; chatBtn.setTextColor(TEXT); teamBtn.setTextColor(MUTED); chatBtn.setBackground(round(PANEL2,dp(18))); teamBtn.setBackground(round(Color.TRANSPARENT,dp(18))); input.setHint("Message Vicky AI…");});
        teamBtn.setOnClickListener(v->{councilMode=true; chatBtn.setTextColor(MUTED); teamBtn.setTextColor(TEXT); teamBtn.setBackground(round(PANEL2,dp(18))); chatBtn.setBackground(round(Color.TRANSPARENT,dp(18))); input.setHint("Give one task to the AI team…"); ensureTeam(false);});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(0,0,0,dp(8)); r.setLayoutParams(p); return r;
    }

    private View singleBar(){
        LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(11),dp(8),dp(8),dp(8)); r.setBackground(round(Color.rgb(13,16,22),dp(14)));
        singleStatus=t(singleLabel(),10.2f,TEXT,false); singleStatus.setSingleLine(true); r.addView(singleStatus,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView sw=t("Switch",10,Color.WHITE,true); sw.setPadding(dp(10),dp(6),dp(10),dp(6)); sw.setBackground(round(PANEL2,dp(12))); sw.setOnClickListener(v->switchSingle()); r.addView(sw); return r;
    }

    private View teamPanel(){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(9),dp(8),dp(9),dp(7)); box.setBackground(round(Color.rgb(13,16,22),dp(15)));
        teamStatus=t("TEAM · preparing…",9.8f,MUTED,true); teamStatus.setPadding(dp(3),0,0,dp(5)); box.addView(teamStatus);
        for(int i=0;i<4;i++){ final int slot=i; rows[i]=t("M"+(i+1)+" · AVAILABLE",10.3f,TEXT,false); rows[i].setPadding(dp(8),dp(6),dp(8),dp(6)); rows[i].setBackground(round(PANEL2,dp(10))); rows[i].setOnClickListener(v->slotDialog(slot)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(0,dp(2),0,dp(2)); box.addView(rows[i],p); }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,dp(7),0,dp(2)); box.setLayoutParams(lp); return box;
    }

    private View composer(){
        LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(9),dp(7),dp(7),dp(7)); r.setBackground(round(PANEL,dp(24)));
        TextView plus=t("＋",23,MUTED,false); plus.setGravity(Gravity.CENTER); plus.setOnClickListener(v->historyDialog()); r.addView(plus,new LinearLayout.LayoutParams(dp(40),dp(44)));
        input=new EditText(this); input.setHint("Give one task to the AI team…"); input.setHintTextColor(Color.rgb(100,110,126)); input.setTextColor(TEXT); input.setTextSize(16); input.setMinLines(1); input.setMaxLines(5); input.setBackgroundColor(Color.TRANSPARENT); r.addView(input,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView send=t("↑",22,Color.WHITE,true); send.setGravity(Gravity.CENTER); send.setBackground(grad(dp(22),ACCENT,ACCENT2)); send.setOnClickListener(v->send()); r.addView(send,new LinearLayout.LayoutParams(dp(46),dp(46))); return r;
    }

    private void send(){
        String q=input.getText().toString().trim(); if(q.isEmpty())return; if(isKilled()){Toast.makeText(this,"Sandbox kill switch is ON",Toast.LENGTH_SHORT).show();return;} if(prefs.getSecret("api_key").isEmpty()){settings();return;}
        input.setText(""); bubble("user",q,true); if(councilMode) runTeam(q); else runSingle();
    }

    private void runSingle(){
        new Thread(()->{try{String ans=callSingle(); append("assistant",ans); runOnUiThread(()->bubble("assistant",ans,false));}catch(Exception e){runOnUiThread(()->bubble("error",safe(e),false));}}).start();
    }

    private void runTeam(String task){
        if(busy){bubble("error","Team is already working on another task.",false);return;} JSONArray team=getCouncil(); if(team.length()!=4){bubble("assistant","Team needs 4 verified models first. Verification has started; send the task again when all four are ready.",false);ensureTeam(false);return;}
        busy=true; event("Team huddle","All four teammates are deciding how to divide this task.",ACCENT2);
        new Thread(()->{try{
            JSONArray proposals=new JSONArray();
            for(int i=0;i<4;i++){JSONObject m=team.getJSONObject(i); stage(i,"HUDDLE · proposing responsibility",m.optString("model")); proposals.put(proposal(m,i,task,team));}
            int coordinator=pickCoordinator(proposals);
            JSONObject planModel=team.getJSONObject(coordinator);
            stage(coordinator,"COORDINATING · team allocation",planModel.optString("model"));
            JSONObject plan=allocation(planModel,task,team,proposals,coordinator);
            showAllocation(plan,team,coordinator);
            String shared=runtime()+"\n\nTASK:\n"+task+"\n\nTEAM PLAN:\n"+plan.toString()+"\n\nSHARED FINDINGS:\n";
            for(int i=0;i<4;i++){JSONObject m=team.getJSONObject(i); String resp=responsibility(plan,i); stage(i,"WORKING · "+resp,m.optString("model")); String finding=work(m,task,resp,shared); shared += "\n\nM"+(i+1)+" FINDINGS ("+resp+"):\n"+finding;}
            stage(coordinator,"SYNTHESIZING · team answer",planModel.optString("model"));
            String finalAnswer=finalizeTeam(planModel,task,plan,shared);
            append("assistant",finalAnswer); audit("TEAM_SUCCESS","coordinator=M"+(coordinator+1)+" chars="+finalAnswer.length());
            runOnUiThread(()->{refreshRows(); event("Team complete","Responsibilities were chosen for this task, not hard-coded.",SAFE); bubble("assistant",finalAnswer,false);});
            updateMemory(task,finalAnswer);
        }catch(Exception e){audit("TEAM_FAILED",safe(e)); runOnUiThread(()->{refreshRows();bubble("error","Team failed\n"+safe(e),false);});}finally{busy=false;}}).start();
    }

    private JSONObject proposal(JSONObject m,int slot,String task,JSONArray team)throws Exception{
        String prompt="You are teammate M"+(slot+1)+" in a 4-model AI team. For THIS task only, propose the most useful distinct responsibility you should take, and nominate which teammate should coordinate the final team result. Avoid duplicating another likely workstream. Return strict JSON only: {\"responsibility\":\"short task-specific role\",\"coordinator\":1,\"reason\":\"short reason\"}. Coordinator must be 1-4.\n\nTEAM MODELS:\n"+teamNames(team)+"\n\nTASK:\n"+task;
        String out=call(m,SYSTEM+"\n"+runtime(),prompt,260); return parseJson(out,new JSONObject().put("responsibility","Analyze a distinct useful part of the task").put("coordinator",slot+1).put("reason","fallback"));
    }

    private int pickCoordinator(JSONArray proposals){int[] votes=new int[4]; for(int i=0;i<proposals.length();i++){JSONObject p=proposals.optJSONObject(i); int c=p==null?1:p.optInt("coordinator",1); if(c>=1&&c<=4)votes[c-1]++;} int best=0; for(int i=1;i<4;i++)if(votes[i]>votes[best])best=i; return best;}

    private JSONObject allocation(JSONObject coordinatorModel,String task,JSONArray team,JSONArray proposals,int coordinator)throws Exception{
        String prompt="The team nominated M"+(coordinator+1)+" as temporary coordinator for THIS task only. Read all teammate proposals and assign four distinct, complementary responsibilities. No permanent roles. A teammate may be assigned 'standby/verify only' if the task is simple. Return strict JSON only: {\"M1\":\"responsibility\",\"M2\":\"responsibility\",\"M3\":\"responsibility\",\"M4\":\"responsibility\"}. Keep each responsibility under 12 words.\n\nTASK:\n"+task+"\n\nMODELS:\n"+teamNames(team)+"\n\nPROPOSALS:\n"+proposals.toString();
        JSONObject fallback=new JSONObject(); for(int i=0;i<4;i++){JSONObject p=proposals.optJSONObject(i); fallback.put("M"+(i+1),p==null?"Contribute a distinct useful perspective":p.optString("responsibility","Contribute a distinct useful perspective"));}
        return parseJson(call(coordinatorModel,SYSTEM+"\n"+runtime(),prompt,360),fallback);
    }

    private String responsibility(JSONObject plan,int slot){String r=plan.optString("M"+(slot+1),"Contribute a distinct useful perspective").trim(); return r.isEmpty()?"Contribute a distinct useful perspective":r;}

    private String work(JSONObject m,String task,String resp,String shared)throws Exception{
        String prompt="You are one teammate. Your dynamically assigned responsibility for THIS task is: "+resp+". Work on that responsibility, but read the shared team state first so you do not repeat what is already covered. Add only useful facts, reasoning, corrections, risks or actions. Share concise findings with the next teammates. Do not write the final answer.\n\nTASK:\n"+task+"\n\nSHARED STATE:\n"+trim(shared,9000);
        return call(m,SYSTEM+"\n"+runtime(),prompt,650);
    }

    private String finalizeTeam(JSONObject coordinatorModel,String task,JSONObject plan,String shared)throws Exception{
        String prompt="You are the temporary coordinator selected by the team for THIS task. Using the team plan and all shared findings, produce ONE concise, practical final answer. Merge agreements, remove duplicates, mention uncertainty only when material, and do not expose internal role chatter unless useful. Answer in the user's language/style.\n\nTASK:\n"+task+"\n\nPLAN:\n"+plan.toString()+"\n\nTEAM FINDINGS:\n"+trim(shared,12000);
        return call(coordinatorModel,SYSTEM+"\n"+runtime(),prompt,900);
    }

    private void showAllocation(JSONObject plan,JSONArray team,int coordinator){runOnUiThread(()->{event("Team decision","Temporary coordinator: M"+(coordinator+1)+"\nThe team chose these responsibilities for this task:",ACCENT2); for(int i=0;i<4;i++){String model=team.optJSONObject(i)==null?"":team.optJSONObject(i).optString("model"); String resp=responsibility(plan,i); rows[i].setText("M"+(i+1)+" · "+resp+" · "+shortName(model)); rows[i].setTextColor(i==coordinator?ACCENT2:TEXT);}});}

    private void stage(int slot,String state,String model){runOnUiThread(()->{rows[slot].setText("M"+(slot+1)+" · "+state+" · "+shortName(model)); rows[slot].setTextColor(AMBER); teamStatus.setText("TEAM · collaborating · "+state);});}

    private void refreshRows(){JSONArray a=getCouncil(); teamStatus.setText(a.length()==4?"TEAM · 4/4 VERIFIED · dynamic roles per task":"TEAM · "+a.length()+"/4 VERIFIED"); for(int i=0;i<4;i++){if(i<a.length()){JSONObject x=a.optJSONObject(i); rows[i].setText("M"+(i+1)+" · AVAILABLE · "+shortName(x==null?"":x.optString("model"))+" · VERIFIED"); rows[i].setTextColor(TEXT);}else{rows[i].setText("M"+(i+1)+" · NOT SET · tap to search/switch"); rows[i].setTextColor(MUTED);}}}

    private void ensureTeam(boolean force){if(busy||isKilled())return; if(!force&&getCouncil().length()==4){refreshRows();return;} String key=prefs.getSecret("api_key"); if(key.isEmpty()){settings();return;} busy=true; new Thread(()->{try{JSONArray c=loadCandidates(key,force); JSONArray locked=new JSONArray(); Set<String> used=new HashSet<>(); for(int i=0;i<c.length()&&locked.length()<4;i++){JSONObject x=c.getJSONObject(i);String model=x.optString("model");if(model.isEmpty()||used.contains(model))continue;int slot=locked.length();stage(slot,"PINGING",model);try{ping(key,x.optString("region"),model);used.add(model);locked.put(new JSONObject().put("model",model).put("region",x.optString("region")).put("candidate_index",i));}catch(Exception ignored){}} if(locked.length()<4)throw new Exception("Only "+locked.length()+" unique working models verified; 4 required"); prefs.putString(PREF_COUNCIL,locked.toString()); JSONObject first=locked.getJSONObject(0); prefs.putString("model",first.optString("model")); prefs.putString("region",first.optString("region")); runOnUiThread(this::refreshRows);}catch(Exception e){runOnUiThread(()->bubble("error","Team setup failed\n"+safe(e),false));}finally{busy=false;}}).start();}

    private void slotDialog(int slot){EditText q=new EditText(this);q.setHint("Search model: qwen / mistral / minimax…");q.setSingleLine(true); new AlertDialog.Builder(this).setTitle("M"+(slot+1)+" · Search / Switch").setMessage(currentSlot(slot)).setView(q).setNeutralButton("Auto Next",(d,w)->switchSlot(slot,"",true)).setNegativeButton("Close",null).setPositiveButton("Search & Switch",(d,w)->switchSlot(slot,q.getText().toString().trim(),false)).show();}
    private String currentSlot(int slot){JSONArray a=getCouncil(); if(slot<a.length()){JSONObject x=a.optJSONObject(slot);return "Current: "+x.optString("model")+"\nRegion: "+x.optString("region")+"\nRoles are NOT fixed. This model is simply a teammate.";}return "No model locked in this slot.";}

    private void switchSlot(int slot,String query,boolean autoNext){if(busy)return;String key=prefs.getSecret("api_key");if(key.isEmpty()){settings();return;}busy=true;new Thread(()->{try{JSONArray c=loadCandidates(key,false),team=getCouncil();Set<String> used=new HashSet<>();for(int i=0;i<team.length();i++)if(i!=slot)used.add(team.getJSONObject(i).optString("model"));String q=query.toLowerCase(Locale.US);int start=0;if(autoNext&&slot<team.length())start=team.getJSONObject(slot).optInt("candidate_index",-1)+1;JSONObject chosen=null;for(int pass=0;pass<2&&chosen==null;pass++){int from=pass==0?Math.max(0,start):0,to=pass==0?c.length():Math.max(0,start);for(int i=from;i<to;i++){JSONObject x=c.getJSONObject(i);String m=x.optString("model");if(m.isEmpty()||used.contains(m)||(!q.isEmpty()&&!m.toLowerCase(Locale.US).contains(q)))continue;try{ping(key,x.optString("region"),m);chosen=new JSONObject().put("model",m).put("region",x.optString("region")).put("candidate_index",i);break;}catch(Exception ignored){}}}if(chosen==null)throw new Exception("No working replacement found");while(team.length()<4)team.put(new JSONObject());team.put(slot,chosen);prefs.putString(PREF_COUNCIL,team.toString());if(slot==0){prefs.putString("model",chosen.optString("model"));prefs.putString("region",chosen.optString("region"));}runOnUiThread(this::refreshRows);}catch(Exception e){runOnUiThread(()->Toast.makeText(this,safe(e),Toast.LENGTH_LONG).show());}finally{busy=false;}}).start();}

    private void switchSingle(){JSONArray a=getCouncil();if(a.length()>1){JSONObject x=a.optJSONObject(1);prefs.putString("model",x.optString("model"));prefs.putString("region",x.optString("region"));singleStatus.setText(singleLabel());Toast.makeText(this,"Single chat switched",Toast.LENGTH_SHORT).show();}else ensureTeam(false);}

    private String callSingle()throws Exception{JSONArray h=getHistory(),msgs=new JSONArray();msgs.put(new JSONObject().put("role","system").put("content",SYSTEM+"\n"+runtime()+memoryContext()));int s=Math.max(0,h.length()-12);for(int i=s;i<h.length();i++){JSONObject x=h.optJSONObject(i);if(x!=null)msgs.put(new JSONObject().put("role",x.optString("role")).put("content",x.optString("text")));}JSONObject b=new JSONObject().put("model",prefs.getString("model","")).put("messages",msgs).put("temperature",.3).put("max_tokens",900);return extract(post(prefs.getSecret("api_key"),prefs.getString("region",""),b,120000));}
    private String call(JSONObject member,String sys,String user,int max)throws Exception{JSONArray msgs=new JSONArray().put(new JSONObject().put("role","system").put("content",sys)).put(new JSONObject().put("role","user").put("content",user));JSONObject b=new JSONObject().put("model",member.optString("model")).put("messages",msgs).put("temperature",.2).put("max_tokens",max);return extract(post(prefs.getSecret("api_key"),member.optString("region"),b,120000));}
    private String extract(JSONObject j)throws Exception{return j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","").trim();}

    private JSONArray loadCandidates(String key,boolean force)throws Exception{String raw=prefs.getString("candidates","");if(!force&&!raw.isEmpty())return new JSONArray(raw);JSONArray out=new JSONArray();Set<String>seen=new HashSet<>();for(String r:REGIONS){try{HttpURLConnection c=open("https://bedrock-mantle."+r+".api.aws/v1/models","GET",key,15000);int code=c.getResponseCode();if(code<200||code>=300)continue;JSONArray data=new JSONObject(read(c,code)).optJSONArray("data");if(data==null)continue;for(int i=0;i<data.length();i++){JSONObject x=data.optJSONObject(i);if(x==null)continue;String id=x.optString("id","");if(!id.isEmpty()&&seen.add(r+"|"+id))out.put(new JSONObject().put("region",r).put("model",id));}}catch(Exception ignored){}}if(out.length()==0)throw new Exception("No Bedrock models discovered");prefs.putString("candidates",out.toString());return out;}
    private void ping(String key,String region,String model)throws Exception{JSONArray m=new JSONArray().put(new JSONObject().put("role","user").put("content","Reply only OK"));JSONObject b=new JSONObject().put("model",model).put("messages",m).put("temperature",0).put("max_tokens",8);post(key,region,b,20000);}
    private JSONObject post(String key,String region,JSONObject body,int timeout)throws Exception{String ep="https://bedrock-mantle."+region+".api.aws/v1/chat/completions";HttpURLConnection c=open(ep,"POST",key,timeout);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();String raw=read(c,code);if(code<200||code>=300)throw new Exception("HTTP "+code+": "+trim(raw,360));return new JSONObject(raw);}
    private HttpURLConnection open(String ep,String method,String key,int timeout)throws Exception{if(isKilled())throw new SecurityException("Sandbox kill switch is ON");URL u=new URL(ep);String host=u.getHost();if(!"https".equalsIgnoreCase(u.getProtocol())||!host.startsWith("bedrock-mantle.")||!host.endsWith(".api.aws"))throw new SecurityException("Blocked endpoint: "+host);HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(timeout);c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Accept","application/json");return c;}
    private String read(HttpURLConnection c,int code)throws Exception{InputStream s=code>=200&&code<300?c.getInputStream():c.getErrorStream();if(s==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(s,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)b.append(l);}return b.toString();}

    private JSONArray getCouncil(){try{String r=prefs.getString(PREF_COUNCIL,"");return r.isEmpty()?new JSONArray():new JSONArray(r);}catch(Exception e){return new JSONArray();}}
    private String teamNames(JSONArray t){StringBuilder b=new StringBuilder();for(int i=0;i<t.length();i++){JSONObject x=t.optJSONObject(i);b.append("M").append(i+1).append(": ").append(x==null?"":x.optString("model")).append(" @ ").append(x==null?"":x.optString("region")).append("\n");}return b.toString();}
    private JSONObject parseJson(String s,JSONObject fallback){try{int a=s.indexOf('{'),b=s.lastIndexOf('}');if(a>=0&&b>a)return new JSONObject(s.substring(a,b+1));}catch(Exception ignored){}return fallback;}
    private String runtime(){Date n=new Date();SimpleDateFormat d=new SimpleDateFormat("EEEE, dd MMMM yyyy",Locale.US),tm=new SimpleDateFormat("HH:mm:ss",Locale.US);return "RUNTIME FACTS — AUTHORITATIVE\nCurrent local date: "+d.format(n)+"\nCurrent local time: "+tm.format(n)+"\nTimezone: "+TimeZone.getDefault().getID()+"\nLive web/news/market feed: NOT CONNECTED\nNever invent latest/current facts not supplied here.";}
    private String memoryContext(){String m=prefs.getString(PREF_MEMORY,"").trim();return m.isEmpty()?"":"\nLOCAL MEMORY:\n"+m;}

    private JSONArray getHistory(){try{String r=prefs.getString(PREF_HISTORY,"");return r.isEmpty()?new JSONArray():new JSONArray(r);}catch(Exception e){return new JSONArray();}}
    private void append(String role,String text){try{JSONArray old=getHistory(),out=new JSONArray();int s=Math.max(0,old.length()-59);for(int i=s;i<old.length();i++)out.put(old.getJSONObject(i));out.put(new JSONObject().put("role",role).put("text",text).put("ts",System.currentTimeMillis()));prefs.putString(PREF_HISTORY,out.toString());}catch(Exception ignored){}}
    private void loadHistory(){JSONArray a=getHistory();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)bubble(x.optString("role","assistant"),x.optString("text",""),false);}}
    private void updateMemory(String user,String answer){String key=prefs.getSecret("api_key"),model=prefs.getString("model",""),region=prefs.getString("region","");if(key.isEmpty()||model.isEmpty())return;new Thread(()->{try{String old=prefs.getString(PREF_MEMORY,"");JSONArray msgs=new JSONArray().put(new JSONObject().put("role","system").put("content","Keep a compact long-term memory of durable user preferences, projects, decisions and recurring instructions. Ignore transient questions and uncertain claims. Return concise bullets only.")).put(new JSONObject().put("role","user").put("content","EXISTING:\n"+old+"\n\nLATEST:\nUser: "+user+"\nAssistant: "+answer));JSONObject b=new JSONObject().put("model",model).put("messages",msgs).put("temperature",0).put("max_tokens",400);String m=extract(post(key,region,b,60000));if(!m.isEmpty())prefs.putString(PREF_MEMORY,m);}catch(Exception ignored){}}).start();}

    private void historyDialog(){new AlertDialog.Builder(this).setTitle("History & Memory").setMessage("Persistent messages: "+getHistory().length()+"\nMemory: "+(prefs.getString(PREF_MEMORY,"").isEmpty()?"empty":"active")).setNeutralButton("Memory",(d,w)->new AlertDialog.Builder(this).setTitle("Local Memory").setMessage(prefs.getString(PREF_MEMORY,"No memory yet.")).setPositiveButton("Close",null).show()).setNegativeButton("Close",null).setPositiveButton("New Chat",(d,w)->{prefs.putString(PREF_HISTORY,"");chat.removeAllViews();}).show();}
    private void sandboxDialog(){new AlertDialog.Builder(this).setTitle("Sandbox SAFE").setMessage("Bedrock-only network\nLocal history + memory\nCurrent date/time injected\nLive web/news/market feed NOT connected\n\nKill switch: "+(isKilled()?"ON":"OFF")).setNegativeButton("Close",null).setPositiveButton(isKilled()?"Enable AI":"KILL SWITCH",(d,w)->prefs.putString(PREF_KILL,isKilled()?"0":"1")).show();}
    private void settings(){EditText k=new EditText(this);k.setHint("Bedrock API key");k.setText(prefs.getSecret("api_key"));k.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);new AlertDialog.Builder(this).setTitle("Bedrock Connection").setView(k).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{String v=k.getText().toString().trim();if(!v.isEmpty()){prefs.putSecret("api_key",v);prefs.putString(PREF_COUNCIL,"");prefs.putString("candidates","");ensureTeam(true);}}).show();}

    private boolean isKilled(){return"1".equals(prefs.getString(PREF_KILL,"0"));}
    private String singleLabel(){String m=prefs.getString("model","");return m.isEmpty()?"Single chat · no model yet":shortName(m)+" · "+prefs.getString("region","");}
    private String shortName(String m){return m.length()>34?m.substring(0,34)+"…":m;}
    private String trim(String s,int n){if(s==null)return"";return s.length()<=n?s:s.substring(0,n);}
    private String safe(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private void audit(String a,String d){String ts=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());prefs.putString(PREF_AUDIT,ts+" | "+a+" | "+d.replace('\n',' ')+"\n"+prefs.getString(PREF_AUDIT,""));}

    private void event(String title,String detail,int c){TextView v=t(title+"\n"+detail,12.5f,TEXT,false);v.setPadding(dp(13),dp(9),dp(13),dp(9));GradientDrawable g=round(Color.rgb(15,19,27),dp(15));g.setStroke(dp(1),c);v.setBackground(g);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(0,dp(4),0,dp(4));chat.addView(v,p);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
    private void bubble(String role,String text,boolean persist){if(persist)append(role,text);TextView v=t(text,15,role.equals("error")?Color.rgb(255,191,191):TEXT,false);v.setPadding(dp(15),dp(11),dp(15),dp(11));int width=role.equals("user")?dp(300):ViewGroup.LayoutParams.MATCH_PARENT;v.setBackground(role.equals("user")?grad(dp(18),Color.rgb(83,69,205),Color.rgb(41,116,204)):round(role.equals("error")?Color.rgb(54,25,30):PANEL,dp(18)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(role.equals("user")?dp(42):0,dp(6),0,dp(6));p.gravity=role.equals("user")?Gravity.END:Gravity.START;chat.addView(v,p);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
    private TextView t(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(r);return g;}
    private GradientDrawable grad(int r,int a,int b){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(r);return g;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}