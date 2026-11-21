package music.xand.share;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;

/** Safe LAN hand-off to the existing XAND editor; never changes the editor/server. */
public final class EditorBridgeActivity extends Activity {
  static final String EDITOR_URL="http://192.168.0.6:1890/";
  static final int PICK_MP4=94;
  private SharedPreferences prefs;
  private String musicUrl;
  private TextView connection,status,details;
  private Button open;
  private Spinner style;
  private final Handler main=new Handler(Looper.getMainLooper());
  private boolean reachable=false;
  private final int ORANGE=Color.rgb(255,133,0);

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    prefs=getSharedPreferences("xand_queue",MODE_PRIVATE);
    musicUrl=getIntent().getStringExtra("music_url");
    if(musicUrl==null || musicUrl.isEmpty()){finish();return;}
    show();
    checkConnection();
  }
  private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
  private TextView text(String s,int size,int color){
    TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
    t.setPadding(0,dp(7),0,dp(7));return t;
  }
  private GradientDrawable shape(int fill){
    GradientDrawable d=new GradientDrawable();
    d.setColor(fill);d.setCornerRadius(dp(14));d.setStroke(dp(1),ORANGE);return d;
  }
  private Button button(String name){
    Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setTextColor(Color.WHITE);
    b.setMinHeight(dp(48));b.setBackground(shape(Color.rgb(42,42,42)));return b;
  }
  private JSONObject item(){
    try{return new JSONObject(prefs.getString("metadata","{}")).optJSONObject(musicUrl);}
    catch(Exception ignored){return null;}
  }
  private void updateMeta(String key,Object value){
    try{
      JSONObject all=new JSONObject(prefs.getString("metadata","{}"));
      JSONObject data=all.optJSONObject(musicUrl);if(data==null)data=new JSONObject();
      data.put(key,value);all.put(musicUrl,data);
      prefs.edit().putString("metadata",all.toString()).apply();
    }catch(Exception ignored){}
  }
  private void show(){
    ScrollView scroll=new ScrollView(this);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);
    root.setPadding(dp(18),dp(18),dp(18),dp(48));root.setBackgroundColor(Color.rgb(9,9,9));
    scroll.addView(root);setContentView(AutomationProgress.wrap(this,prefs,scroll));
    TextView title=text("XAND MUSIC  •  EDITOR DO MOTOROLA",21,Color.WHITE);
    title.setTypeface(null,1);root.addView(title);
    JSONObject data=item();
    String name=data==null?"Música pendente":data.optString("song","Música pendente");
    String artist=data==null?"":data.optString("artist","");
    root.addView(text(name+" — "+artist,16,ORANGE));
    root.addView(text("Servidor local: "+EDITOR_URL+"\nO editor existente será aberto no Chrome. Não vamos alterar suas configurações.",13,Color.LTGRAY));
    connection=text("Verificando acesso pela rede local...",14,ORANGE);root.addView(connection);
    TextView styleLabel=text("Composição desejada (escolha; não altera o editor automaticamente)",14,Color.WHITE);
    root.addView(styleLabel);
    style=new Spinner(this);
    String[] options={"Vídeo vertical 9:16","Foto + vídeo com degradê 9:16"};
    ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,options);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    style.setAdapter(adapter);style.setBackgroundColor(Color.rgb(36,36,36));root.addView(style);
    String selected=data==null?"":data.optString("editor_requested_style","");
    if("splitfade".equals(selected))style.setSelection(1);
    else if(selected.isEmpty()){
      String last=prefs.getString("editor_last_completed_style","");
      style.setSelection("video".equals(last)?1:0);
    }
    open=button("Abrir XAND MUSIC Editor no Chrome");open.setEnabled(false);
    LinearLayout.LayoutParams wide=new LinearLayout.LayoutParams(-1,dp(52));wide.topMargin=dp(12);root.addView(open,wide);
    open.setOnClickListener(v->openEditor());
    Button check=button("Testar conexão novamente");root.addView(check,new LinearLayout.LayoutParams(-1,dp(52)));
    check.setOnClickListener(v->checkConnection());
    Button pick=button("Registrar MP4 baixado no Motorola");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));
    p.topMargin=dp(18);root.addView(pick,p);
    pick.setOnClickListener(v->selectMp4());
    status=text("A exportação só será concluída aqui após validar o MP4 baixado.",14,Color.LTGRAY);root.addView(status);
    details=text("TikTok: som original 0% e música adicionada 100% — ajuste pendente dentro do TikTok. Sem publicação automática.",13,ORANGE);
    root.addView(details);
    if(data!=null&&!data.optString("editor_video_uri","").isEmpty()){
      String done="MP4 registrado: "+data.optString("editor_video_name","arquivo")
       +" · "+(data.optLong("editor_video_duration_ms",0)/1000)+" s";
      status.setText(done);
    }
    Button back=button("Voltar à fila");root.addView(back,new LinearLayout.LayoutParams(-1,dp(48)));
    back.setOnClickListener(v->finish());
  }
  private void checkConnection(){
    connection.setText("Consultando servidor local...");open.setEnabled(false);reachable=false;
    new Thread(()->{
      HttpURLConnection connectionHttp=null;boolean ok=false;
      try{
        connectionHttp=(HttpURLConnection)new URL(EDITOR_URL).openConnection();
        connectionHttp.setRequestMethod("GET");connectionHttp.setConnectTimeout(3500);
        connectionHttp.setReadTimeout(3500);connectionHttp.setInstanceFollowRedirects(false);
        ok=connectionHttp.getResponseCode()==200;
      }catch(Exception ignored){}
      finally{if(connectionHttp!=null)connectionHttp.disconnect();}
      final boolean online=ok;
      main.post(()->{
        if(isFinishing()||isDestroyed())return;
        reachable=online;open.setEnabled(online);
        connection.setText(online?"Editor disponível na rede local. Sessão do Chrome ainda precisa ser validada.":"Editor inacessível nesta rede. Tarefa pendente; nenhum arquivo foi alterado.");
        if(!online)updateMeta("editor_job_status","waiting_local_network");
      });
    },"xand-editor-health").start();
  }
  private void openEditor(){
    if(!reachable)return;
    String requested=style.getSelectedItemPosition()==1?"splitfade":"video";
    updateMeta("editor_requested_style",requested);
    updateMeta("editor_job_status","waiting_editor_session");
    updateMeta("editor_started_at",System.currentTimeMillis());
    try{
      Intent view=new Intent(Intent.ACTION_VIEW,Uri.parse(EDITOR_URL));
      view.setPackage("com.android.chrome");
      startActivity(view);
    }catch(Exception error){
      try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(EDITOR_URL)));}
      catch(Exception ignored){status.setText("Não foi possível abrir o Chrome. O editor não foi modificado.");}
    }
  }
  private void selectMp4(){
    Intent choose=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    choose.addCategory(Intent.CATEGORY_OPENABLE);choose.setType("video/mp4");
    choose.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(choose,PICK_MP4);
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request!=PICK_MP4||result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri file=data.getData();MediaMetadataRetriever retriever=new MediaMetadataRetriever();
    try{
      getContentResolver().takePersistableUriPermission(file,Intent.FLAG_GRANT_READ_URI_PERMISSION);
      String filename="MP4";long bytes=-1;
      try(Cursor cursor=getContentResolver().query(file,null,null,null,null)){
        if(cursor!=null&&cursor.moveToFirst()){
          int n=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=cursor.getColumnIndex(OpenableColumns.SIZE);
          if(n>=0)filename=cursor.getString(n);if(s>=0&&!cursor.isNull(s))bytes=cursor.getLong(s);
        }
      }
      if(bytes>=0 && bytes<1024)throw new Exception("Arquivo muito pequeno para um MP4 válido.");
      retriever.setDataSource(this,file);
      String durationText=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      String video=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
      if(durationText==null || !"yes".equalsIgnoreCase(video))throw new Exception("Arquivo sem vídeo válido.");
      long ms=Long.parseLong(durationText);
      if(ms<59000L||ms>182000L)throw new Exception("Duração fora de 1 a 3 minutos: "+(ms/1000)+" s.");
      boolean downloads=false;
      try{String documentId=DocumentsContract.getDocumentId(file);
        downloads=documentId!=null&&(documentId.startsWith("primary:Download/")||
          ("com.android.providers.downloads.documents".equals(file.getAuthority())));
      }catch(Exception ignored){}
      JSONObject all=new JSONObject(prefs.getString("metadata","{}"));
      JSONObject item=all.optJSONObject(musicUrl);if(item==null)item=new JSONObject();
      item.put("editor_video_uri",file.toString());item.put("editor_video_name",filename);
      item.put("editor_video_duration_ms",ms);item.put("editor_video_size_bytes",bytes);
      item.put("editor_downloads_verified",downloads);
      item.put("editor_job_status","downloaded_verified");
      String chosen=style.getSelectedItemPosition()==1?"splitfade":"video";
      item.put("editor_completed_style",chosen);
      item.put("tiktok_original_volume_pct",0);
      item.put("tiktok_added_volume_pct",100);
      item.put("tiktok_sound_check","pending_inside_tiktok");
      all.put(musicUrl,item);
      prefs.edit().putString("metadata",all.toString()).putString("editor_last_completed_style",chosen).apply();
      status.setText("MP4 validado: "+filename+" · "+(ms/1000)+" s"
        +(downloads?" · Downloads confirmado":" · origem Downloads não comprovada"));
      new AlertDialog.Builder(this).setTitle("MP4 recebido")
        .setMessage("O vídeo foi verificado e associado à música da fila. A publicação não foi realizada. "+
          (downloads?"A pasta Downloads foi confirmada.":"A pasta Downloads não pôde ser confirmada pelo seletor."))
        .setPositiveButton("OK",null).show();
    }catch(Exception error){
      status.setText("Falha na validação: "+error.getMessage()+" Nenhum vídeo foi registrado.");
    }finally{try{retriever.release();}catch(Exception ignored){}}
  }
}
