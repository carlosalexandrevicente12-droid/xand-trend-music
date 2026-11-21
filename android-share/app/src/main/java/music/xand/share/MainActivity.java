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
 final java.util.Set<String> coversInProgress=java.util.Collections.synchronizedSet(new java.util.HashSet<>());
 String pendingImageUrl="",pendingExportUrl="",pendingLogoUrl="";
 boolean shareIntentHandled=false;
 void clearConsumedShareIntent(){Intent safe=new Intent(this,MainActivity.class);safe.setAction(Intent.ACTION_MAIN);safe.addCategory(Intent.CATEGORY_LAUNCHER);setIntent(safe);}
 JSONArray load(){try{return new JSONArray(prefs.getString("queue","[]"));}catch(Exception e){return new JSONArray();}}
 void save(JSONArray a){prefs.edit().putString("queue",a.toString()).apply();}
 JSONArray resolved(){try{return new JSONArray(prefs.getString("resolved","[]"));}catch(Exception e){return new JSONArray();}}
 void markResolved(int idx){JSONArray old=load(),next=new JSONArray(),history=resolved();String done=old.optString(idx);for(int j=0;j<old.length();j++){String link=old.optString(j);if(j==idx)history.put(link);else next.put(link);}SharedPreferences.Editor e=prefs.edit().putString("queue",next.toString()).putString("resolved",history.toString());if(done.equals(prefs.getString("recognition_pending_url","")))e.remove("recognition_pending_url");e.remove("review_pending_"+done).remove("image_pending_"+done).remove("logo_pending_"+done).remove("duration_pending_"+done).apply();show();}
 void add(String text){if(text==null)return; Matcher m=Pattern.compile("https?://[^\\s]+").matcher(text); if(!m.find())return; String url=m.group().replaceAll("[),.;]+$",""); if(!TikTokMusicResolver.isTrustedLink(url))return; JSONArray a=load(); for(int i=0;i<a.length();i++)if(url.equals(a.optString(i))){Toast.makeText(this,"Essa música já está na fila.",Toast.LENGTH_LONG).show();return;} JSONArray history=resolved();for(int i=0;i<history.length();i++)if(url.equals(history.optString(i))){Toast.makeText(this,"Link já utilizado. Consulte o histórico.",Toast.LENGTH_LONG).show();return;}a.put(url);save(a);if(!runShareFlows(url))identifySong(url,false);}
 public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("xand_queue",MODE_PRIVATE);CreativeAutomation.ensureWorkflow(prefs);if(b!=null){pendingImageUrl=b.getString("pending_image_url","");pendingExportUrl=b.getString("pending_export_url","");pendingLogoUrl=b.getString("pending_logo_url","");shareIntentHandled=b.getBoolean("share_intent_handled",false);}Intent incoming=getIntent();if(Intent.ACTION_SEND.equals(incoming.getAction())&&!shareIntentHandled){shareIntentHandled=true;clearConsumedShareIntent();if(incoming.getType()!=null&&incoming.getType().startsWith("image/")){show();receiveSharedArtwork(incoming);return;}String shared=incoming.getStringExtra(Intent.EXTRA_TEXT);if(isTikTokShare(shared))add(shared);else{show();importAudioResult(shared);return;}}show();openPendingStepIfRequested(incoming);}
 @Override protected void onSaveInstanceState(Bundle state){state.putString("pending_image_url",pendingImageUrl);state.putString("pending_export_url",pendingExportUrl);state.putString("pending_logo_url",pendingLogoUrl);state.putBoolean("share_intent_handled",shareIntentHandled);super.onSaveInstanceState(state);}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);shareIntentHandled=Intent.ACTION_SEND.equals(intent.getAction());clearConsumedShareIntent();if(shareIntentHandled){if(intent.getType()!=null&&intent.getType().startsWith("image/")){show();receiveSharedArtwork(intent);return;}String shared=intent.getStringExtra(Intent.EXTRA_TEXT);if(isTikTokShare(shared))add(shared);else{show();importAudioResult(shared);return;}}show();openPendingStepIfRequested(intent);}
 
 boolean chatImageSelected(){
  try{
   JSONArray flows=new JSONObject(prefs.getString("workflows_v1","{}")).optJSONArray("flows");
   if(flows==null)return false;
   for(int i=0;i<flows.length();i++){
    JSONObject flow=flows.optJSONObject(i);
    if(flow==null||!flow.optBoolean("enabled",false))continue;
    JSONObject node=connectedNode(flow,"action_image");
    if(node!=null&&"chatgpt_chat".equals(node.optString("image_source","chatgpt_chat")))return true;
   }
  }catch(Exception ignored){}
  return false;
 }
 void sendArtworkPromptToChatGPT(String url,boolean repeat){
  if(!isQueuedLink(url))return;
  if(!repeat&&prefs.getBoolean("chatgpt_handoff_"+url,false))return;
  JSONObject item=info(url);
  String title=item.optString("song","").trim(),artist=item.optString("artist","").trim();
  if(title.isEmpty()||artist.isEmpty()){confirmReview(url);return;}
  String genre=item.optString("genre","").trim();
  String prompt="Crie agora uma imagem ORIGINAL de capa para XAND MUSIC, vertical 9:16 (1080 × 1920). "
    +"Música: "+title+". Artista: "+artist+". "
    +(genre.isEmpty()?"Não invente o gênero da música. ":"Estilo musical confirmado: "+genre+". ")
    +"A arte precisa variar visualmente entre as músicas, sem repetir composição. "
    +"Use a atmosfera da faixa como inspiração, não capturas do TikTok. "
    +"Inclua título e artista legíveis. Use a logo XAND MUSIC original anexada COMPLETA, "
    +"sem cortar, modificar ou redesenhar. Mantenha identidade preta e laranja. "
    +"Gere a imagem nesta conversa do ChatGPT. Depois compartilhe o PNG gerado com o app XAND MUSIC "
    +"pela opção Compartilhar do Android para vincular a arte ao link. Link de referência: "+url;
  String logo=prefs.getString("logo_uri","");
  Intent send=new Intent(Intent.ACTION_SEND);
  if(!logo.isEmpty()){
   Uri uri=Uri.parse(logo);
   send.setType("image/png");
   send.putExtra(Intent.EXTRA_STREAM,uri);
   send.setClipData(android.content.ClipData.newUri(getContentResolver(),"Logo oficial",uri));
   send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  }else send.setType("text/plain");
  send.putExtra(Intent.EXTRA_TEXT,prompt);
  boolean handed=false;
  try{
   send.setPackage("com.openai.chatgpt");
   startActivity(send);
   handed=true;
  }catch(Exception absent){
   try{
    send.setPackage(null);
    startActivity(Intent.createChooser(send,"Enviar instruções ao ChatGPT Chat"));
    handed=true;
   }catch(Exception blocked){
    Toast.makeText(this,"Instale ou abra o ChatGPT para criar a arte.",Toast.LENGTH_LONG).show();
   }
  }
  if(handed)prefs.edit().putBoolean("chatgpt_handoff_"+url,true).apply();
 }
 void receiveSharedArtwork(Intent intent){
  Uri shared=null;
  try{shared=(Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM);}catch(Exception ignored){}
  if(shared==null){Toast.makeText(this,"Imagem não recebida. Compartilhe o arquivo PNG.",Toast.LENGTH_LONG).show();return;}
  final Uri input=shared;
  JSONArray queue=load();
  java.util.ArrayList<String> urls=new java.util.ArrayList<>();
  java.util.ArrayList<String> names=new java.util.ArrayList<>();
  for(int i=0;i<queue.length();i++){
   String url=queue.optString(i);
   if(prefs.getBoolean("image_pending_"+url,false)||prefs.getBoolean("chatgpt_handoff_"+url,false)){
    urls.add(url);
    JSONObject item=info(url);
    names.add(field(item,"song","Música "+(i+1))+" — "+field(item,"artist","Artista pendente"));
   }
  }
  if(urls.isEmpty()){Toast.makeText(this,"Nenhuma música aguardando arte na fila.",Toast.LENGTH_LONG).show();return;}
  if(urls.size()==1){importChatArtwork(urls.get(0),input);return;}
  new AlertDialog.Builder(this).setTitle("Para qual música é esta imagem?")
    .setItems(names.toArray(new String[0]),(d,which)->importChatArtwork(urls.get(which),input))
    .setNegativeButton("Cancelar",null).show();
 }
 void importChatArtwork(String url,Uri source){
  new Thread(()->{
   Uri imported=null;String failure=null;
   try{imported=CreativeAutomation.importSharedImage(this,source);}
   catch(Exception ex){failure=ex.getMessage();}
   final Uri image=imported;final String error=failure;
   runOnUiThread(()->{
    if(image==null){
     Toast.makeText(this,error==null?"Erro ao importar imagem.":error,Toast.LENGTH_LONG).show();
     return;
    }
    try{
     JSONObject all=metadata(),item=all.optJSONObject(url);if(item==null)item=new JSONObject();
     item.put("generated_cover_uri",image.toString());
     item.put("generated_cover_source","chatgpt_chat");
     item.remove("generation_issue");
     all.put(url,item);
     prefs.edit().putString("metadata",all.toString()).remove("image_pending_"+url)
       .remove("logo_pending_"+url).remove("duration_pending_"+url).apply();
     show();Toast.makeText(this,"Arte do ChatGPT vinculada à música.",Toast.LENGTH_LONG).show();
    }catch(Exception ex){Toast.makeText(this,"Falha ao vincular arte.",Toast.LENGTH_LONG).show();}
   });
  }).start();
 }
void openPendingStepIfRequested(Intent incoming){
  if(incoming==null)return;
  String url=incoming.getStringExtra("xand_pending_url");
  String stage=incoming.getStringExtra("xand_pending_stage");
  if(url==null||stage==null)return;
  boolean queued=false;JSONArray q=load();for(int i=0;i<q.length();i++)if(url.equals(q.optString(i)))queued=true;
  incoming.removeExtra("xand_pending_url");incoming.removeExtra("xand_pending_stage");
  if(!queued)return;
  if("review".equals(stage))confirmReview(url);
  else if("image".equals(stage)){if(chatImageSelected())sendArtworkPromptToChatGPT(url,true);else imageOptions(url);}
  else if("logo".equals(stage))logoPosition(url);
  else if("duration".equals(stage))durationOptions(url);
 }

 void selectImage(String url,boolean logo){
  pendingImageUrl=logo?"":url;pendingLogoUrl=logo?url:"";
  Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("image/*");
  pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
  try{startActivityForResult(pick,logo?81:80);}catch(Exception e){Toast.makeText(this,"Seletor de imagens indisponível.",Toast.LENGTH_LONG).show();}
 }
 @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
  super.onActivityResult(requestCode,resultCode,data);
  if(requestCode==82){
   String url=pendingExportUrl;pendingExportUrl="";
   if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null&&!url.isEmpty())renderExport(url,data.getData());
   return;
  }
  if(resultCode!=RESULT_OK||data==null||data.getData()==null||(requestCode!=80&&requestCode!=81))return;
  Uri uri=data.getData();
  try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
  if(requestCode==81){
   prefs.edit().putString("logo_uri",uri.toString()).apply();
   String target=pendingLogoUrl;pendingLogoUrl="";
   Toast.makeText(this,"Logotipo registrado sem alterações.",Toast.LENGTH_LONG).show();show();
   if(!target.isEmpty()&&prefs.getBoolean("logo_pending_"+target,false))logoPosition(target);
   return;
  }
  final String url=pendingImageUrl;pendingImageUrl="";
  if(url.isEmpty())return;
  new Thread(()->{
   String hash=hashImage(uri);
   runOnUiThread(()->{
    JSONObject all=metadata();java.util.Iterator<String> keys=all.keys();
    while(keys.hasNext()){
     String other=keys.next();JSONObject previous=all.optJSONObject(other);
     if(other.equals(url)||previous==null)continue;
     boolean sameUri=uri.toString().equals(previous.optString("image_uri"));
     boolean sameBytes=!hash.isEmpty()&&hash.equals(previous.optString("image_sha256"));
     if(sameUri||sameBytes){
      new AlertDialog.Builder(this).setTitle("Imagem já utilizada").setMessage("Esta imagem já está vinculada a outra música. Deseja reutilizá-la mesmo assim?")
       .setNegativeButton("Escolher outra",null).setPositiveButton("Reutilizar",(d,w)->saveImage(url,uri,hash)).show();return;
     }
    }
    saveImage(url,uri,hash);
   });
  }).start();
 }
 String hashImage(Uri uri){
  try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
   if(in==null)return "";
   java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
   byte[] buffer=new byte[32768];long total=0;int n;
   while((n=in.read(buffer))!=-1){
    total+=n;if(total>50L*1024*1024)return "";
    digest.update(buffer,0,n);
   }
   StringBuilder result=new StringBuilder();
   for(byte value:digest.digest())result.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
   return result.toString();
  }catch(Exception ignored){return "";}
 }
 void saveImage(String url,Uri uri,String sha256){
  try{
   JSONObject all=metadata();JSONObject item=all.optJSONObject(url);if(item==null)item=new JSONObject();
   item.put("image_uri",uri.toString());item.put("image_sha256",sha256);all.put(url,item);
   prefs.edit().putString("metadata",all.toString()).remove("image_pending_"+url).apply();
   if(hasConnectedStep("action_image","action_logo")&&(!item.has("logo_x_pct")||!item.has("logo_width_pct"))){
    prefs.edit().putBoolean("logo_pending_"+url,true).apply();show();logoPosition(url);
   }else{prefs.edit().remove("logo_pending_"+url).remove("duration_pending_"+url).apply();
    Toast.makeText(this,"Imagem registrada com as regras do fluxo.",Toast.LENGTH_SHORT).show();show();}
  }catch(Exception e){Toast.makeText(this,"Não foi possível registrar a imagem.",Toast.LENGTH_SHORT).show();}
 }
 void logoPosition(String url){
  String logoUri=prefs.getString("logo_uri","");
  if(logoUri.isEmpty()){
   new AlertDialog.Builder(this).setTitle("Logo original necessária")
    .setMessage("Selecione primeiro o arquivo oficial XAND MUSIC. Ele será preservado por inteiro.")
    .setNegativeButton("Voltar",null).setPositiveButton("Selecionar logo",(d,w)->selectImage(url,true)).show();
   return;
  }
  final android.graphics.Bitmap logoBitmap;
  try{logoBitmap=ArtworkRenderer.loadLogoPreview(this,Uri.parse(logoUri));}
  catch(Exception e){new AlertDialog.Builder(this).setTitle("Logo indisponível")
   .setMessage("Escolha novamente o arquivo original para ajustar a posição.")
   .setPositiveButton("Selecionar logo",(d,w)->selectImage(url,true)).setNegativeButton("Voltar",null).show();return;}
  JSONObject item=info(url);
  String legacy=prefs.getString("logo_position","Superior direita");
  int defaultX=legacy.endsWith("centro")?50:73;
  int defaultY=legacy.startsWith("Superior")?10:58;
  final int[] state={item.optInt("logo_x_pct",defaultX),item.optInt("logo_y_pct",defaultY),
      item.optInt("logo_width_pct",38)};
  final int width=(int)(getResources().getDisplayMetrics().widthPixels*.63f);
  final int height=width*16/9;
  FrameLayout preview=new FrameLayout(this);
  preview.setBackgroundColor(Color.rgb(24,24,24));
  ImageView background=new ImageView(this);
  background.setScaleType(ImageView.ScaleType.FIT_CENTER);
  String image=item.optString("image_uri","");
  if(!image.isEmpty())try{background.setImageURI(Uri.parse(image));}catch(Exception ignored){}
  preview.addView(background,new FrameLayout.LayoutParams(width,height));
  ImageView logoView=new ImageView(this);
  logoView.setImageBitmap(logoBitmap);
  logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
  preview.addView(logoView,new FrameLayout.LayoutParams(1,1));
  TextView guidance=label("Arraste a logo sobre a prévia. Ajuste o tamanho sem cortar a imagem original.",12,muted,false);
  final Runnable place=()->{
   float targetWidth=width*Math.max(15,Math.min(80,state[2]))/100f;
   float scale=Math.min(targetWidth/logoBitmap.getWidth(),height*.48f/logoBitmap.getHeight());
   int w=Math.max(1,Math.round(logoBitmap.getWidth()*scale));
   int h=Math.max(1,Math.round(logoBitmap.getHeight()*scale));
   FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(w,h);
   logoView.setLayoutParams(lp);
   float left=Math.max(0,Math.min(width-w,width*state[0]/100f-w/2f));
   float top=Math.max(0,Math.min(height-h,height*state[1]/100f-h/2f));
   logoView.setX(left);logoView.setY(top);
  };
  final float[] start={0,0,0,0};
  logoView.setOnTouchListener((view,event)->{
   switch(event.getActionMasked()){
    case android.view.MotionEvent.ACTION_DOWN:
     view.getParent().requestDisallowInterceptTouchEvent(true);
     start[0]=event.getRawX();start[1]=event.getRawY();start[2]=view.getX();start[3]=view.getY();return true;
    case android.view.MotionEvent.ACTION_MOVE:
    case android.view.MotionEvent.ACTION_UP:
     float nx=Math.max(0,Math.min(width-view.getWidth(),start[2]+event.getRawX()-start[0]));
     float ny=Math.max(0,Math.min(height-view.getHeight(),start[3]+event.getRawY()-start[1]));
     view.setX(nx);view.setY(ny);
     state[0]=Math.round((nx+view.getWidth()/2f)*100f/width);
     state[1]=Math.round((ny+view.getHeight()/2f)*100f/height);return true;
   }
   return false;
  });
  SeekBar size=new SeekBar(this);
  size.setMax(65);size.setProgress(Math.max(0,Math.min(65,state[2]-15)));
  TextView sizeLabel=label("Tamanho da logo: "+state[2]+"% da largura",12,fg,true);
  size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){state[2]=15+progress;sizeLabel.setText("Tamanho da logo: "+state[2]+"% da largura");place.run();}
   public void onStartTrackingTouch(SeekBar bar){}
   public void onStopTrackingTouch(SeekBar bar){}
  });
  LinearLayout contents=new LinearLayout(this);contents.setOrientation(1);contents.setPadding(20,12,20,12);
  contents.addView(guidance);contents.addView(preview,new LinearLayout.LayoutParams(width,height));
  contents.addView(sizeLabel);contents.addView(size);
  ScrollView scroll=new ScrollView(this);scroll.addView(contents);
  new AlertDialog.Builder(this).setTitle("Posicionar logo • XAND")
   .setView(scroll).setNegativeButton("Cancelar",null)
   .setPositiveButton("Salvar posição",(d,w)->{
    try{
     JSONObject all=metadata();JSONObject updated=all.optJSONObject(url);if(updated==null)updated=new JSONObject();
     updated.put("logo_x_pct",state[0]);updated.put("logo_y_pct",state[1]);updated.put("logo_width_pct",state[2]);
     all.put(url,updated);
     prefs.edit().putString("metadata",all.toString()).remove("logo_pending_"+url).apply();
     show();
     if(hasConnectedStep("action_logo","action_duration"))durationOptions(url);
     else Toast.makeText(this,"Posição da logo salva para esta música.",Toast.LENGTH_LONG).show();
    }catch(Exception e){Toast.makeText(this,"Erro ao salvar posição.",Toast.LENGTH_LONG).show();}
   }).show();
  preview.post(place);
 }

 void durationOptions(String url){
  JSONObject item=info(url);
  int initial=Math.max(5,Math.min(180,item.optInt("video_duration_seconds",15)));
  LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(32,18,32,18);
  TextView value=label("Duração: "+initial+" segundos",18,accent,true);body.addView(value);
  TextView guide=label("Uma duração única controla todas as camadas. Imagem, logo, título, artista, estilo e efeitos permanecem de 00:00 até o final.",13,fg,false);
  guide.setPadding(0,12,0,14);body.addView(guide);
  SeekBar duration=new SeekBar(this);duration.setMax(175);duration.setProgress(initial-5);body.addView(duration);
  TextView timeline=label("Todas as camadas: 00:00 → "+String.format(java.util.Locale.ROOT,"%02d:%02d",initial/60,initial%60),13,fg,true);
  timeline.setPadding(0,12,0,12);body.addView(timeline);
  TextView note=label("Esta etapa salva a linha do tempo. Exportar um vídeo MP4 com áudio será uma funcionalidade posterior; o PNG continua disponível.",12,muted,false);body.addView(note);
  final int[] seconds={initial};
  duration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
    seconds[0]=5+progress;value.setText("Duração: "+seconds[0]+" segundos");
    timeline.setText("Todas as camadas: 00:00 → "+String.format(java.util.Locale.ROOT,"%02d:%02d",seconds[0]/60,seconds[0]%60));
   }
   public void onStartTrackingTouch(SeekBar bar){}
   public void onStopTrackingTouch(SeekBar bar){}
  });
  new AlertDialog.Builder(this).setTitle("Duração do vídeo • camadas sincronizadas")
   .setView(body).setNegativeButton("Cancelar",null)
   .setPositiveButton("Salvar duração",(d,w)->{
    try{
     JSONObject all=metadata();JSONObject updated=all.optJSONObject(url);if(updated==null)updated=new JSONObject();
     updated.put("video_duration_seconds",seconds[0]);
     updated.put("video_timing_mode","all_layers_full_duration");
     all.put(url,updated);
     prefs.edit().putString("metadata",all.toString()).remove("duration_pending_"+url).apply();
     show();Toast.makeText(this,"Todas as camadas ajustadas para "+seconds[0]+" segundos.",Toast.LENGTH_LONG).show();
    }catch(Exception e){Toast.makeText(this,"Não foi possível salvar duração.",Toast.LENGTH_LONG).show();}
   }).show();
 }

 void imageOptions(String url){
  String[] choices={"Usar imagem do celular como fundo","Selecionar logo original XAND sem recortar","Pesquisar imagens no Pinterest","Pedir imagem inédita ao ChatGPT","Arrastar e ajustar tamanho da logo","Visualizar a capa antes de salvar","Salvar capa PNG 1080 × 1920","Retirar a imagem escolhida"};
  new AlertDialog.Builder(this).setTitle("Imagens e arte • XAND MUSIC").setItems(choices,(d,w)->{
   switch(w){
    case 0:selectImage(url,false);break;
    case 1:selectImage(url,true);break;
    case 2:pinterest(url);break;
    case 3:prepareArtwork(url);break;
    case 4:logoPosition(url);break;
    case 5:previewArt(url);break;
    case 6:beginExport(url);break;
    case 7:try{JSONObject all=metadata();JSONObject item=all.optJSONObject(url);if(item!=null){item.remove("image_uri");item.remove("image_sha256");all.put(url,item);prefs.edit().putString("metadata",all.toString()).apply();show();}}catch(Exception ignored){}break;
   }
  }).show();
 }

 void beginExport(String url){
  if(needsMetadata(url))return;
  JSONObject item=info(url);
  if(item.optString("image_uri","").isEmpty()){
   new AlertDialog.Builder(this).setTitle("Imagem necessária").setMessage("Escolha uma imagem de fundo para a música antes de exportar.")
    .setNegativeButton("Voltar",null).setPositiveButton("Escolher imagem",(d,w)->selectImage(url,false)).show();return;
  }
  if(prefs.getString("logo_uri","").isEmpty()){
   new AlertDialog.Builder(this).setTitle("Logo original necessária")
    .setMessage("Selecione o arquivo original da logo XAND. A exportação manterá a imagem completa, sem cortes nem alterações.")
    .setNegativeButton("Voltar",null).setPositiveButton("Escolher logo",(d,w)->selectImage(url,true)).show();return;
  }
  String base=(field(item,"song","")+"_"+field(item,"artist",""))
    .replaceAll("[^\\p{L}\\p{N} _-]","").trim().replaceAll("\\s+","_");
  if(base.isEmpty())base="XAND_MUSIC";
  if(base.length()>65)base=base.substring(0,65);
  Intent output=new Intent(Intent.ACTION_CREATE_DOCUMENT);
  output.addCategory(Intent.CATEGORY_OPENABLE);
  output.setType("image/png");
  output.putExtra(Intent.EXTRA_TITLE,"XAND_"+base+".png");
  output.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
  pendingExportUrl=url;
  try{startActivityForResult(output,82);}
  catch(Exception e){pendingExportUrl="";Toast.makeText(this,"Não foi possível abrir a escolha de destino.",Toast.LENGTH_LONG).show();}
 }
 void renderExport(String url,Uri destination){
  JSONObject item=info(url);
  String background=item.optString("image_uri",""),logo=prefs.getString("logo_uri","");
  String title=item.optString("song",""),artist=item.optString("artist",""),genre=item.optString("genre","");
  String position=prefs.getString("logo_position","Superior direita");
  ProgressBar spinner=new ProgressBar(this);
  spinner.setPadding(28,20,28,20);
  AlertDialog waiting=new AlertDialog.Builder(this).setTitle("Exportando arte 1080 × 1920").setMessage("Aguarde. A arte é montada na memória.")
    .setView(spinner).setCancelable(false).create();
  waiting.show();
  new Thread(()->{
   String error=null;
   try{
    if(background.isEmpty()||logo.isEmpty())throw new java.io.IOException("Selecione imagem e logo antes de exportar.");
    try(java.io.OutputStream output=getContentResolver().openOutputStream(destination,"w")){
     if(output==null)throw new java.io.IOException("Não foi possível gravar no destino escolhido.");
     ArtworkRenderer.export(this,Uri.parse(background),Uri.parse(logo),title,artist,genre,position,
      item.optInt("logo_x_pct",-1),item.optInt("logo_y_pct",-1),item.optInt("logo_width_pct",-1),output);
    }
   }catch(Exception e){error=e.getMessage()==null?"Falha ao exportar a arte.":e.getMessage();}
   String failure=error;
   runOnUiThread(()->{
    waiting.dismiss();
    if(failure!=null){
     new AlertDialog.Builder(this).setTitle("Exportação não concluída")
      .setMessage(failure+"\nSe o aplicativo de arquivos criou um documento vazio, descarte-o pelo gerenciador de arquivos.")
      .setPositiveButton("Entendi",null).show();
     return;
    }
    prefs.edit().putString("art_export_last",url+"|"+System.currentTimeMillis()).apply();
    new AlertDialog.Builder(this).setTitle("Arte exportada")
     .setMessage("PNG 1080 × 1920 salvo no destino selecionado. Imagem e logo preservadas sem recorte.")
     .setNegativeButton("Concluir",null)
     .setNeutralButton("Abrir",(d,w)->{
      try{Intent openFile=new Intent(Intent.ACTION_VIEW);openFile.setDataAndType(destination,"image/png");
       openFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(openFile);}
      catch(Exception ex){Toast.makeText(this,"Não há aplicativo disponível para abrir o PNG.",Toast.LENGTH_SHORT).show();}
     })
     .setPositiveButton("Compartilhar",(d,w)->{
      try{Intent send=new Intent(Intent.ACTION_SEND);send.setType("image/png");send.putExtra(Intent.EXTRA_STREAM,destination);
       send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(send,"Compartilhar arte XAND"));}
      catch(Exception ex){Toast.makeText(this,"Não foi possível compartilhar a imagem.",Toast.LENGTH_SHORT).show();}
     }).show();
   });
  }).start();
 }

 void previewArt(String url){
  JSONObject item=info(url);
  int width=(int)(getResources().getDisplayMetrics().widthPixels*.67f);
  int height=width*16/9;
  FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.rgb(28,28,28));
  ImageView background=new ImageView(this);background.setScaleType(ImageView.ScaleType.FIT_CENTER);
  String image=item.optString("image_uri","");
  if(!image.isEmpty())try{background.setImageURI(Uri.parse(image));}catch(Exception ignored){}
  frame.addView(background,new FrameLayout.LayoutParams(-1,-1));
  String logo=prefs.getString("logo_uri","");
  if(!logo.isEmpty()){
   ImageView logoView=new ImageView(this);logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
   try{
    android.graphics.Bitmap bitmap=ArtworkRenderer.loadLogoPreview(this,Uri.parse(logo));
    logoView.setImageBitmap(bitmap);
    if(item.has("logo_x_pct")&&item.has("logo_y_pct")&&item.has("logo_width_pct")){
     int pct=Math.max(15,Math.min(80,item.optInt("logo_width_pct",38)));
     float scale=Math.min(width*pct/100f/bitmap.getWidth(),height*.48f/bitmap.getHeight());
     int w=Math.max(1,Math.round(bitmap.getWidth()*scale)),h=Math.max(1,Math.round(bitmap.getHeight()*scale));
     FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(w,h,android.view.Gravity.TOP|android.view.Gravity.LEFT);
     frame.addView(logoView,lp);
     float cx=width*item.optInt("logo_x_pct",73)/100f,cy=height*item.optInt("logo_y_pct",10)/100f;
     logoView.setX(Math.max(0,Math.min(width-w,cx-w/2f)));
     logoView.setY(Math.max(0,Math.min(height-h,cy-h/2f)));
    }else{
     String pos=prefs.getString("logo_position","Superior direita");
     int gravity=(pos.startsWith("Superior")?android.view.Gravity.TOP:android.view.Gravity.BOTTOM)|
       (pos.endsWith("centro")?android.view.Gravity.CENTER_HORIZONTAL:android.view.Gravity.RIGHT);
     FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams((int)(width*.36f),(int)(width*.22f),gravity);
     lp.setMargins(14,14,14,14);frame.addView(logoView,lp);
    }
   }catch(Exception ex){Toast.makeText(this,"Não foi possível ler a logo original.",Toast.LENGTH_SHORT).show();}
  }
  TextView names=label(field(item,"song","Nome da música não informado")+"\n"+field(item,"artist","Artista não informado")+"\n"+field(item,"genre","Estilo não informado"),14,fg,true);
  names.setBackgroundColor(Color.argb(195,0,0,0));names.setPadding(20,14,20,14);
  FrameLayout.LayoutParams titleLp=new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.BOTTOM);
  frame.addView(names,titleLp);
  LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(15,12,15,12);
  body.addView(frame,new LinearLayout.LayoutParams(width,height));
  TextView note=label("Prévia de posicionamento • a exportação adapta os textos em 1080×1920. Nenhum arquivo foi gerado ou salvo.",12,muted,false);
  note.setPadding(0,12,0,2);body.addView(note);
  ScrollView scroll=new ScrollView(this);scroll.addView(body);
  new AlertDialog.Builder(this).setTitle("Prévia da composição 9:16").setView(scroll)
   .setNegativeButton("Fechar",null).setPositiveButton("Escolher imagem",(d,w)->selectImage(url,false)).show();
 }

 JSONObject metadata(){try{return new JSONObject(prefs.getString("metadata","{}"));}catch(Exception e){return new JSONObject();}}
 JSONObject info(String url){JSONObject x=metadata().optJSONObject(url);return x==null?new JSONObject():x;}
 String field(JSONObject x,String key,String fallback){String s=x.optString(key,"").trim();return s.isEmpty()?fallback:s;}
 void edit(String url){JSONObject x=info(url);LinearLayout fields=new LinearLayout(this);fields.setOrientation(1);fields.setPadding(32,16,32,0);EditText song=new EditText(this);song.setHint("Nome da música");song.setSingleLine(true);song.setText(x.optString("song"));fields.addView(song);EditText artist=new EditText(this);artist.setHint("Artista");artist.setSingleLine(true);artist.setText(x.optString("artist"));fields.addView(artist);EditText genre=new EditText(this);genre.setHint("Estilo musical (opcional)");genre.setSingleLine(true);genre.setText(x.optString("genre"));fields.addView(genre);new AlertDialog.Builder(this).setTitle("Dados da música").setView(fields).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->{try{JSONObject all=metadata();JSONObject info=all.optJSONObject(url);if(info==null)info=new JSONObject();info.put("song",song.getText().toString().trim());info.put("artist",artist.getText().toString().trim());info.put("genre",genre.getText().toString().trim());all.put(url,info);prefs.edit().putString("metadata",all.toString()).apply();show();if(prefs.getBoolean("review_pending_"+url,false)&&!song.getText().toString().trim().isEmpty()&&!artist.getText().toString().trim().isEmpty())confirmReview(url);}catch(Exception e){Toast.makeText(this,"Erro ao salvar",Toast.LENGTH_SHORT).show();}}).show();}

 void identifyOptions(String url){
  String[] options={"Reconhecer a música ouvindo (Audile grátis)","Consultar informações públicas do TikTok","Informar ou corrigir título, artista e estilo","Confirmar dados e seguir para imagem","Abrir TikTok para ouvir"};
  new AlertDialog.Builder(this).setTitle("1 • Identificar música")
   .setItems(options,(d,which)->{
    if(which==0)launchAudile(url);
    else if(which==1)identifySong(url,true);
    else if(which==2)edit(url);
    else if(which==3)confirmReview(url);
    else open(url);
   }).setNegativeButton("Voltar",null).show();
 }
 void postOptions(String url){
  String[] options={"Definir duração e sincronizar camadas","Pedir uma arte original ao ChatGPT","Criar e revisar a legenda do TikTok","Salvar capa pronta em PNG 9:16","Enviar dados para outro aplicativo","Copiar o link da música"};
  new AlertDialog.Builder(this).setTitle("3 • Preparar post")
   .setItems(options,(d,which)->{
    if(which==0)durationOptions(url);
    else if(which==1)prepareArtwork(url);
    else if(which==2)prepareCaption(url);
    else if(which==3)beginExport(url);
    else if(which==4)share(url);
    else copyText("Link da música",url);
   }).setNegativeButton("Voltar",null).show();
 }
 void open(String url){try{Intent external=new Intent(Intent.ACTION_VIEW,Uri.parse(url));external.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(external);}catch(Exception e){Toast.makeText(this,"Não foi possível abrir o link",Toast.LENGTH_SHORT).show();}}

 boolean isTikTokShare(String shared){
  if(shared==null)return false;
  Matcher urls=Pattern.compile("https?://[^\\s]+").matcher(shared);
  while(urls.find())if(TikTokMusicResolver.isTrustedLink(urls.group().replaceAll("[),.;]+$","")))return true;
  return false;
 }
 boolean queued(String url){
  if(url==null||url.isEmpty())return false;
  JSONArray items=load();for(int i=0;i<items.length();i++)if(url.equals(items.optString(i)))return true;
  return false;
 }
 void launchAudile(String url){
  Intent external=getPackageManager().getLaunchIntentForPackage("com.mrsep.musicrecognizer");
  if(external==null){
   new AlertDialog.Builder(this).setTitle("Reconhecimento por áudio")
    .setMessage("Audile não está instalado. É um aplicativo gratuito de código aberto. Instale a versão oficial pelo GitHub.")
    .setNegativeButton("Cancelar",null)
    .setPositiveButton("GitHub oficial",(d,w)->open("https://github.com/AudileTeam/Audile/releases/latest")).show();
   return;
  }
  prefs.edit().putString("recognition_pending_url",url).apply();
  try{
   external.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   startActivity(external);
   Toast.makeText(this,"Reconheça no Audile e use Compartilhar → XAND MUSIC.",Toast.LENGTH_LONG).show();
  }catch(Exception e){Toast.makeText(this,"Não foi possível abrir o Audile.",Toast.LENGTH_LONG).show();}
 }
 void importAudioResult(String shared){
  if(shared==null||shared.trim().isEmpty())return;
  String url=prefs.getString("recognition_pending_url","");
  if(!queued(url)){
   new AlertDialog.Builder(this).setTitle("Resultado de áudio recebido")
    .setMessage("Nenhuma música da fila está vinculada a este reconhecimento. Selecione uma música no XAND MUSIC e toque em 'Reconhecer áudio • Audile' antes de compartilhar o resultado.")
    .setPositiveButton("Entendi",null).show();
   return;
  }
  String first=shared.split("\\r?\\n",2)[0].trim();
  String title=first,artist="";
  int dash=first.indexOf(" - ");
  if(dash>0){title=first.substring(0,dash).trim();artist=first.substring(dash+3).trim();}
  JSONObject previous=info(url);
  LinearLayout fields=new LinearLayout(this);fields.setOrientation(1);fields.setPadding(32,16,32,0);
  TextView target=label("Confirmar reconhecimento para o link:\n"+url,12,muted,false);fields.addView(target);
  EditText song=new EditText(this);song.setHint("Música identificada");song.setSingleLine(true);song.setText(title);fields.addView(song);
  EditText performer=new EditText(this);performer.setHint("Artista identificado");performer.setSingleLine(true);performer.setText(artist);fields.addView(performer);
  EditText genre=new EditText(this);genre.setHint("Estilo musical (opcional)");genre.setSingleLine(true);genre.setText(previous.optString("genre"));fields.addView(genre);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Importar resultado do Audile")
   .setMessage("Confira os dados compartilhados antes de vincular à música. A identificação acústica não garante correspondência exata.")
   .setView(fields).setNegativeButton("Cancelar",null).setPositiveButton("Salvar na fila",null).create();
  dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   String titleText=song.getText().toString().trim(),artistText=performer.getText().toString().trim();
   if(titleText.isEmpty()||artistText.isEmpty()){
    Toast.makeText(this,"Informe música e artista antes de salvar.",Toast.LENGTH_LONG).show();return;
   }
   if(!queued(url)){Toast.makeText(this,"Este link não está mais na fila.",Toast.LENGTH_LONG).show();dialog.dismiss();return;}
   try{
    JSONObject all=metadata(),item=all.optJSONObject(url);
    if(item==null)item=new JSONObject();
    item.put("song",titleText);item.put("artist",artistText);item.put("genre",genre.getText().toString().trim());
    item.put("audio_import_confirmed",true);
    all.put(url,item);
    prefs.edit().putString("metadata",all.toString()).remove("recognition_pending_url").apply();
    dialog.dismiss();show();
   }catch(Exception e){Toast.makeText(this,"Não foi possível salvar o resultado.",Toast.LENGTH_LONG).show();}
  }));
  dialog.show();
 }
 void share(String url){JSONObject x=info(url);String text=field(x,"song","Música não identificada")+" — "+field(x,"artist","Artista não identificado")+"\n"+field(x,"genre","")+"\n"+url;Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(send,"Compartilhar música"));}
 
 
 
 boolean hasConnectedStep(String sourceType,String targetType){
  try{
   JSONArray flows=new JSONObject(prefs.getString("workflows_v1","{}")).optJSONArray("flows");if(flows==null)return false;
   for(int i=0;i<flows.length();i++){
    JSONObject flow=flows.optJSONObject(i);if(flow==null||!flow.optBoolean("enabled",false))continue;
    JSONArray nodes=flow.optJSONArray("nodes"),edges=flow.optJSONArray("edges");if(nodes==null||edges==null)continue;
    java.util.HashMap<String,String> types=new java.util.HashMap<>();
    for(int n=0;n<nodes.length();n++){JSONObject node=nodes.optJSONObject(n);if(node!=null)types.put(node.optString("id"),node.optString("type"));}
    for(int e=0;e<edges.length();e++){JSONObject edge=edges.optJSONObject(e);
     if(edge!=null&&sourceType.equals(types.get(edge.optString("from")))&&targetType.equals(types.get(edge.optString("to"))))return true;
    }
   }
  }catch(Exception ignored){}
  return false;
 }
 boolean hasImageAfterReview(){return hasConnectedStep("action_review","action_image");}
 void completeReview(String url){
  if(needsMetadata(url))return;
  prefs.edit().remove("review_pending_"+url).putBoolean("review_confirmed_"+url,true).apply();
  if(hasAutomaticImageNode()){continueCreativeAutomation(url);show();return;}
  if(hasImageAfterReview()&&info(url).optString("image_uri","").isEmpty()){
   prefs.edit().putBoolean("image_pending_"+url,true).apply();
   show();imageOptions(url);
  }else if(hasConnectedStep("action_image","action_logo")&&!info(url).has("logo_x_pct")){
   prefs.edit().putBoolean("logo_pending_"+url,true).apply();show();logoPosition(url);
  }else{
   show();Toast.makeText(this,"Dados confirmados para esta música.",Toast.LENGTH_LONG).show();
  }
 }
 void confirmReview(String url){
  JSONObject item=info(url);
  String song=item.optString("song","").trim(),artist=item.optString("artist","").trim(),genre=item.optString("genre","").trim();
  if(song.isEmpty()||artist.isEmpty()){
   prefs.edit().putBoolean("review_pending_"+url,true).apply();
   edit(url);return;
  }
  new AlertDialog.Builder(this).setTitle("Confirmar dados da música")
   .setMessage("Música: "+song+"\\nArtista: "+artist+"\\nEstilo: "+(genre.isEmpty()?"Não informado":genre)+"\\n\\nConfirme somente se os dados estiverem corretos. O nome do som original no TikTok não comprova o artista.")
   .setNegativeButton("Corrigir",(d,w)->edit(url))
   .setPositiveButton("Confirmar e continuar",(d,w)->completeReview(url)).show();
 }
 void applyConfiguredVisualRules(String url,JSONArray nodes){
  try{
   JSONObject all=metadata();JSONObject item=all.optJSONObject(url);if(item==null)item=new JSONObject();
   boolean changed=false;
   for(int j=0;j<nodes.length();j++){
    JSONObject n=nodes.optJSONObject(j);if(n==null)continue;
    if("action_logo".equals(n.optString("type"))&&!item.has("logo_x_pct")){
     String pos=n.optString("placement","rightTop");
     item.put("logo_x_pct",pos.startsWith("center")?50:73);
     item.put("logo_y_pct",pos.endsWith("Bottom")?68:12);
     item.put("logo_width_pct",Math.max(15,Math.min(80,n.optInt("logo_width_pct",38))));
     changed=true;
    }else if("action_duration".equals(n.optString("type"))&&!item.has("video_duration_seconds")){
     item.put("video_duration_seconds",Math.max(5,Math.min(180,n.optInt("duration_sec",15))));
     item.put("video_timing_mode","all_layers_full_duration");
     changed=true;
    }
   }
   if(changed){all.put(url,item);prefs.edit().putString("metadata",all.toString()).apply();}
  }catch(Exception ignored){}
 }

 JSONObject connectedNode(JSONObject flow,String expectedType){
  JSONArray nodes=flow.optJSONArray("nodes"),edges=flow.optJSONArray("edges");
  if(nodes==null||edges==null)return null;
  java.util.HashMap<String,JSONObject> byId=new java.util.HashMap<>();
  java.util.ArrayDeque<String> pending=new java.util.ArrayDeque<>();
  java.util.HashSet<String> visited=new java.util.HashSet<>();
  for(int i=0;i<nodes.length();i++){
   JSONObject node=nodes.optJSONObject(i);if(node==null)continue;
   String id=node.optString("id");if(id.isEmpty())continue;
   byId.put(id,node);
   if("trigger_share".equals(node.optString("type")))pending.add(id);
  }
  int limit=0;
  while(!pending.isEmpty()&&limit++<80){
   String id=pending.removeFirst();if(!visited.add(id))continue;
   JSONObject node=byId.get(id);if(node==null)continue;
   if(expectedType.equals(node.optString("type")))return node;
   for(int e=0;e<edges.length();e++){
    JSONObject edge=edges.optJSONObject(e);
    if(edge!=null&&id.equals(edge.optString("from"))&&byId.containsKey(edge.optString("to")))
     pending.addLast(edge.optString("to"));
   }
  }
  return null;
 }
 boolean hasAutomaticImageNode(){
  try{
   JSONArray flows=new JSONObject(prefs.getString("workflows_v1","{}")).optJSONArray("flows");
   if(flows==null)return false;
   for(int i=0;i<flows.length();i++){
    JSONObject flow=flows.optJSONObject(i);
    if(flow==null||!flow.optBoolean("enabled",false))continue;
    JSONObject node=connectedNode(flow,"action_image");
    if(node!=null&&("generated_local".equals(node.optString("image_source","chatgpt_chat"))||"chatgpt_chat".equals(node.optString("image_source","chatgpt_chat"))))return true;
   }
  }catch(Exception ignored){}
  return false;
 }
 boolean isQueuedLink(String url){
  JSONArray items=load();for(int i=0;i<items.length();i++)if(url.equals(items.optString(i)))return true;
  return false;
 }
 void invokeChatArtScript(String url,JSONObject flow){
  if(prefs.getBoolean("chatgpt_handoff_"+url,false))return;
  JSONObject caption=connectedNode(flow,"action_caption");
  if(caption==null)return;
  JSONArray nodes=flow.optJSONArray("nodes"),edges=flow.optJSONArray("edges");
  if(nodes==null||edges==null)return;
  for(int i=0;i<nodes.length();i++){
   JSONObject node=nodes.optJSONObject(i);
   if(node==null||!"action_script".equals(node.optString("type"))||
      !"xand_chatgpt_art_script_v1".equals(node.optString("scriptId")))continue;
   for(int e=0;e<edges.length();e++){
    JSONObject edge=edges.optJSONObject(e);
    if(edge!=null&&caption.optString("id").equals(edge.optString("from"))&&
       node.optString("id").equals(edge.optString("to"))){
     ScriptExecutor.run(this,prefs,node.optString("scriptId"),url);
     return;
    }
   }
  }
 }
 void continueCreativeAutomation(String url){
  if(!isQueuedLink(url))return;
  try{
   JSONArray flows=new JSONObject(prefs.getString("workflows_v1","{}")).optJSONArray("flows");
   if(flows==null)return;
   for(int i=0;i<flows.length();i++){
    JSONObject flow=flows.optJSONObject(i);
    if(flow==null||!flow.optBoolean("enabled",false))continue;
    JSONObject review=connectedNode(flow,"action_review");
    JSONObject image=connectedNode(flow,"action_image");
    JSONObject captionNode=connectedNode(flow,"action_caption");
    if(image==null&&captionNode==null)continue;
    JSONObject item=info(url);
    String song=item.optString("song","").trim(),artist=item.optString("artist","").trim();
    if(song.isEmpty()||artist.isEmpty())return; // Sem credenciais inventadas.
    boolean confirmed=prefs.getBoolean("review_confirmed_"+url,false);
    boolean verified="identified".equals(item.optString("recognition_status"));
    if(review!=null){
     String mode=review.optString("review_mode","auto_verified");
     if("manual".equals(mode)&&!confirmed)return;
     if(!verified&&!confirmed)return;
     prefs.edit().remove("review_pending_"+url).apply();
    }else if(!verified&&!confirmed)return;
    JSONObject all=metadata();
    item=all.optJSONObject(url);if(item==null)item=new JSONObject();
    long seed=CreativeAutomation.seed(url);
    if(captionNode!=null&&!item.has("caption_user")){
     String caption=CreativeAutomation.caption(song,artist,item.optString("genre",""),
       captionNode.optString("caption_mode","engagement"),seed);
     item.put("caption_auto",caption);item.put("caption_generation","local_template");
    }
    all.put(url,item);prefs.edit().putString("metadata",all.toString()).apply();
    if(image==null)return;
    String source=image.optString("image_source","chatgpt_chat");
    if("chatgpt_chat".equals(source)){
     if(item.optString("generated_cover_uri","").isEmpty()){
      item.put("generation_issue","Aguardando a imagem criada no ChatGPT Chat.");
      all.put(url,item);prefs.edit().putString("metadata",all.toString())
        .putBoolean("image_pending_"+url,true).apply();
      invokeChatArtScript(url,flow);
     }else prefs.edit().remove("image_pending_"+url).apply();
     return;
    }
    if(!"generated_local".equals(source))return;
    if(!item.optString("generated_cover_uri","").isEmpty()||
       !item.optString("image_uri","").isEmpty()){
     prefs.edit().remove("image_pending_"+url).remove("image_generating_"+url).apply();
     return;
    }
    String logo=prefs.getString("logo_uri","");
    if(logo.isEmpty()){
     item.put("generation_issue","Registre a logo original no nó Posicionar logo.");
     all.put(url,item);prefs.edit().putString("metadata",all.toString()).apply();
     prefs.edit().putBoolean("image_pending_"+url,true).apply();return;
    }
    if(!coversInProgress.add(url))return;
    String artStyle=image.optString("art_style","auto");
    final String title=song,performer=artist,genre=item.optString("genre","");
    final int lx=item.optInt("logo_x_pct",73),ly=item.optInt("logo_y_pct",12),
      lw=item.optInt("logo_width_pct",38);
    prefs.edit().putBoolean("image_generating_"+url,true).apply();
    new Thread(()->{
     Uri result=null;String error=null;
     try{
      result=CreativeAutomation.saveCover(this,Uri.parse(logo),title,performer,genre,
        artStyle,seed,lx,ly,lw);
     }catch(Exception failure){error=failure.getMessage();}
     final Uri finished=result;
     final String failure=error;
     runOnUiThread(()->{
      coversInProgress.remove(url);
      try{
       JSONObject latest=metadata();JSONObject updated=latest.optJSONObject(url);
       if(updated==null)updated=new JSONObject();
       if(finished!=null){
        updated.put("generated_cover_uri",finished.toString());
        updated.put("generated_cover_source","generated_local");
        updated.put("generated_cover_style",artStyle);
        updated.remove("generation_issue");
        prefs.edit().remove("image_pending_"+url).remove("logo_pending_"+url)
          .remove("duration_pending_"+url).apply();
       }else{
        updated.put("generation_issue",failure==null?"Falha na geração da arte.":failure);
        prefs.edit().putBoolean("image_pending_"+url,true).apply();
       }
       latest.put(url,updated);
       prefs.edit().putString("metadata",latest.toString())
         .remove("image_generating_"+url).apply();
      }catch(Exception ignored){}
      show();
     });
    }).start();
    return;
   }
  }catch(Exception failure){
   Toast.makeText(this,"Não foi possível executar a criação automática.",Toast.LENGTH_SHORT).show();
  }
 }

 boolean runShareFlows(String url){
  CreativeAutomation.ensureWorkflow(prefs);
  boolean identifiedByNode=false;
  try{
   JSONObject saved=new JSONObject(prefs.getString("workflows_v1","{}"));
   JSONArray flows=saved.optJSONArray("flows");if(flows==null)return false;
   for(int i=0;i<flows.length();i++){
    JSONObject flow=flows.optJSONObject(i);if(flow==null||!flow.optBoolean("enabled",false))continue;
    JSONArray nodes=flow.optJSONArray("nodes"),edges=flow.optJSONArray("edges");if(nodes==null||edges==null)continue;
    applyConfiguredVisualRules(url,nodes);
    java.util.HashMap<String,String> types=new java.util.HashMap<>();
    java.util.HashMap<String,String> assignedScripts=new java.util.HashMap<>();
    java.util.ArrayDeque<String> todo=new java.util.ArrayDeque<>();
    java.util.HashSet<String> visited=new java.util.HashSet<>();
    for(int j=0;j<nodes.length();j++){JSONObject n=nodes.optJSONObject(j);if(n==null)continue;String id=n.optString("id");String type=n.optString("type");if(id.isEmpty())continue;types.put(id,type);if("action_script".equals(type))assignedScripts.put(id,n.optString("scriptId",""));if("trigger_share".equals(type))todo.add(id);}
    int limit=0;
    while(!todo.isEmpty()&&limit++<60){
     String current=todo.removeFirst();if(!visited.add(current))continue;
     String type=types.get(current);
     if("action_notify".equals(type))Toast.makeText(this,"XAND: link recebido pela automação.",Toast.LENGTH_SHORT).show();
     else if("action_identify".equals(type)){identifiedByNode=true;identifySong(url,false);}
     else if("action_review".equals(type)){
      prefs.edit().putBoolean("review_pending_"+url,true).apply();
      // Em modo automático aguarda a consulta pública, sem solicitar ação antecipada.
      continue;
     }
     else if("action_image".equals(type)){
      prefs.edit().putBoolean("image_pending_"+url,true).apply();
      continue; // A etapa automática continua após confirmar os créditos.
     }
     else if("action_logo".equals(type)){
      if(!info(url).has("logo_x_pct")){prefs.edit().putBoolean("logo_pending_"+url,true).apply();continue;}
     }
     else if("action_duration".equals(type)){
      if(!info(url).has("video_duration_seconds")){prefs.edit().putBoolean("duration_pending_"+url,true).apply();continue;}
     }
     else if("action_caption".equals(type))continueCreativeAutomation(url);
     else if("action_copy".equals(type))((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("TikTok",url));
     else if("action_open".equals(type))open(url);
     else if("action_script".equals(type))ScriptExecutor.run(this,prefs,assignedScripts.getOrDefault(current,""),url);
     // action_queue é idempotente: add() já registrou o link.
     for(int e=0;e<edges.length();e++){JSONObject edge=edges.optJSONObject(e);if(edge!=null&&current.equals(edge.optString("from"))){String dest=edge.optString("to");if(types.containsKey(dest)&&!visited.contains(dest))todo.addLast(dest);}}
    }
   }
  }catch(Exception ex){Toast.makeText(this,"Falha ao executar automação.",Toast.LENGTH_SHORT).show();}
  return identifiedByNode;
 }

 void identifySong(String url,boolean interactive){
  if(interactive)Toast.makeText(this,"Consultando créditos públicos do TikTok...",Toast.LENGTH_SHORT).show();
  new Thread(()->{
   TikTokMusicResolver.Result result=TikTokMusicResolver.lookup(url);
   runOnUiThread(()->{
    try{
     JSONObject all=metadata();JSONObject item=all.optJSONObject(url);
     if(item==null)item=new JSONObject();
     if(result.verified()){
      if(item.optString("song","").trim().isEmpty()){
       item.put("song",result.title);item.put("auto_song",result.title);
      }
      if(item.optString("artist","").trim().isEmpty()){
       item.put("artist",result.artist);item.put("auto_artist",result.artist);
      }
     }else{
      String lastSong=item.optString("auto_song","");
      String lastArtist=item.optString("auto_artist","");
      if(!lastSong.isEmpty()&&lastSong.equals(item.optString("song","")))item.remove("song");
      if(!lastArtist.isEmpty()&&lastArtist.equals(item.optString("artist","")))item.remove("artist");
      item.remove("auto_song");item.remove("auto_artist");
     }
     item.put("recognition_status",result.status);
     item.put("recognition_checked_at",System.currentTimeMillis());
     item.put("recognition_source",result.source);
     item.put("recognition_type",result.type);
     item.put("recognition_reason",result.reason);
     item.put("recognition_url",result.canonical);
     item.put("sound_hint",result.soundHint);
     all.put(url,item);prefs.edit().putString("metadata",all.toString()).apply();
     continueCreativeAutomation(url);
     show();
    }catch(Exception e){Toast.makeText(this,"Não foi possível salvar a consulta.",Toast.LENGTH_SHORT).show();}
    if(!interactive)return;
    String caption="Tipo: "+result.type+"\n";
    if(result.verified())caption+="Música: "+result.title+"\nArtista: "+result.artist+"\n";
    else caption+="Música e artista: não confirmados\n";
    if(!result.soundHint.isEmpty())caption+="Som indicado: "+result.soundHint+"\n";
    caption+="Estilo: não informado automaticamente\n\n"+result.reason;
    new AlertDialog.Builder(this).setTitle(result.verified()?"Música identificada":"Consulta de música")
     .setMessage(caption)
     .setNegativeButton("Voltar",null)
     .setNeutralButton("Editar música",(dialog,which)->edit(url))
     .setPositiveButton("Abrir TikTok",(dialog,which)->open(result.canonical)).show();
   });
  }).start();
 }

 void copyText(String title,String value){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText(title,value));Toast.makeText(this,"Copiado.",Toast.LENGTH_SHORT).show();}
 void shareText(String title,String value){Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,value);startActivity(Intent.createChooser(send,title));}
 boolean needsMetadata(String url){JSONObject x=info(url);if(x.optString("song","").trim().isEmpty()||x.optString("artist","").trim().isEmpty()){Toast.makeText(this,"Preencha música e artista primeiro.",Toast.LENGTH_LONG).show();edit(url);return true;}return false;}
 String caption(String url){JSONObject x=info(url);String generated=x.optString("caption_auto","");if(!generated.isEmpty())return generated;String song=field(x,"song","Música não identificada"),artist=field(x,"artist","Artista não identificado"),genre=x.optString("genre","").trim();String result="🎵 "+song+" — "+artist;if(!genre.isEmpty())result+="\n🎧 Estilo: "+genre;return result+"\n\nQual sua parte favorita? 🎶\n\n#XANDMUSIC #TikTokMusic";}
 void prepareCaption(String url){if(needsMetadata(url))return;EditText input=new EditText(this);input.setText(caption(url));input.setMinLines(5);input.setGravity(android.view.Gravity.TOP);LinearLayout layout=new LinearLayout(this);layout.setPadding(28,12,28,0);layout.addView(input,new LinearLayout.LayoutParams(-1,-2));new AlertDialog.Builder(this).setTitle("Legenda para o TikTok").setView(layout).setNegativeButton("Voltar",null).setNeutralButton("Copiar",(d,w)->copyText("Legenda XAND",input.getText().toString())).setPositiveButton("Compartilhar",(d,w)->shareText("Enviar legenda",input.getText().toString())).show();}
 void pinterest(String url){if(needsMetadata(url))return;JSONObject x=info(url);String query=field(x,"song","")+" "+field(x,"artist","")+" "+field(x,"genre","")+" music cover art";open("https://www.pinterest.com/search/pins/?q="+Uri.encode(query.trim()));}
 void prepareArtwork(String url){if(needsMetadata(url))return;JSONObject x=info(url);String song=field(x,"song",""),artist=field(x,"artist",""),genre=field(x,"genre","estilo ainda não confirmado");String prompt="Crie uma arte vertical 9:16 para o perfil XAND MUSIC. Música: "+song+". Artista: "+artist+". Estilo: "+genre+". A imagem deve se inspirar na atmosfera da música, nunca em capturas de tela do TikTok. Incluir nome da música, artista, estilo e meu logotipo oficial XAND MUSIC completo, sem cortar ou alterar. Se eu ainda não tiver enviado a logo, peça o arquivo original antes de finalizar. Evite imagens repetidas, excesso de efeitos artificiais e padrões genéricos. Respeite os direitos de uso de imagens de terceiros. Gere uma proposta visual original.";shareText("Enviar instruções ao ChatGPT",prompt);}

 boolean isSelected(String url){return prefs.getBoolean("selected_"+url,false);}
 void setSelected(String url,boolean selected){prefs.edit().putBoolean("selected_"+url,selected).apply();show();}
 int bg=Color.rgb(9,9,9),panel=Color.rgb(21,21,21),stroke=Color.rgb(48,48,48),fg=Color.rgb(245,245,245),muted=Color.rgb(166,166,166),accent=Color.rgb(255,133,0);
 GradientDrawable shape(int color,int radius,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);if(border!=0)d.setStroke(2,border);return d;}
 TextView label(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
 Button action(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(primary?bg:fg);b.setBackground(shape(primary?accent:Color.rgb(42,42,42),20,primary?0:stroke));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,104);lp.topMargin=10;b.setLayoutParams(lp);return b;}
 void moveInQueue(int idx,int delta){
  JSONArray before=load();int next=idx+delta;
  if(idx<0||idx>=before.length()||next<0||next>=before.length())return;
  JSONArray reordered=new JSONArray();
  for(int k=0;k<before.length();k++)reordered.put(before.optString(k==idx?next:k==next?idx:k));
  save(reordered);show();
 }
 void removeFromQueue(int idx){
  JSONArray current=load();if(idx<0||idx>=current.length())return;
  String url=current.optString(idx);
  new AlertDialog.Builder(this).setTitle("Remover da fila?")
   .setMessage("O link sairá da fila de produção. Os dados já registrados não serão apagados.")
   .setNegativeButton("Cancelar",null).setPositiveButton("Remover",(d,w)->{
    JSONArray next=new JSONArray();for(int k=0;k<current.length();k++)if(k!=idx)next.put(current.optString(k));
    save(next);show();
   }).show();
 }
 void openGeneratedCover(String url){
  String uri=info(url).optString("generated_cover_uri","");
  if(uri.isEmpty())return;
  try{
   Intent view=new Intent(Intent.ACTION_VIEW);
   view.setDataAndType(Uri.parse(uri),"image/png");
   view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   startActivity(Intent.createChooser(view,"Visualizar arte XAND"));
  }catch(Exception ex){Toast.makeText(this,"Não foi possível abrir a arte.",Toast.LENGTH_LONG).show();}
 }
 void shareGeneratedCover(String url){
  JSONObject item=info(url);
  String uri=item.optString("generated_cover_uri","");
  if(uri.isEmpty())return;
  try{
   Uri cover=Uri.parse(uri);
   Intent send=new Intent(Intent.ACTION_SEND);
   send.setType("image/png");
   send.putExtra(Intent.EXTRA_STREAM,cover);
   send.putExtra(Intent.EXTRA_TEXT,caption(url));
   send.setClipData(android.content.ClipData.newUri(getContentResolver(),"Arte XAND",cover));
   send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   startActivity(Intent.createChooser(send,"Enviar arte e legenda"));
  }catch(Exception ex){Toast.makeText(this,"Falha ao compartilhar a arte.",Toast.LENGTH_LONG).show();}
 }
 void manageQueueItem(int idx){
  JSONArray items=load();if(idx<0||idx>=items.length())return;
  String url=items.optString(idx);
  JSONObject item=info(url);
  boolean cover=!item.optString("generated_cover_uri","").isEmpty();
  boolean legend=!item.optString("caption_auto","").isEmpty();
  java.util.ArrayList<String> options=new java.util.ArrayList<>();
  options.add("Mover uma posição para cima");
  options.add("Mover uma posição para baixo");
  options.add("Remover da fila");
  options.add("Abrir link original");
  if(chatImageSelected()&&!cover)options.add("Executar script no ChatGPT Chat");
  if(cover){options.add("Visualizar arte criada");options.add("Compartilhar arte e legenda");}
  if(legend)options.add("Copiar legenda");
  new AlertDialog.Builder(this).setTitle("Gerenciar link "+(idx+1))
   .setItems(options.toArray(new String[0]),(d,w)->{
    String chosen=options.get(w);
    if(w==0)moveInQueue(idx,-1);
    else if(w==1)moveInQueue(idx,1);
    else if(w==2)removeFromQueue(idx);
    else if(w==3)open(url);
    else if("Executar script no ChatGPT Chat".equals(chosen))sendArtworkPromptToChatGPT(url,true);
    else if("Visualizar arte criada".equals(chosen))openGeneratedCover(url);
    else if("Compartilhar arte e legenda".equals(chosen))shareGeneratedCover(url);
    else if("Copiar legenda".equals(chosen))copyText("Legenda XAND",caption(url));
   }).setNegativeButton("Voltar",null).show();
 }
 void show(){
  root=new LinearLayout(this);root.setOrientation(1);root.setPadding(34,44,34,44);root.setBackgroundColor(bg);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
  TextView brand=label("XAND  MUSIC",26,fg,true);root.addView(brand);
  TextView brandNote=label("●  MUSIC ANALYTICS  /  PRETO + LARANJA",11,Color.rgb(255,171,78),true);
  brandNote.setPadding(0,4,0,8);root.addView(brandNote);
  TextView heading=label("MINHA FILA DE MÚSICAS",23,fg,true);
  heading.setPadding(0,18,0,6);root.addView(heading);
  TextView intro=label("Compartilhe os links pelo TikTok. Aqui você só organiza a lista e acompanha o andamento.",14,muted,false);
  intro.setPadding(0,0,0,20);root.addView(intro);
  JSONArray a=load(),history=resolved();
  TextView summary=label(a.length()+" na fila  •  "+history.length()+" concluída(s)",14,accent,true);
  summary.setPadding(0,2,0,18);root.addView(summary);
  if(a.length()==0){
   TextView empty=label("Fila vazia. No TikTok, toque em Compartilhar → XAND MUSIC para adicionar uma música.",14,muted,false);
   empty.setPadding(18,24,18,24);empty.setBackground(shape(panel,22,stroke));root.addView(empty);
  }
  for(int i=0;i<a.length();i++){
   final int index=i;String url=a.optString(i);JSONObject item=info(url);
   LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(22,18,22,18);
   card.setBackground(shape(panel,24,stroke));
   LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=15;root.addView(card,cp);
   TextView order=label(i==0?"01  •  PRÓXIMA MÚSICA":String.format(java.util.Locale.ROOT,"%02d  •  NA FILA",i+1),12,accent,true);
   card.addView(order);
   String title=item.optString("song","").trim(),artist=item.optString("artist","").trim();
   TextView music=label(title.isEmpty()?"Música ainda não identificada":title,18,fg,true);
   music.setPadding(0,10,0,2);card.addView(music);
   TextView performer=label(artist.isEmpty()?"Artista pendente":artist,13,muted,false);card.addView(performer);
   String genre=item.optString("genre","").trim();
   if(!genre.isEmpty()){TextView g=label("Estilo: "+genre,12,muted,false);g.setPadding(0,4,0,0);card.addView(g);}
   String state;
   if(prefs.getBoolean("image_generating_"+url,false))state="Gerando arte original no celular";
   else if(prefs.getBoolean("chatgpt_handoff_"+url,false)&&item.optString("generated_cover_uri","").isEmpty())state="Aguardando imagem do ChatGPT Chat";
   else if(!item.optString("generated_cover_uri","").isEmpty()&&!item.optString("caption_auto","").isEmpty())state="Arte e legenda prontas";
   else if(!item.optString("generated_cover_uri","").isEmpty())state="Arte pronta";
   else if(!item.optString("caption_auto","").isEmpty()&&!item.optString("generation_issue","").isEmpty())state="Legenda pronta • confira a logo no fluxo";
   else if(prefs.getBoolean("review_pending_"+url,false))state="Identificação aguardando confirmação";
   else if(prefs.getBoolean("image_pending_"+url,false))state="Aguardando criação da imagem";
   else if(prefs.getBoolean("logo_pending_"+url,false))state="Aguardando composição da arte";
   else if(prefs.getBoolean("duration_pending_"+url,false))state="Aguardando renderização do vídeo";
   else if("original".equals(item.optString("recognition_status","")))state="Som original • artista não confirmado";
   else state="Aguardando processamento";
   TextView status=label("●  "+state,12,accent,true);status.setPadding(0,10,0,6);card.addView(status);
   TextView link=label(url,11,muted,false);link.setTextIsSelectable(true);card.addView(link);
   LinearLayout controls=new LinearLayout(this);controls.setOrientation(0);controls.setGravity(android.view.Gravity.CENTER_VERTICAL);
   controls.setPadding(0,10,0,0);card.addView(controls);
   CheckBox done=new CheckBox(this);done.setText("Resolvido");done.setTextColor(fg);
   done.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));
   controls.addView(done,new LinearLayout.LayoutParams(0,-2,1));
   done.setOnCheckedChangeListener((button,checked)->{if(!checked)return;
    new AlertDialog.Builder(this).setTitle("Confirmar conclusão")
     .setMessage("Marque como resolvido somente depois de confirmar a publicação. O link ficará no histórico.")
     .setNegativeButton("Cancelar",(d,w)->done.setChecked(false))
     .setOnCancelListener(d->done.setChecked(false))
     .setPositiveButton("Concluir",(d,w)->markResolved(index)).show();
   });
   Button manage=new Button(this);manage.setText("Gerenciar");manage.setAllCaps(false);
   manage.setTextColor(fg);manage.setBackground(shape(Color.rgb(43,43,43),16,stroke));
   controls.addView(manage,new LinearLayout.LayoutParams((int)(getResources().getDisplayMetrics().density*125),
       (int)(getResources().getDisplayMetrics().density*44)));
   manage.setOnClickListener(v->manageQueueItem(index));
  }
  TextView settings=label("CONFIGURAÇÃO DA AUTOMAÇÃO",11,accent,true);
  settings.setPadding(0,24,0,10);root.addView(settings);
  Button flow=action("⚙  Editar fluxo de nós",false);
  flow.setOnClickListener(v->startActivity(new Intent(this,WorkflowActivity.class)));root.addView(flow);
  TextView note=label("Identificação, imagem, logo, duração e produção são configuradas no fluxo. A publicação automática ainda depende da integração com o TikTok.",12,muted,false);
  note.setPadding(5,6,5,18);root.addView(note);
  if(history.length()>0){
   TextView historyTitle=label("Histórico de resolvidos",18,fg,true);historyTitle.setPadding(0,18,0,12);root.addView(historyTitle);
   for(int j=0;j<history.length();j++){
    String url=history.optString(j);JSONObject item=info(url);
    TextView entry=label("✓  "+field(item,"song","Música não identificada")+" — "+field(item,"artist","Artista não identificado")+"\n"+url,12,muted,false);
    entry.setPadding(18,15,18,15);entry.setBackground(shape(panel,16,stroke));
    LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=8;root.addView(entry,hp);
   }
  }
 }

}
