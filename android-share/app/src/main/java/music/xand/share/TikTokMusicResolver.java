package music.xand.share;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Best-effort lookup of public TikTok sound credits. No private API or third-party service. */
public final class TikTokMusicResolver {
  private TikTokMusicResolver() {}

  public static final class Result {
    public String canonical="", type="Link do TikTok", title="", artist="", soundHint="", status="unavailable";
    public String reason="Dados insuficientes para verificar o nome da música e do artista.";
    public String source="TikTok público", itemId="";
    public boolean verified(){return "identified".equals(status);}
  }

  private static boolean trusted(URL url) {
    String host=url.getHost().toLowerCase(Locale.ROOT);
    return "https".equalsIgnoreCase(url.getProtocol()) &&
      ("tiktok.com".equals(host)||host.endsWith(".tiktok.com"));
  }

  public static boolean isTrustedLink(String raw) {
    try{return trusted(new URL(raw));}catch(Exception ignored){return false;}
  }

  private static HttpURLConnection connect(URL url,String method) throws Exception {
    if(!trusted(url))throw new SecurityException("Domínio não autorizado.");
    HttpURLConnection c=(HttpURLConnection)url.openConnection();
    c.setRequestMethod(method);
    c.setInstanceFollowRedirects(false);
    c.setConnectTimeout(8500);c.setReadTimeout(8500);
    c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Safari/537.36");
    c.setRequestProperty("Accept","text/html,application/xhtml+xml");
    return c;
  }

  private static HttpURLConnection follow(URL url,String method) throws Exception {
    URL current=url;
    for(int i=0;i<6;i++){
      HttpURLConnection connection=connect(current,method);
      int status;
      try{status=connection.getResponseCode();}
      catch(Exception e){connection.disconnect();throw e;}
      if(status!=301&&status!=302&&status!=303&&status!=307&&status!=308)return connection;
      String location=connection.getHeaderField("Location");
      connection.disconnect();
      if(location==null||location.isEmpty())throw new IOException("Redirecionamento sem destino.");
      current=new URL(current,location);
      if(!trusted(current))throw new SecurityException("Redirecionamento fora do TikTok.");
    }
    throw new IOException("Limite de redirecionamentos do TikTok.");
  }

  private static String readPage(HttpURLConnection c) throws Exception {
    if(c.getResponseCode()<200||c.getResponseCode()>=300)return "";
    try(InputStream input=c.getInputStream();ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
      byte[] part=new byte[16384];int count;int max=1500000;
      while((count=input.read(part))!=-1){
        if(buffer.size()+count>max)return "";
        buffer.write(part,0,count);
      }
      return buffer.toString("UTF-8");
    }
  }

  private static String itemId(String url) {
    Matcher m=Pattern.compile("/(?:video|photo)/([0-9]{8,25})").matcher(url);
    return m.find()?m.group(1):"";
  }

  private static boolean originalSound(String title,boolean original) {
    String t=title.toLowerCase(Locale.ROOT).trim();
    return original || t.startsWith("original sound") || t.startsWith("som original") ||
      t.startsWith("original audio") || t.startsWith("áudio original") ||
      t.startsWith("оригинальный звук") || t.startsWith("sonido original") ||
      t.startsWith("son original") || t.startsWith("suono originale") ||
      t.startsWith("originalton") || t.startsWith("suara asli") ||
      t.startsWith("âm thanh gốc") || t.startsWith("オリジナル楽曲") ||
      t.startsWith("原声") || t.startsWith("原聲") || t.startsWith("오리지널 사운드") ||
      t.startsWith("الصوت الأصلي");
  }

  private static String safeText(String s){
    if(s==null)return "";
    s=s.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]"," ").trim();
    return s.length()>160?s.substring(0,160):s;
  }

  private static void applyMusic(JSONObject music,Result result) {
    if(music==null)return;
    String title=safeText(music.optString("title",""));
    String artist=safeText(music.optString("authorName",""));
    if(title.isEmpty())return;
    result.soundHint=title+(artist.isEmpty()?"":" — "+artist);
    if(originalSound(title,music.optBoolean("original",false))){
      result.status="original";
      result.reason="O TikTok identifica este áudio como som original. O criador do som pode não ser o artista da música.";
      return;
    }
    if(artist.isEmpty()){
      result.status="partial";
      result.reason="O título do som foi encontrado, mas o artista não pôde ser confirmado.";
      return;
    }
    result.title=title;result.artist=artist;result.status="identified";
    result.reason="Créditos de som encontrados nos metadados da publicação indicada.";
  }

  /** Accepts only music tied to the exact video/photo ID, never related recommendations. */
  private static boolean findMatchingItem(Object value,String targetId,Result result,int depth,int[] budget){
    if(value==null||depth>13||budget[0]++>12000)return false;
    if(value instanceof JSONObject){
      JSONObject obj=(JSONObject)value;
      if(targetId.equals(obj.optString("id")) && obj.optJSONObject("music")!=null){
        applyMusic(obj.optJSONObject("music"),result);
        return true;
      }
      Iterator<String> keys=obj.keys();
      while(keys.hasNext()){
        String key=keys.next();
        Object child=obj.opt(key);
        if((child instanceof JSONObject || child instanceof JSONArray) &&
            findMatchingItem(child,targetId,result,depth+1,budget))return true;
      }
    }else if(value instanceof JSONArray){
      JSONArray arr=(JSONArray)value;
      for(int i=0;i<arr.length()&&i<800;i++) {
        Object child=arr.opt(i);
        if((child instanceof JSONObject || child instanceof JSONArray) &&
           findMatchingItem(child,targetId,result,depth+1,budget))return true;
      }
    }
    return false;
  }

  private static void inspectHtml(String html,Result result) {
    if(html.isEmpty()||result.itemId.isEmpty())return;
    Pattern script=Pattern.compile("<script[^>]+id=[\\\"'](?:__UNIVERSAL_DATA_FOR_REHYDRATION__|SIGI_STATE|__NEXT_DATA__)[\\\"'][^>]*>(.*?)</script>",
      Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
    Matcher found=script.matcher(html);
    while(found.find()){
      try{
        JSONObject json=new JSONObject(found.group(1));
        if(findMatchingItem(json,result.itemId,result,0,new int[]{0}))return;
      }catch(Exception ignored){}
    }
  }

  public static Result lookup(String rawUrl){
    Result result=new Result();
    result.canonical=rawUrl;
    try{
      URL initial=new URL(rawUrl);
      if(!trusted(initial)){
        result.reason="Link fora de um domínio oficial do TikTok.";return result;
      }
      try{
        HttpURLConnection head=follow(initial,"HEAD");
        try{head.getResponseCode();URL redirect=head.getURL();
          if(trusted(redirect))result.canonical=redirect.toString();
        }finally{head.disconnect();}
      }catch(Exception ignored){}
      Uri canonicalUri=Uri.parse(result.canonical);
      String path=canonicalUri.getPath()==null?"":canonicalUri.getPath();
      if(path.contains("/photo/"))result.type="Publicação com fotos";
      else if(path.contains("/video/"))result.type="Vídeo";
      else if(path.contains("/music/"))result.type="Página de som";
      result.itemId=itemId(result.canonical);
      if("Página de som".equals(result.type)){
        result.status="partial";
        result.reason="Link de um som: abra a página para confirmar título e artista.";
        String slug=path.substring(path.indexOf("/music/")+7).replaceAll("-[0-9]{8,25}$","");
        result.soundHint=safeText(slug.replace('-',' '));
        return result;
      }
      HttpURLConnection page=follow(new URL(result.canonical),"GET");
      try{
        String html=readPage(page);
        URL redirected=page.getURL();
        if(trusted(redirected))result.canonical=redirected.toString();
        if(result.itemId.isEmpty())result.itemId=itemId(result.canonical);
        if(!result.itemId.isEmpty())inspectHtml(html,result);
      }finally{page.disconnect();}
    }catch(Exception e){
      result.reason="Os metadados públicos do TikTok não estão disponíveis para consulta.";
    }
    return result;
  }
}
