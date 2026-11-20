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
    if(lines.length>40)return "Limite de 40 linhas.";
    for(int i=0;i<lines.length;i++){
      String line=lines[i].trim();
      if(line.isEmpty()||line.startsWith("#"))continue;
      if(line.startsWith("AVISAR ")&&line.length()<=170&&line.substring(7).trim().length()>0)continue;
      if(line.equals("COPIAR_LINK")||line.equals("ABRIR_TIKTOK")||line.equals("MANTER_NA_FILA"))continue;
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
            try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception error){Toast.makeText(activity,"Não foi possível abrir o TikTok.",Toast.LENGTH_SHORT).show();}
          }
          // MANTER_NA_FILA does not duplicate: MainActivity already registered the link.
        }
        return true;
      }
    }catch(Exception ignored){}
    return false;
  }
}
