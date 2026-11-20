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
 void add(String text){if(text==null)return; Matcher m=Pattern.compile("https?://[^\\s]+").matcher(text); if(!m.find())return; String url=m.group().replaceAll("[),.;]+$",""); if(!url.contains("tiktok.com"))return; JSONArray a=load(); for(int i=0;i<a.length();i++)if(url.equals(a.optString(i))){Toast.makeText(this,"Essa música já está na fila.",Toast.LENGTH_LONG).show();return;} JSONArray history=resolved();for(int i=0;i<history.length();i++)if(url.equals(history.optString(i))){Toast.makeText(this,"Link já utilizado. Consulte o histórico.",Toast.LENGTH_LONG).show();return;}a.put(url);save(a);runShareFlows(url);}
 public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("xand_queue",MODE_PRIVATE);if(Intent.ACTION_SEND.equals(getIntent().getAction()))add(getIntent().getStringExtra(Intent.EXTRA_TEXT));show();}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(Intent.ACTION_SEND.equals(intent.getAction()))add(intent.getStringExtra(Intent.EXTRA_TEXT));show();}
 JSONObject metadata(){try{return new JSONObject(prefs.getString("metadata","{}"));}catch(Exception e){return new JSONObject();}}
 JSONObject info(String url){JSONObject x=metadata().optJSONObject(url);return x==null?new JSONObject():x;}
 String field(JSONObject x,String key,String fallback){String s=x.optString(key,"").trim();return s.isEmpty()?fallback:s;}
 void edit(String url){JSONObject x=info(url);LinearLayout fields=new LinearLayout(this);fields.setOrientation(1);fields.setPadding(32,16,32,0);EditText song=new EditText(this);song.setHint("Nome da música");song.setSingleLine(true);song.setText(x.optString("song"));fields.addView(song);EditText artist=new EditText(this);artist.setHint("Artista");artist.setSingleLine(true);artist.setText(x.optString("artist"));fields.addView(artist);EditText genre=new EditText(this);genre.setHint("Estilo musical (opcional)");genre.setSingleLine(true);genre.setText(x.optString("genre"));fields.addView(genre);new AlertDialog.Builder(this).setTitle("Dados da música").setView(fields).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->{try{JSONObject all=metadata();JSONObject info=new JSONObject();info.put("song",song.getText().toString().trim());info.put("artist",artist.getText().toString().trim());info.put("genre",genre.getText().toString().trim());all.put(url,info);prefs.edit().putString("metadata",all.toString()).apply();show();}catch(Exception e){Toast.makeText(this,"Erro ao salvar",Toast.LENGTH_SHORT).show();}}).show();}
 void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"Não foi possível abrir o link",Toast.LENGTH_SHORT).show();}}
 void share(String url){JSONObject x=info(url);String text=field(x,"song","Música não identificada")+" — "+field(x,"artist","Artista não identificado")+"\n"+field(x,"genre","")+"\n"+url;Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(send,"Compartilhar música"));}
 
 
 
 void runShareFlows(String url){
  try{
   JSONObject saved=new JSONObject(prefs.getString("workflows_v1","{}"));
   JSONArray flows=saved.optJSONArray("flows");if(flows==null)return;
   for(int i=0;i<flows.length();i++){
    JSONObject flow=flows.optJSONObject(i);if(flow==null||!flow.optBoolean("enabled",false))continue;
    JSONArray nodes=flow.optJSONArray("nodes"),edges=flow.optJSONArray("edges");if(nodes==null||edges==null)continue;
    java.util.HashMap<String,String> types=new java.util.HashMap<>();
    java.util.ArrayDeque<String> todo=new java.util.ArrayDeque<>();
    java.util.HashSet<String> visited=new java.util.HashSet<>();
    for(int j=0;j<nodes.length();j++){JSONObject n=nodes.optJSONObject(j);if(n==null)continue;String id=n.optString("id");String type=n.optString("type");if(id.isEmpty())continue;types.put(id,type);if("trigger_share".equals(type))todo.add(id);}
    int limit=0;
    while(!todo.isEmpty()&&limit++<60){
     String current=todo.removeFirst();if(!visited.add(current))continue;
     String type=types.get(current);
     if("action_notify".equals(type))Toast.makeText(this,"XAND: link recebido pela automação.",Toast.LENGTH_SHORT).show();
     else if("action_copy".equals(type))((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("TikTok",url));
     else if("action_open".equals(type))open(url);
     // action_queue is idempotent: add() already saved the received link.
     for(int e=0;e<edges.length();e++){JSONObject edge=edges.optJSONObject(e);if(edge!=null&&current.equals(edge.optString("from"))){String dest=edge.optString("to");if(types.containsKey(dest)&&!visited.contains(dest))todo.addLast(dest);}}
    }
   }
  }catch(Exception ex){Toast.makeText(this,"Falha ao executar automação.",Toast.LENGTH_SHORT).show();}
 }

 void inspectTikTok(String url){
  Toast.makeText(this,"Consultando publicação pública...",Toast.LENGTH_SHORT).show();
  new Thread(()->{
   String canonical=url;String type="Link do TikTok";String player="";
   try{
    java.net.URL target=new java.net.URL(url);
    java.net.HttpURLConnection connection=(java.net.HttpURLConnection)target.openConnection();
    connection.setRequestMethod("HEAD");connection.setInstanceFollowRedirects(true);
    connection.setConnectTimeout(9000);connection.setReadTimeout(9000);
    connection.setRequestProperty("User-Agent","Mozilla/5.0");
    connection.getResponseCode();
    canonical=connection.getURL().toString();
    connection.disconnect();
   }catch(Exception ignored){}
   Uri parsed=Uri.parse(canonical);
   String host=parsed.getHost()==null?"":parsed.getHost().toLowerCase(java.util.Locale.ROOT);
   if(!(host.equals("tiktok.com")||host.endsWith(".tiktok.com"))){canonical=url;parsed=Uri.parse(url);}
   String path=parsed.getPath()==null?"":parsed.getPath();
   Matcher videoId=Pattern.compile("/video/([0-9]+)").matcher(path);
   if(videoId.find()){type="Vídeo";player="https://www.tiktok.com/player/v1/"+videoId.group(1)+"?music_info=1&description=1";}
   else if(path.contains("/photo/"))type="Publicação com fotos";
   String finalPlayer=player;String finalUrl=canonical;
   String message="Tipo: "+type+"\n\nO TikTok não informou o nome e o artista do áudio por esta API. O player oficial pode exibir informações do som para conferência. Não confunda o criador da publicação com o artista da música.";
   runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Consulta do TikTok").setMessage(message)
    .setNegativeButton("Voltar",null)
    .setNeutralButton("Informar música",(d,w)->edit(url))
    .setPositiveButton(finalPlayer.isEmpty()?"Abrir TikTok":"Ver som no player",(d,w)->open(finalPlayer.isEmpty()?finalUrl:finalPlayer)).show());
  }).start();
 }

 void copyText(String title,String value){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText(title,value));Toast.makeText(this,"Copiado.",Toast.LENGTH_SHORT).show();}
 void shareText(String title,String value){Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,value);startActivity(Intent.createChooser(send,title));}
 boolean needsMetadata(String url){JSONObject x=info(url);if(x.optString("song","").trim().isEmpty()||x.optString("artist","").trim().isEmpty()){Toast.makeText(this,"Preencha música e artista primeiro.",Toast.LENGTH_LONG).show();edit(url);return true;}return false;}
 String caption(String url){JSONObject x=info(url);String song=field(x,"song","Música não identificada"),artist=field(x,"artist","Artista não identificado"),genre=x.optString("genre","").trim();String result="🎵 "+song+" — "+artist;if(!genre.isEmpty())result+="\n🎧 Estilo: "+genre;return result+"\n\nQual sua parte favorita? 🎶\n\n#XANDMUSIC #TikTokMusic";}
 void prepareCaption(String url){if(needsMetadata(url))return;EditText input=new EditText(this);input.setText(caption(url));input.setMinLines(5);input.setGravity(android.view.Gravity.TOP);LinearLayout layout=new LinearLayout(this);layout.setPadding(28,12,28,0);layout.addView(input,new LinearLayout.LayoutParams(-1,-2));new AlertDialog.Builder(this).setTitle("Legenda para o TikTok").setView(layout).setNegativeButton("Voltar",null).setNeutralButton("Copiar",(d,w)->copyText("Legenda XAND",input.getText().toString())).setPositiveButton("Compartilhar",(d,w)->shareText("Enviar legenda",input.getText().toString())).show();}
 void pinterest(String url){if(needsMetadata(url))return;JSONObject x=info(url);String query=field(x,"song","")+" "+field(x,"artist","")+" "+field(x,"genre","")+" music cover art";open("https://www.pinterest.com/search/pins/?q="+Uri.encode(query.trim()));}
 void prepareArtwork(String url){if(needsMetadata(url))return;JSONObject x=info(url);String song=field(x,"song",""),artist=field(x,"artist",""),genre=field(x,"genre","estilo ainda não confirmado");String prompt="Crie uma arte vertical 9:16 para o perfil XAND MUSIC. Música: "+song+". Artista: "+artist+". Estilo: "+genre+". A imagem deve se inspirar na atmosfera da música, nunca em capturas de tela do TikTok. Incluir nome da música, artista, estilo e meu logotipo oficial XAND MUSIC completo, sem cortar ou alterar. Se eu ainda não tiver enviado a logo, peça o arquivo original antes de finalizar. Evite imagens repetidas, excesso de efeitos artificiais e padrões genéricos. Respeite os direitos de uso de imagens de terceiros. Gere uma proposta visual original.";shareText("Enviar instruções ao ChatGPT",prompt);}

 boolean isSelected(String url){return prefs.getBoolean("selected_"+url,false);}
 void setSelected(String url,boolean selected){prefs.edit().putBoolean("selected_"+url,selected).apply();show();}
 int bg=Color.rgb(9,9,9),panel=Color.rgb(21,21,21),stroke=Color.rgb(48,48,48),fg=Color.rgb(245,245,245),muted=Color.rgb(166,166,166),accent=Color.rgb(255,133,0);
 GradientDrawable shape(int color,int radius,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);if(border!=0)d.setStroke(2,border);return d;}
 TextView label(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
 Button action(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(primary?bg:fg);b.setBackground(shape(primary?accent:Color.rgb(42,42,42),20,primary?0:stroke));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,104);lp.topMargin=10;b.setLayoutParams(lp);return b;}
 void show(){
 root=new LinearLayout(this);root.setOrientation(1);root.setPadding(34,44,34,44);root.setBackgroundColor(bg);
 ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
 TextView brand=label("XAND  MUSIC",26,fg,true);root.addView(brand);TextView brandNote=label("●  MUSIC ANALYTICS  /  PRETO + LARANJA",11,Color.rgb(255,171,78),true);brandNote.setPadding(0,4,0,8);root.addView(brandNote);
 TextView kicker=label("BIBLIOTECA MUSICAL  •  TIKTOK",11,accent,true);kicker.setPadding(0,8,0,30);root.addView(kicker);
 Button workflows=action("◈ Criar automações • nós e conexões",true);workflows.setOnClickListener(v->startActivity(new Intent(this,WorkflowActivity.class)));root.addView(workflows);
 JSONArray a=load(),history=resolved();
 TextView count=label("Fila de músicas",23,fg,true);root.addView(count);
 TextView sub=label(a.length()+" pendente(s)   •   "+history.length()+" resolvido(s)",13,muted,false);sub.setPadding(0,6,0,25);root.addView(sub);
 if(a.length()==0){TextView empty=label("Nenhuma música pendente. Compartilhe um link do TikTok para adicionar.",14,muted,false);empty.setPadding(18,24,18,24);empty.setBackground(shape(panel,22,stroke));root.addView(empty);}
 for(int i=0;i<a.length();i++){
 final int idx=i;String url=a.optString(i);JSONObject x=info(url);
 LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(24,24,24,24);card.setBackground(shape(panel,26,stroke));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=20;root.addView(card,cp);
 TextView number=label((i==0?"PRÓXIMA DA FILA  •  ":"FAIXA "+(i+1)+"  •  ")+"TIKTOK",11,accent,true);card.addView(number);
 TextView song=label(field(x,"song","Música não identificada"),19,fg,true);song.setPadding(0,12,0,3);card.addView(song);
 TextView artist=label(field(x,"artist","Artista não identificado"),14,muted,false);card.addView(artist);
 TextView genre=label("ESTILO  "+field(x,"genre","Não informado"),12,Color.rgb(255,171,78),false);genre.setPadding(0,12,0,10);card.addView(genre);
 TextView link=label(url,11,muted,false);link.setTextIsSelectable(true);card.addView(link);
 CheckBox choose=new CheckBox(this);choose.setText("Selecionar esta música");choose.setTextColor(fg);choose.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));choose.setChecked(isSelected(url));choose.setPadding(0,16,0,6);card.addView(choose);
 LinearLayout actions=new LinearLayout(this);actions.setOrientation(1);actions.setVisibility(isSelected(url)?View.VISIBLE:View.GONE);card.addView(actions);
 choose.setOnCheckedChangeListener((button,checked)->{prefs.edit().putBoolean("selected_"+url,checked).apply();actions.setVisibility(checked?View.VISIBLE:View.GONE);});
 Button edit=action("Editar música, artista e estilo",true);edit.setOnClickListener(v->edit(url));actions.addView(edit);
 Button open=action("Abrir no TikTok",false);open.setOnClickListener(v->open(url));actions.addView(open);
 Button inspect=action("Consultar áudio e publicação",false);inspect.setOnClickListener(v->inspectTikTok(url));actions.addView(inspect);

 Button searchPin=action("Buscar arte no Pinterest",false);searchPin.setOnClickListener(v->pinterest(url));actions.addView(searchPin);
 Button artwork=action("Preparar arte com ChatGPT",false);artwork.setOnClickListener(v->prepareArtwork(url));actions.addView(artwork);
 Button captionBtn=action("Gerar legenda para TikTok",true);captionBtn.setOnClickListener(v->prepareCaption(url));actions.addView(captionBtn);

 Button share=action("Compartilhar / enviar para processamento",false);share.setOnClickListener(v->share(url));actions.addView(share);
 Button copy=action("Copiar link",false);copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("TikTok",url));});actions.addView(copy);
 CheckBox done=new CheckBox(this);done.setText("Resolvido");done.setTextColor(muted);done.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));done.setPadding(0,12,0,0);done.setOnCheckedChangeListener((button,checked)->{if(checked)markResolved(idx);});card.addView(done);
 }
 TextView historyTitle=label("Histórico de resolvidos",19,fg,true);historyTitle.setPadding(0,22,0,16);root.addView(historyTitle);
 for(int j=0;j<history.length();j++){String url=history.optString(j);JSONObject x=info(url);TextView item=label("✓  "+field(x,"song","Música não identificada")+" — "+field(x,"artist","Artista não identificado")+"\n"+url,12,muted,false);item.setPadding(20,18,20,18);item.setBackground(shape(panel,18,stroke));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=10;root.addView(item,hp);}
 }
}
