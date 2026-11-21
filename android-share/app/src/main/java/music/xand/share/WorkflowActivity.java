package music.xand.share;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.content.Intent;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

public class WorkflowActivity extends Activity {
  WebView editor;
  SharedPreferences prefs;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs=getSharedPreferences("xand_queue",MODE_PRIVATE);
    CreativeAutomation.ensureWorkflow(prefs);
    editor=new WebView(this);
    editor.getSettings().setJavaScriptEnabled(true);
    editor.getSettings().setDomStorageEnabled(true);
    editor.setBackgroundColor(0xff090909);
    editor.setWebViewClient(new WebViewClient());
    editor.setWebChromeClient(new WebChromeClient());
    editor.addJavascriptInterface(new Bridge(),"Xand");
    editor.loadUrl("file:///android_asset/flows.html");
    setContentView(AutomationProgress.wrap(this,prefs,editor));
  }
  final class Bridge {
    @JavascriptInterface public String scripts(){return prefs.getString("scripts_v1","{\"version\":1,\"scripts\":[]}");}
    @JavascriptInterface public void openScripts(){runOnUiThread(()->startActivity(new Intent(WorkflowActivity.this,ScriptActivity.class)));}
    @JavascriptInterface public void selectLogo(){runOnUiThread(()->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("image/*");pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(pick,81);});}
    @JavascriptInterface public String continuePending(){
      try{
        JSONArray queue=new JSONArray(prefs.getString("queue","[]"));
        for(int i=0;i<queue.length();i++){
          String url=queue.optString(i);String stage="";
          if(prefs.getBoolean("review_pending_"+url,false))stage="review";
          else if(prefs.getBoolean("image_pending_"+url,false))stage="image";
          else if(prefs.getBoolean("logo_pending_"+url,false))stage="logo";
          else if(prefs.getBoolean("duration_pending_"+url,false))stage="duration";
          if(stage.isEmpty()){
            JSONObject all=new JSONObject(prefs.getString("metadata","{}"));
            JSONObject item=all.optJSONObject(url);
            if(item==null||item.optString("song").trim().isEmpty()||
               item.optString("artist").trim().isEmpty())stage="review";
          }
          if(!stage.isEmpty()){
            final String target=url,step=stage;
            runOnUiThread(()->{Intent intent=new Intent(WorkflowActivity.this,MainActivity.class);intent.putExtra("xand_pending_url",target);intent.putExtra("xand_pending_stage",step);startActivity(intent);});
            return "Abrindo a próxima etapa da fila.";
          }
        }
        return "Nenhuma etapa manual pendente. Aguarde a próxima música.";
      }catch(Exception e){return "Não foi possível consultar a fila.";}
    }
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
  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
    super.onActivityResult(requestCode,resultCode,data);
    if(requestCode==81&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
      android.net.Uri uri=data.getData();
      try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException denied){Toast.makeText(this,"Não foi possível manter acesso à logo.",Toast.LENGTH_LONG).show();return;}
      prefs.edit().putString("logo_uri",uri.toString()).apply();
      Toast.makeText(this,"Logo original registrada, sem alterações.",Toast.LENGTH_LONG).show();
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
