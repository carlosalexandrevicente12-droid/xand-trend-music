package music.xand.share;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

public class WorkflowActivity extends Activity {
  WebView editor;
  SharedPreferences prefs;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs=getSharedPreferences("xand_queue",MODE_PRIVATE);
    editor=new WebView(this);
    editor.getSettings().setJavaScriptEnabled(true);
    editor.getSettings().setDomStorageEnabled(true);
    editor.setBackgroundColor(0xff090909);
    editor.setWebViewClient(new WebViewClient());
    editor.addJavascriptInterface(new Bridge(),"Xand");
    editor.loadUrl("file:///android_asset/flows.html");
    setContentView(editor);
  }
  final class Bridge {
    @JavascriptInterface public String load(){
      return prefs.getString("workflows_v1","{\"version\":1,\"flows\":[]}");
    }
    @JavascriptInterface public String save(String value){
      try{
        if(value==null||value.length()>100000)return "Arquivo muito grande";
        JSONObject data=new JSONObject(value);
        JSONArray flows=data.optJSONArray("flows");
        if(flows==null||flows.length()>20)return "Quantidade de fluxos inválida";
        for(int i=0;i<flows.length();i++){
          JSONObject flow=flows.optJSONObject(i);
          if(flow==null||flow.optJSONArray("nodes")==null||flow.optJSONArray("edges")==null||
             flow.optJSONArray("nodes").length()>30||flow.optJSONArray("edges").length()>60)return "Fluxo inválido";
        }
        prefs.edit().putString("workflows_v1",data.toString()).apply();
        return "OK";
      }catch(Exception exception){return "Falha ao validar os fluxos";}
    }
    @JavascriptInterface public String test(String value){
      try{
        JSONObject flow=new JSONObject(value);
        JSONArray nodes=flow.getJSONArray("nodes"),edges=flow.getJSONArray("edges");
        int triggers=0;
        for(int i=0;i<nodes.length();i++)if("trigger_share".equals(nodes.getJSONObject(i).optString("type")))triggers++;
        if(triggers==0)return "Inclua o gatilho Receber link TikTok.";
        if(edges.length()==0)return "Conecte o gatilho a pelo menos uma ação.";
        return "Fluxo válido. Ative e salve para executar ao compartilhar links.";
      }catch(Exception exception){return "Não foi possível testar o fluxo.";}
    }
  }
  @Override public void onBackPressed(){
    if(editor.canGoBack()){editor.goBack();return;}
    super.onBackPressed();
  }
  @Override public void onDestroy(){
    if(editor!=null){editor.removeJavascriptInterface("Xand");editor.destroy();}
    super.onDestroy();
  }
}
