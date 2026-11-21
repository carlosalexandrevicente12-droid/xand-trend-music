package music.xand.share;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Free on-device creative work. Never downloads unlicensed Pinterest imagery. */
final class CreativeAutomation {
  private CreativeAutomation(){}

  static String ensureChatScript(SharedPreferences prefs){
    final String id="xand_chatgpt_art_script_v1";
    try{
      JSONObject root=new JSONObject(prefs.getString("scripts_v1","{}"));
      JSONArray scripts=root.optJSONArray("scripts");
      if(scripts==null){scripts=new JSONArray();root.put("scripts",scripts);}
      for(int i=0;i<scripts.length();i++){
        JSONObject existing=scripts.optJSONObject(i);
        if(existing!=null&&id.equals(existing.optString("id")))return id;
      }
      if(prefs.getBoolean("chatgpt_script_seeded_v1",false)||scripts.length()>=30)return "";
      JSONObject script=new JSONObject();
      script.put("id",id);
      script.put("name","Criar arte no ChatGPT Chat");
      script.put("body","CRIAR_ARTE_CHATGPT");
      scripts.put(script);
      root.put("version",1);
      prefs.edit().putString("scripts_v1",root.toString())
        .putBoolean("chatgpt_script_seeded_v1",true).apply();
      return id;
    }catch(Exception ignored){return "";}
  }

  static void ensureWorkflow(SharedPreferences prefs){
    try{
      JSONObject root=new JSONObject(prefs.getString("workflows_v1","{}"));
      JSONArray flows=root.optJSONArray("flows");
      if(flows==null)return;
      boolean changed=false;
      String chatScript=ensureChatScript(prefs);
      for(int i=0;i<flows.length();i++){
        JSONObject flow=flows.optJSONObject(i);
        if(flow==null||!flow.optBoolean("enabled",false))continue;
        JSONArray nodes=flow.optJSONArray("nodes"),edges=flow.optJSONArray("edges");
        if(nodes==null||edges==null)continue;
        JSONObject review=null,image=null,duration=null,caption=null;
        boolean hasTrigger=false;
        for(int j=0;j<nodes.length();j++){
          JSONObject n=nodes.optJSONObject(j);if(n==null)continue;
          String type=n.optString("type");
          if("trigger_share".equals(type))hasTrigger=true;
          if("action_review".equals(type))review=n;
          if("action_image".equals(type))image=n;
          if("action_duration".equals(type))duration=n;
          if("action_caption".equals(type))caption=n;
        }
        if(!hasTrigger||image==null||duration==null)continue;
        if(review!=null&&!review.has("review_mode")){
          review.put("review_mode","auto_verified");changed=true;
        }
        if(!image.has("image_source")){
          image.put("image_source","chatgpt_chat");changed=true;
        }
        if(caption==null&&nodes.length()<30&&edges.length()<60){
          String id="n_creative_caption_"+Math.abs(flow.optString("id").hashCode());
          caption=new JSONObject();
          caption.put("id",id);caption.put("type","action_caption");
          caption.put("x",690);caption.put("y",495);
          caption.put("caption_mode","engagement");
          nodes.put(caption);
          JSONObject edge=new JSONObject();
          edge.put("from",duration.optString("id"));edge.put("to",id);
          edges.put(edge);
          changed=true;
        }
        if("chatgpt_chat".equals(image.optString("image_source"))&&caption!=null&&
           !chatScript.isEmpty()&&nodes.length()<30&&edges.length()<60){
          boolean hasChatNode=false;
          for(int j=0;j<nodes.length();j++){
            JSONObject n=nodes.optJSONObject(j);
            if(n!=null&&"action_script".equals(n.optString("type"))&&
               chatScript.equals(n.optString("scriptId")))hasChatNode=true;
          }
          if(!hasChatNode){
            String id="n_chat_script_"+Math.abs(flow.optString("id").hashCode());
            JSONObject scriptNode=new JSONObject();
            scriptNode.put("id",id);scriptNode.put("type","action_script");
            scriptNode.put("scriptId",chatScript);
            scriptNode.put("x",690);scriptNode.put("y",700);
            nodes.put(scriptNode);
            JSONObject edge=new JSONObject();
            edge.put("from",caption.optString("id"));edge.put("to",id);
            edges.put(edge);
            changed=true;
          }
        }
      }
      if(changed)prefs.edit().putString("workflows_v1",root.toString()).apply();
    }catch(Exception ignored){}
  }

  static String caption(String title,String artist,String genre,String mode,long seed){
    StringBuilder out=new StringBuilder("🎵 ").append(title.trim()).append(" — ").append(artist.trim());
    if(genre!=null&&!genre.trim().isEmpty())out.append("\n🎧 ").append(genre.trim());
    if("minimal".equals(mode)){
      out.append("\n\n#XANDMUSIC #TikTokMusic");
    }else if("info".equals(mode)){
      out.append("\n\nDescubra essa música e compartilhe com quem vai gostar.");
      out.append("\n\n#XANDMUSIC #MusicDiscovery");
    }else{
      String[] endings={
        "Qual é a sua parte favorita dessa música? 🔥",
        "Essa música combina com qual momento? 🎶",
        "Você já conhecia essa faixa? Conta aqui. 🎧",
        "Quem mais colocaria essa música no repeat? 🔊"
      };
      out.append("\n\n").append(endings[(int)Math.floorMod(seed,4L)]);
      out.append("\n\n#XANDMUSIC #TikTokMusic");
    }
    return out.toString();
  }

  static long seed(String url){
    try{
      byte[] hash=MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8"));
      long result=0;
      for(int i=0;i<8;i++)result=(result<<8)|(hash[i]&255L);
      return result;
    }catch(Exception ignored){return url.hashCode();}
  }

  static Uri saveCover(Context context,Uri logoUri,String title,String artist,String genre,
      String artStyle,long seed,int logoX,int logoY,int logoWidth) throws IOException {
    if(Build.VERSION.SDK_INT<29)
      throw new IOException("Geração automática na galeria requer Android 10 ou superior.");
    ContentResolver resolver=context.getContentResolver();
    ContentValues values=new ContentValues();
    values.put(MediaStore.Images.Media.DISPLAY_NAME,
      "XAND_"+Long.toHexString(seed)+"_"+System.currentTimeMillis()+".png");
    values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
    values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/XAND MUSIC");
    values.put(MediaStore.Images.Media.IS_PENDING,1);
    Uri target=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
    if(target==null)throw new IOException("Galeria indisponível para salvar a arte.");
    try{
      try(OutputStream output=resolver.openOutputStream(target,"w")){
        if(output==null)throw new IOException("Não foi possível abrir o arquivo de imagem.");
        ArtworkRenderer.exportGenerated(context,logoUri,title,artist,genre,artStyle,seed,
          logoX,logoY,logoWidth,output);
      }
      ContentValues ready=new ContentValues();
      ready.put(MediaStore.Images.Media.IS_PENDING,0);
      resolver.update(target,ready,null,null);
      return target;
    }catch(Exception failure){
      try{resolver.delete(target,null,null);}catch(Exception ignored){}
      throw failure instanceof IOException?(IOException)failure:new IOException("Falha ao salvar arte gerada.",failure);
    }
  }

  /** Stores a returned image in the gallery without editing or recompressing its pixels. */
  static Uri importSharedImage(Context context,Uri source) throws IOException {
    if(source==null)throw new IOException("Imagem não recebida.");
    if(Build.VERSION.SDK_INT<29)throw new IOException("Importação requer Android 10 ou superior.");
    ContentResolver resolver=context.getContentResolver();
    String mime=resolver.getType(source);
    if(mime==null||!mime.startsWith("image/"))mime="image/png";
    String suffix=mime.contains("jpeg")?".jpg":mime.contains("webp")?".webp":".png";
    ContentValues values=new ContentValues();
    values.put(MediaStore.Images.Media.DISPLAY_NAME,"XAND_CHAT_"+System.currentTimeMillis()+suffix);
    values.put(MediaStore.Images.Media.MIME_TYPE,mime);
    values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/XAND MUSIC");
    values.put(MediaStore.Images.Media.IS_PENDING,1);
    Uri target=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
    if(target==null)throw new IOException("Não foi possível salvar a imagem do ChatGPT.");
    try{
      try(java.io.InputStream in=resolver.openInputStream(source);
          OutputStream out=resolver.openOutputStream(target,"w")){
        if(in==null||out==null)throw new IOException("Falha ao ler a imagem compartilhada.");
        byte[] buffer=new byte[32768];int n;long bytes=0;
        while((n=in.read(buffer))!=-1){
          bytes+=n;if(bytes>40L*1024*1024)throw new IOException("Imagem maior que 40 MB.");
          out.write(buffer,0,n);
        }
        if(bytes==0)throw new IOException("Imagem recebida vazia.");
      }
      ContentValues complete=new ContentValues();
      complete.put(MediaStore.Images.Media.IS_PENDING,0);
      resolver.update(target,complete,null,null);
      return target;
    }catch(Exception failure){
      try{resolver.delete(target,null,null);}catch(Exception ignored){}
      throw failure instanceof IOException?(IOException)failure:new IOException("Falha ao importar imagem.",failure);
    }
  }

}
