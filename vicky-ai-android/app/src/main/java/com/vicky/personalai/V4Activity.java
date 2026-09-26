package com.vicky.personalai;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class V4Activity extends Activity {
    private SecurePrefs prefs;
    private LinearLayout body;
    private TextView sandboxLabel;
    private final int BG=Color.rgb(7,9,13), PANEL=Color.rgb(18,21,28), PANEL2=Color.rgb(27,31,40);
    private final int TEXT=Color.rgb(242,245,249), MUTED=Color.rgb(145,154,168), ACCENT=Color.rgb(110,92,255), ACCENT2=Color.rgb(0,197,255);
    private final int SAFE=Color.rgb(57,194,123), DANGER=Color.rgb(235,84,84), WARN=Color.rgb(240,172,64);

    @Override protected void onCreate(Bundle b){super.onCreate(b); prefs=new SecurePrefs(this); build();}
    @Override protected void onResume(){super.onResume(); if(body!=null)showOverview(); refreshSandboxLabel();}

    private void build(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(14),dp(8),dp(14),dp(10)); root.setBackgroundColor(BG);
        root.addView(hero()); root.addView(sandboxBar()); root.addView(nav());
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); root.addView(body,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root); showOverview();
    }

    private View hero(){
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(4),dp(2),dp(2),dp(8));
        ImageView avatar=new ImageView(this); avatar.setScaleType(ImageView.ScaleType.CENTER_CROP); byte[] raw=Base64.decode(AvatarAsset.JPEG_BASE64,Base64.DEFAULT); avatar.setImageBitmap(BitmapFactory.decodeByteArray(raw,0,raw.length)); avatar.setBackground(roundGradient(dp(28),ACCENT,ACCENT2)); avatar.setClipToOutline(true); row.addView(avatar,new LinearLayout.LayoutParams(dp(104),dp(104)));
        LinearLayout titles=new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL); titles.setPadding(dp(14),0,dp(8),0); titles.addView(txt("Vicky AI",26,TEXT,true)); titles.addView(txt("Your Private AI Assistant",12,MUTED,false)); TextView three=txt("REALISTIC 3D AVATAR",9,ACCENT2,true); three.setPadding(0,dp(5),0,0); titles.addView(three); row.addView(titles,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView gear=txt("⚙",19,TEXT,false); gear.setGravity(Gravity.CENTER); gear.setBackground(round(PANEL,dp(20))); gear.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class))); row.addView(gear,new LinearLayout.LayoutParams(dp(42),dp(42))); return row;
    }

    private View sandboxBar(){
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(13),dp(11),dp(13),dp(11)); bar.setBackground(round(Color.rgb(10,37,43),dp(17)));
        TextView icon=txt("◈",26,SAFE,true); icon.setGravity(Gravity.CENTER); bar.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout t=new LinearLayout(this); t.setOrientation(LinearLayout.VERTICAL); sandboxLabel=txt("",13,SAFE,true); t.addView(sandboxLabel); t.addView(txt("Tap to open secure sandbox window",10,MUTED,false)); bar.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); bar.addView(txt("›",26,TEXT,false)); bar.setOnClickListener(v->sandboxDialog());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,0,0,dp(9)); bar.setLayoutParams(lp); refreshSandboxLabel(); return bar;
    }

    private View nav(){
        LinearLayout n=new LinearLayout(this); n.setPadding(dp(4),dp(4),dp(4),dp(4)); n.setBackground(round(PANEL,dp(22)));
        TextView overview=navBtn("Overview"), chat=navBtn("Chat"), monitor=navBtn("Monitor"), audit=navBtn("Audit");
        n.addView(overview,lp1()); n.addView(chat,lp1()); n.addView(monitor,lp1()); n.addView(audit,lp1());
        overview.setOnClickListener(v->showOverview()); chat.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class))); monitor.setOnClickListener(v->showMonitor()); audit.setOnClickListener(v->showAudit()); return n;
    }

    private TextView navBtn(String s){TextView v=txt(s,11.5f,TEXT,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(5),dp(10),dp(5),dp(10));return v;}
    private LinearLayout.LayoutParams lp1(){return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);}

    private void showOverview(){
        body.removeAllViews(); TextView title=txt("Sandbox Overview",21,TEXT,true); title.setPadding(0,dp(13),0,dp(10)); body.addView(title);
        body.addView(infoCard("STATUS",isKilled()?"BLOCKED":"SAFE",isKilled()?DANGER:SAFE)); body.addView(infoCard("NETWORK","Bedrock Only",ACCENT2)); body.addView(infoCard("DEVICE ACCESS","Blocked",DANGER)); body.addView(infoCard("EXTERNAL WRITES","Approval Required",WARN)); body.addView(infoCard("MEMORY","Local Only",ACCENT));
        String model=prefs.getString("model",""); String region=prefs.getString("region",""); body.addView(modelCard(model.isEmpty()?"No verified text model yet":model,region.isEmpty()?"Auto-detect":region));
        TextView open=txt("Open Chat",14,Color.WHITE,true); open.setGravity(Gravity.CENTER); open.setPadding(dp(12),dp(13),dp(12),dp(13)); open.setBackground(roundGradient(dp(16),ACCENT,ACCENT2)); open.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class))); LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); op.setMargins(0,dp(12),0,0); body.addView(open,op);
    }

    private View infoCard(String a,String b,int c){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(14),dp(12),dp(14),dp(12));r.setBackground(round(PANEL,dp(15)));TextView l=txt(a,11,MUTED,true);TextView v=txt(b,12,c,true);r.addView(l,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));r.addView(v);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(0,0,0,dp(7));r.setLayoutParams(p);return r;}
    private View modelCard(String model,String region){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(12),dp(14),dp(12));c.setBackground(round(Color.rgb(14,20,34),dp(16)));c.addView(txt("TEXT MODEL  •  VERIFIED WHEN GREEN",10,SAFE,true));c.addView(txt(model,13,TEXT,true));c.addView(txt("Region: "+region,10,MUTED,false));return c;}

    private void showMonitor(){
        body.removeAllViews(); body.addView(sectionTitle("⌁  Live Monitor","Real-time sandbox activity")); String log=prefs.getString("sandbox_audit",""); if(log.isEmpty())log="No sandbox events yet."; String[] ls=log.split("\\n"); StringBuilder b=new StringBuilder(); for(int i=0;i<Math.min(ls.length,20);i++){String line=ls[i];String tag=line.contains("BLOCK")||line.contains("FAILED")?"  [BLOCKED/ERROR]":line.contains("SUCCESS")||line.contains("VERIFIED")?"  [SUCCESS]":"  [ALLOWED]";b.append(line).append(tag).append("\n\n");}
        TextView feed=txt(b.toString(),11.5f,TEXT,false);feed.setLineSpacing(dp(3),1f);feed.setPadding(dp(12),dp(12),dp(12),dp(12));feed.setBackground(round(PANEL,dp(16)));ScrollView s=new ScrollView(this);s.addView(feed);body.addView(s,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
    }

    private void showAudit(){
        body.removeAllViews(); body.addView(sectionTitle("▤  Audit Log","Complete history of sandbox activities")); LinearLayout stats=new LinearLayout(this);stats.setPadding(0,dp(10),0,dp(8));stats.addView(stat("Allowed",SAFE),lp1());stats.addView(stat("Blocked",DANGER),lp1());stats.addView(stat("Errors",WARN),lp1());body.addView(stats);
        TextView log=txt(prefs.getString("sandbox_audit","No audit events yet."),11.5f,TEXT,false);log.setTextIsSelectable(true);log.setLineSpacing(dp(3),1f);log.setPadding(dp(12),dp(12),dp(12),dp(12));log.setBackground(round(PANEL,dp(16)));ScrollView s=new ScrollView(this);s.addView(log);body.addView(s,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
    }
    private View sectionTitle(String a,String b){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(0,dp(13),0,dp(8));v.addView(txt(a,21,TEXT,true));v.addView(txt(b,11,MUTED,false));return v;}
    private TextView stat(String s,int c){TextView v=txt(s,11,c,true);v.setGravity(Gravity.CENTER);v.setBackground(round(PANEL2,dp(12)));v.setPadding(dp(4),dp(15),dp(4),dp(15));return v;}

    private void sandboxDialog(){
        Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(16),dp(18),dp(16));card.setBackground(round(Color.rgb(12,17,28),dp(24)));
        LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);h.addView(txt("◈",30,SAFE,true),new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout tt=new LinearLayout(this);tt.setOrientation(LinearLayout.VERTICAL);tt.addView(txt("Sandbox Window",20,TEXT,true));tt.addView(txt("Secure isolated environment",11,ACCENT2,true));h.addView(tt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView x=txt("×",25,MUTED,false);x.setOnClickListener(v->d.dismiss());h.addView(x);card.addView(h);
        TextView intro=txt("Run AI models and tools safely with isolation, audit visibility and restricted access.",12,MUTED,false);intro.setPadding(0,dp(8),0,dp(12));card.addView(intro);
        card.addView(control("Status",isKilled()?"BLOCKED":"SAFE",isKilled()?DANGER:SAFE));card.addView(control("Network","Bedrock Only",ACCENT2));card.addView(control("Data Isolation","ON",SAFE));card.addView(control("Device Access","Blocked",DANGER));card.addView(control("External Writes","Approval Required",WARN));
        TextView kill=txt(isKilled()?"Enable Restricted AI":"KILL SWITCH",13,Color.WHITE,true);kill.setGravity(Gravity.CENTER);kill.setPadding(dp(10),dp(13),dp(10),dp(13));kill.setBackground(round(isKilled()?SAFE:DANGER,dp(14)));kill.setOnClickListener(v->{prefs.putString("sandbox_kill",isKilled()?"0":"1");refreshSandboxLabel();d.dismiss();});LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);kp.setMargins(0,dp(10),0,dp(8));card.addView(kill,kp);
        LinearLayout actions=new LinearLayout(this);TextView m=navBtn("Monitor"),a=navBtn("Audit");m.setBackground(round(PANEL2,dp(13)));a.setBackground(round(PANEL2,dp(13)));m.setOnClickListener(v->{d.dismiss();showMonitor();});a.setOnClickListener(v->{d.dismiss();showAudit();});actions.addView(m,new LinearLayout.LayoutParams(0,dp(46),1));actions.addView(a,new LinearLayout.LayoutParams(0,dp(46),1));card.addView(actions);
        d.setContentView(card);d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setDimAmount(.68f);w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.91f),ViewGroup.LayoutParams.WRAP_CONTENT);} 
    }

    private View control(String a,String b,int c){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(12),dp(10),dp(12),dp(10));r.setBackground(round(PANEL,dp(13)));r.addView(txt(a,12,TEXT,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));r.addView(txt(b,11,c,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(0,0,0,dp(6));r.setLayoutParams(p);return r;}
    private boolean isKilled(){return "1".equals(prefs.getString("sandbox_kill","0"));}
    private void refreshSandboxLabel(){if(sandboxLabel!=null){sandboxLabel.setText(isKilled()?"SANDBOX KILLED":"SANDBOX SAFE");sandboxLabel.setTextColor(isKilled()?DANGER:SAFE);}}
    private TextView txt(String s,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private GradientDrawable roundGradient(int radius,int a,int b){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});d.setCornerRadius(radius);return d;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
