package music.xand.share;
import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
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
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(Intent.ACTION_SEND.equals(intent.getAction()))add(intent.getStringExtra(Intent.EXTRA_TEXT));show();}
 JSONObject metadata(){try{return new JSONObject(prefs.getString("metadata","{}"));}catch(Exception e){return new JSONObject();}}
 JSONObject info(String url){JSONObject x=metadata().optJSONObject(url);return x==null?new JSONObject():x;}
 String field(JSONObject x,String key,String fallback){String s=x.optString(key,"").trim();return s.isEmpty()?fallback:s;}
 void edit(String url){JSONObject x=info(url);LinearLayout fields=new LinearLayout(this);fields.setOrientation(1);fields.setPadding(32,16,32,0);EditText song=new EditText(this);song.setHint("Nome da música");song.setSingleLine(true);song.setText(x.optString("song"));fields.addView(song);EditText artist=new EditText(this);artist.setHint("Artista");artist.setSingleLine(true);artist.setText(x.optString("artist"));fields.addView(artist);EditText genre=new EditText(this);genre.setHint("Estilo musical (opcional)");genre.setSingleLine(true);genre.setText(x.optString("genre"));fields.addView(genre);new AlertDialog.Builder(this).setTitle("Dados da música").setView(fields).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->{try{JSONObject all=metadata();JSONObject info=new JSONObject();info.put("song",song.getText().toString().trim());info.put("artist",artist.getText().toString().trim());info.put("genre",genre.getText().toString().trim());all.put(url,info);prefs.edit().putString("metadata",all.toString()).apply();show();}catch(Exception e){Toast.makeText(this,"Erro ao salvar",Toast.LENGTH_SHORT).show();}}).show();}
 void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"Não foi possível abrir o link",Toast.LENGTH_SHORT).show();}}
 void share(String url){JSONObject x=info(url);String text=field(x,"song","Música não identificada")+" — "+field(x,"artist","Artista não identificado")+"\n"+field(x,"genre","")+"\n"+url;Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(send,"Compartilhar música"));}
 boolean isSelected(String url){return prefs.getBoolean("selected_"+url,false);}
 void setSelected(String url,boolean selected){prefs.edit().putBoolean("selected_"+url,selected).apply();show();}
 int bg=Color.rgb(8,13,22),panel=Color.rgb(17,27,40),stroke=Color.rgb(41,53,69),fg=Color.rgb(242,247,250),muted=Color.rgb(151,169,184),accent=Color.rgb(204,251,104);
 GradientDrawable shape(int color,int radius,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);if(border!=0)d.setStroke(2,border);return d;}
 TextView label(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
 Button action(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(primary?bg:fg);b.setBackground(shape(primary?accent:Color.rgb(27,42,55),20,primary?0:stroke));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,104);lp.topMargin=10;b.setLayoutParams(lp);return b;}
 void show(){
 root=new LinearLayout(this);root.setOrientation(1);root.setPadding(34,44,34,44);root.setBackgroundColor(bg);
 ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
 TextView brand=label("XAND  /  MUSIC",26,fg,true);root.addView(brand);
 TextView kicker=label("BIBLIOTECA MUSICAL  •  TIKTOK",11,accent,true);kicker.setPadding(0,8,0,30);root.addView(kicker);
 JSONArray a=load(),history=resolved();
 TextView count=label("Fila de músicas",23,fg,true);root.addView(count);
 TextView sub=label(a.length()+" pendente(s)   •   "+history.length()+" resolvido(s)",13,muted,false);sub.setPadding(0,6,0,25);root.addView(sub);
 if(a.length()==0){TextView empty=label("Nenhuma música pendente. Compartilhe um link do TikTok para adicionar.",14,muted,false);empty.setPadding(18,24,18,24);empty.setBackground(shape(panel,22,stroke));root.addView(empty);}
 for(int i=0;i<a.length();i++){
 final int idx=i;String url=a.optString(i);JSONObject x=info(url);
 LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(24,24,24,24);card.setBackground(shape(panel,26,stroke));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=20;root.addView(card,cp);
 TextView number=label("FAIXA "+(i+1)+"  •  TIKTOK",11,accent,true);card.addView(number);
 TextView song=label(field(x,"song","Música não identificada"),19,fg,true);song.setPadding(0,12,0,3);card.addView(song);
 TextView artist=label(field(x,"artist","Artista não identificado"),14,muted,false);card.addView(artist);
 TextView genre=label("ESTILO  "+field(x,"genre","Não informado"),12,accent,false);genre.setPadding(0,12,0,10);card.addView(genre);
 TextView link=label(url,11,muted,false);link.setTextIsSelectable(true);card.addView(link);
 CheckBox choose=new CheckBox(this);choose.setText("Selecionar esta música");choose.setTextColor(fg);choose.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));choose.setChecked(isSelected(url));choose.setPadding(0,16,0,6);card.addView(choose);
 LinearLayout actions=new LinearLayout(this);actions.setOrientation(1);actions.setVisibility(isSelected(url)?View.VISIBLE:View.GONE);card.addView(actions);
 choose.setOnCheckedChangeListener((button,checked)->{prefs.edit().putBoolean("selected_"+url,checked).apply();actions.setVisibility(checked?View.VISIBLE:View.GONE);});
 Button edit=action("Editar música, artista e estilo",true);edit.setOnClickListener(v->edit(url));actions.addView(edit);
 Button open=action("Abrir no TikTok",false);open.setOnClickListener(v->open(url));actions.addView(open);
 Button share=action("Compartilhar / enviar para processamento",false);share.setOnClickListener(v->share(url));actions.addView(share);
 Button copy=action("Copiar link",false);copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("TikTok",url));});actions.addView(copy);
 CheckBox done=new CheckBox(this);done.setText("Resolvido");done.setTextColor(muted);done.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));done.setPadding(0,12,0,0);done.setOnCheckedChangeListener((button,checked)->{if(checked)markResolved(idx);});card.addView(done);
 }
 TextView historyTitle=label("Histórico de resolvidos",19,fg,true);historyTitle.setPadding(0,22,0,16);root.addView(historyTitle);
 for(int j=0;j<history.length();j++){String url=history.optString(j);JSONObject x=info(url);TextView item=label("✓  "+field(x,"song","Música não identificada")+" — "+field(x,"artist","Artista não identificado")+"\n"+url,12,muted,false);item.setPadding(20,18,20,18);item.setBackground(shape(panel,18,stroke));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=10;root.addView(item,hp);}
 }
}
