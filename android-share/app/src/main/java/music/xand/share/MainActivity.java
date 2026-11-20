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
 void add(String text){if(text==null)return; Matcher m=Pattern.compile("https?://[^\\s]+").matcher(text); if(!m.find())return; String url=m.group().replaceAll("[),.;]+$",""); if(!url.contains("tiktok.com"))return; JSONArray a=load(); for(int i=0;i<a.length();i++)if(url.equals(a.optString(i)))return; a.put(url);save(a);}
 public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("xand_queue",MODE_PRIVATE);if(Intent.ACTION_SEND.equals(getIntent().getAction()))add(getIntent().getStringExtra(Intent.EXTRA_TEXT));show();}
 void show(){root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,24,24,24);ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);TextView title=new TextView(this);title.setText("XAND MUSIC — Fila de links");title.setTextSize(22);root.addView(title);JSONArray a=load();TextView count=new TextView(this);count.setText("Pendentes: "+a.length());root.addView(count);for(int i=0;i<a.length();i++){final int idx=i;String url=a.optString(i);TextView item=new TextView(this);item.setText((i+1)+". "+url);item.setTextIsSelectable(true);root.addView(item);Button copy=new Button(this);copy.setText("Copiar link");copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("TikTok",url));});root.addView(copy);Button done=new Button(this);done.setText("Marcar como concluído");done.setOnClickListener(v->{JSONArray next=new JSONArray();JSONArray old=load();for(int j=0;j<old.length();j++)if(j!=idx)next.put(old.optString(j));save(next);show();});root.addView(done);}}
}
