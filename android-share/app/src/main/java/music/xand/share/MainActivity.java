package music.xand.share;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.*;
import org.json.*;
import java.util.regex.*;

public class MainActivity extends Activity {
 SharedPreferences prefs; LinearLayout root;
 JSONArray load(){try{return new JSONArray(prefs.getString("queue","[]"));}catch(Exception e){return new JSONArray();}}
 void save(JSONArray a){prefs.edit().putString("queue",a.toString()).apply();}
 JSONArray resolved(){try{return new JSONArray(prefs.getString("resolved","[]"));}catch(Exception e){return new JSONArray();}}
 void markResolved(int idx){JSONArray old=load(),next=new JSONArray(),history=resolved();for(int j=0;j<old.length();j++){String link=old.optString(j);if(j==idx)history.put(link);else next.put(link);}prefs.edit().putString("queue",next.toString()).putString("resolved",history.toString()).apply();show();}
 void add(String text){if(text==null)return; Matcher m=Pattern.compile("https?://[^\\s]+").matcher(text); if(!m.find())return; String url=m.group().replaceAll("[),.;]+$",""); if(!url.contains("tiktok.com"))return; JSONArray a=load(); for(int i=0;i<a.length();i++)if(url.equals(a.optString(i)))return; a.put(url);save(a);}
 public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("xand_queue",MODE_PRIVATE);if(Intent.ACTION_SEND.equals(getIntent().getAction()))add(getIntent().getStringExtra(Intent.EXTRA_TEXT));show();}
 void show(){root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,24,24,24);ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);TextView title=new TextView(this);title.setText("XAND MUSIC — Fila de links");title.setTextSize(22);root.addView(title);JSONArray a=load();TextView count=new TextView(this);count.setText("Pendentes: "+a.length());root.addView(count);for(int i=0;i<a.length();i++){final int idx=i;String url=a.optString(i);TextView item=new TextView(this);item.setText((i+1)+". "+url);item.setTextIsSelectable(true);root.addView(item);Button copy=new Button(this);copy.setText("Copiar link");copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("TikTok",url));});root.addView(copy);CheckBox done=new CheckBox(this);done.setText("Resolvido");done.setChecked(false);done.setOnCheckedChangeListener((button,checked)->{if(checked)markResolved(idx);});root.addView(done);}JSONArray history=resolved();TextView historyTitle=new TextView(this);historyTitle.setText("Resolvidos: "+history.length());historyTitle.setTextSize(18);root.addView(historyTitle);for(int j=0;j<history.length();j++){TextView item=new TextView(this);item.setText("✓ "+history.optString(j));root.addView(item);}}
}
