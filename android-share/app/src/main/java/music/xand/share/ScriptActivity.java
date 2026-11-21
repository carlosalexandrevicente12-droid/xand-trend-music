package music.xand.share;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import org.json.JSONArray;
import org.json.JSONObject;

public class ScriptActivity extends Activity {
  WebView web;
  SharedPreferences prefs;
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    prefs=getSharedPreferences("xand_queue",MODE_PRIVATE);
    web=new WebView(this);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.setWebViewClient(new WebViewClient());
    web.setWebChromeClient(new WebChromeClient());
    web.setBackgroundColor(0xff090909);
    web.addJavascriptInterface(new Bridge(),"XandScripts");
    web.loadUrl("file:///android_asset/scripts.html");
    setContentView(web);
  }
  final class Bridge{
    @JavascriptInterface public String load(){
      return prefs.getString("scripts_v1","{\"version\":1,\"scripts\":[]}");
    }
    @JavascriptInterface public String check(String body){return ScriptExecutor.validate(body);}
    @JavascriptInterface public String save(String input){
      try{
        if(input==null||input.length()>100000)return "Dados muito grandes.";
        JSONObject value=new JSONObject(input);
        JSONArray scripts=value.optJSONArray("scripts");
        if(scripts==null||scripts.length()>30)return "Limite de 30 scripts.";
        for(int i=0;i<scripts.length();i++){
          JSONObject item=scripts.optJSONObject(i);
          if(item==null||item.optString("id").isEmpty()||item.optString("name").length()>80)return "Script inválido.";
          String result=ScriptExecutor.validate(item.optString("body",""));
          if(!"OK".equals(result))return item.optString("name")+": "+result;
        }
        prefs.edit().putString("scripts_v1",value.toString()).apply();
        return "OK";
      }catch(Exception error){return "Não foi possível salvar os scripts.";}
    }
  }
  @Override public void onDestroy(){
    if(web!=null){web.removeJavascriptInterface("XandScripts");web.destroy();}
    super.onDestroy();
  }
}
