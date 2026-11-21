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

  static void ensureWorkflow(SharedPreferences prefs){
    try{
      JSONObject root=new JSONObject(prefs.getString("workflows_v1","{}"));
      JSONArray flows=root.optJSONArray("flows");
      if(flows==null)return;
      boolean changed=false;
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
          image.put("image_source","generated_local");changed=true;
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
}
