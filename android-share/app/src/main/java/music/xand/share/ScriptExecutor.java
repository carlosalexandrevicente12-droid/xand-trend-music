package music.xand.share;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

/** Allowlisted local script instructions. No shell, JavaScript eval or remote code execution. */
public final class ScriptExecutor {
  private ScriptExecutor(){}

  public static String validate(String body){
    if(body==null||body.length()>4000)return "Limite de 4.000 caracteres.";
    String[] lines=body.replace("\r","").split("\n",-1);
    int interactiveActions=0;
    if(lines.length>40)return "Limite de 40 linhas.";
    for(int i=0;i<lines.length;i++){
      String line=lines[i].trim();
      if(line.isEmpty()||line.startsWith("#"))continue;
      if(line.startsWith("AVISAR ")&&line.length()<=170&&line.substring(7).trim().length()>0)continue;
      if(line.equals("COPIAR_LINK")||line.equals("ABRIR_TIKTOK")||line.equals("MANTER_NA_FILA")||line.equals("COPIAR_LEGENDA"))continue;
      if(line.equals("GERAR_LEGENDA")||line.equals("BUSCAR_PINTEREST")||line.equals("PREPARAR_ARTE")||line.equals("CRIAR_ARTE_CHATGPT")){if(++interactiveActions>1)return "Use apenas uma ação visual por script.";continue;}
      return "Comando inválido na linha "+(i+1)+": "+(line.length()>30?line.substring(0,30):line);
    }
    return "OK";
  }

  public static boolean run(Activity activity,SharedPreferences prefs,String scriptId,String url){
    try{
      JSONArray scripts=new JSONObject(prefs.getString("scripts_v1","{}")).optJSONArray("scripts");
      if(scripts==null)return false;
      for(int i=0;i<scripts.length();i++){
        JSONObject script=scripts.optJSONObject(i);
        if(script==null||!scriptId.equals(script.optString("id")))continue;
        String body=script.optString("body","");
        if(!"OK".equals(validate(body)))return false;
        for(String raw:body.split("\n")){
          String line=raw.trim();
          if(line.startsWith("AVISAR "))Toast.makeText(activity,line.substring(7).trim(),Toast.LENGTH_LONG).show();
          else if(line.equals("COPIAR_LINK")){
            ((ClipboardManager)activity.getSystemService(Activity.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("TikTok",url));
          }else if(line.equals("ABRIR_TIKTOK")){
            try{Intent external=new Intent(Intent.ACTION_VIEW,Uri.parse(url));external.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity.startActivity(external);}catch(Exception error){Toast.makeText(activity,"Não foi possível abrir o TikTok.",Toast.LENGTH_SHORT).show();}
          }

          else if(activity instanceof MainActivity){
            MainActivity main=(MainActivity)activity;
            if(line.equals("COPIAR_LEGENDA"))new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(!main.needsMetadata(url))main.copyText("Legenda XAND",main.caption(url));});
            else if(line.equals("GERAR_LEGENDA"))new android.os.Handler(android.os.Looper.getMainLooper()).post(()->main.prepareCaption(url));
            else if(line.equals("BUSCAR_PINTEREST"))new android.os.Handler(android.os.Looper.getMainLooper()).post(()->main.pinterest(url));
            else if(line.equals("PREPARAR_ARTE"))new android.os.Handler(android.os.Looper.getMainLooper()).post(()->main.prepareArtwork(url));
            else if(line.equals("CRIAR_ARTE_CHATGPT"))new android.os.Handler(android.os.Looper.getMainLooper()).post(()->main.sendArtworkPromptToChatGPT(url,false));
          }
                    // MANTER_NA_FILA does not duplicate: MainActivity already registered the link.
        }
        prefs.edit().putString("script_last_run",scriptId+"|"+System.currentTimeMillis()).apply();
        return true;
      }
    }catch(Exception ignored){}
    return false;
  }
}
