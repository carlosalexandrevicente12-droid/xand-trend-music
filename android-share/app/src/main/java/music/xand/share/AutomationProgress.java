package music.xand.share;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** On-screen progress for observable stages only. Never estimates encoding time. */
final class AutomationProgress {
  private static int dp(Activity a,float v){return Math.round(a.getResources().getDisplayMetrics().density*v);}
  private static GradientDrawable shape(Activity a,int fill){
    GradientDrawable d=new GradientDrawable();
    d.setColor(fill);d.setCornerRadius(dp(a,14));d.setStroke(dp(a,1),0xffff8500);return d;
  }
  private static TextView label(Activity a,String value,int sp,int color){
    TextView text=new TextView(a);text.setText(value);text.setTextSize(sp);
    text.setTextColor(color);return text;
  }
  static View wrap(Activity a,SharedPreferences prefs,View content){
    FrameLayout frame=new FrameLayout(a);
    frame.addView(content,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout expanded=new LinearLayout(a);expanded.setOrientation(1);
    expanded.setPadding(dp(a,14),dp(a,10),dp(a,14),dp(a,12));
    expanded.setBackground(shape(a,0xf7191919));expanded.setElevation(dp(a,12));
    LinearLayout row=new LinearLayout(a);row.setOrientation(0);row.setGravity(Gravity.CENTER_VERTICAL);
    TextView title=label(a,"XAND • AUTOMAÇÃO",13,0xffff8500);
    title.setTypeface(null,1);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
    Button hide=new Button(a);hide.setText("Ocultar");hide.setAllCaps(false);
    hide.setTextColor(Color.WHITE);hide.setTextSize(12);
    hide.setMinHeight(dp(a,48));hide.setBackground(shape(a,0xff303030));
    row.addView(hide,new LinearLayout.LayoutParams(dp(a,100),dp(a,48)));
    expanded.addView(row);
    String message="Fila vazia";int completed=0;int total=4;
    try{
      if(MainActivity.PHOTO_ONLY_CHANNEL){
        JSONArray queue=new JSONArray(prefs.getString("queue","[]"));
        JSONObject all=new JSONObject(prefs.getString("metadata","{}"));
        String target="";
        for(int i=0;i<queue.length();i++){
          String url=queue.optString(i);
          JSONObject item=all.optJSONObject(url);
          if(item==null||!"user_approved".equals(item.optString("photo_review_status",""))||
             (item.optString("caption_auto","").isEmpty()&&item.optString("caption_user","").isEmpty())){target=url;break;}
        }
        if(target.isEmpty()&&queue.length()>0)target=queue.optString(0);
        if(!target.isEmpty()){
          JSONObject item=all.optJSONObject(target);if(item==null)item=new JSONObject();
          completed=1;
          boolean credits=!item.optString("song","").trim().isEmpty()&&!item.optString("artist","").trim().isEmpty()&&
            ("identified".equals(item.optString("recognition_status"))||prefs.getBoolean("review_confirmed_"+target,false));
          boolean photoApproved="user_approved".equals(item.optString("photo_review_status",""))&&
            !item.optString("generated_cover_uri","").isEmpty();
          boolean captionReady=!item.optString("caption_auto","").isEmpty()||!item.optString("caption_user","").isEmpty();
          if(credits)completed++;
          if(photoApproved)completed++;
          if(captionReady)completed++;
          String song=item.optString("song","Música não identificada");
          if(song.trim().isEmpty())song="Música não identificada";
          if(song.length()>34)song=song.substring(0,34)+"…";
          if(!credits)message="AGUARDANDO créditos • "+song;
          else if(!photoApproved)message="AGUARDANDO foto aprovada • "+song;
          else if(!captionReady)message="AGUARDANDO legenda • "+song;
          else message="PRONTO PARA RASCUNHO • "+song;
        }
      }else{
      JSONArray queue=new JSONArray(prefs.getString("queue","[]"));
      JSONObject all=new JSONObject(prefs.getString("metadata","{}"));
      String target="";
      for(int i=0;i<queue.length();i++){
        String url=queue.optString(i);JSONObject item=all.optJSONObject(url);
        if(item==null || (item.optString("video_uri","").isEmpty() &&
             item.optString("editor_video_uri","").isEmpty())){target=url;break;}
      }
      if(target.isEmpty()&&queue.length()>0)target=queue.optString(0);
      if(!target.isEmpty()){
        JSONObject item=all.optJSONObject(target);if(item==null)item=new JSONObject();
        completed=1;
        boolean credits=(!item.optString("song","").trim().isEmpty()&&!item.optString("artist","").trim().isEmpty())&&
          ("identified".equals(item.optString("recognition_status"))||
           prefs.getBoolean("review_confirmed_"+target,false));
        if(credits)completed++;
        if(!item.optString("generated_cover_uri","").isEmpty()||!item.optString("image_uri","").isEmpty())completed++;
        boolean exported=!item.optString("video_uri","").isEmpty()||
          !item.optString("editor_video_uri","").isEmpty();
        if(exported)completed++;
        String song=item.optString("song","Música não identificada");
        if(song.trim().isEmpty())song="Música não identificada";
        if(song.length()>34)song=song.substring(0,34)+"…";
        if(!item.optString("video_export_issue","").isEmpty())message="ERRO no MP4 • "+song;
        else if(!item.optString("generation_issue","").isEmpty()&&completed<=2)
          message="AGUARDANDO arte • "+song;
        else if(prefs.getBoolean("video_exporting_"+target,false))message="PROCESSANDO MP4 • "+song;
        else if(prefs.getBoolean("image_generating_"+target,false))message="PROCESSANDO imagem • "+song;
        else if("waiting_local_network".equals(item.optString("editor_job_status")))message="ESPERA: editor fora da rede • "+song;
        else if("waiting_editor_session".equals(item.optString("editor_job_status")))message="ESPERA: editor no Chrome • "+song;
        else if(exported)message="CONCLUÍDO: MP4 preparado • "+song;
        else if(credits)message="AGUARDANDO criação ou exportação • "+song;
        else message="AGUARDANDO créditos • "+song;
      }
      }
    }catch(Exception ignored){message="Dados da fila indisponíveis";}
    final int done=completed;
    TextView status=label(a,message,12,Color.WHITE);
    status.setPadding(0,dp(a,4),0,dp(a,6));expanded.addView(status);
    ProgressBar progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);
    progress.setMax(total);progress.setProgress(done);
    progress.setProgressTintList(ColorStateList.valueOf(0xffff8500));
    progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xff494949));
    expanded.addView(progress,new LinearLayout.LayoutParams(-1,dp(a,6)));
    TextView count=label(a,MainActivity.PHOTO_ONLY_CHANNEL?done+" de "+total+" etapas de preparação • rascunho a confirmar no TikTok":done+" de "+total+" etapas comprovadas • percentual refere-se às etapas, não ao tempo",11,0xffcccccc);
    count.setPadding(0,dp(a,8),0,0);expanded.addView(count);
    Button show=new Button(a);show.setText("Mostrar progresso");
    show.setAllCaps(false);show.setTextColor(Color.WHITE);show.setTextSize(13);
    show.setMinHeight(dp(a,48));show.setBackground(shape(a,0xff272727));
    FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
    position.setMargins(dp(a,12),dp(a,6),dp(a,12),dp(a,14));
    frame.addView(expanded,position);
    FrameLayout.LayoutParams minimised=new FrameLayout.LayoutParams(dp(a,182),dp(a,50),Gravity.BOTTOM|Gravity.END);
    minimised.setMargins(dp(a,12),dp(a,8),dp(a,12),dp(a,14));
    frame.addView(show,minimised);
    Runnable sync=()->{
      boolean visible=prefs.getBoolean("xand_progress_visible",true);
      expanded.setVisibility(visible?View.VISIBLE:View.GONE);
      show.setVisibility(visible?View.GONE:View.VISIBLE);
    };
    hide.setOnClickListener(v->{prefs.edit().putBoolean("xand_progress_visible",false).apply();sync.run();});
    show.setOnClickListener(v->{prefs.edit().putBoolean("xand_progress_visible",true).apply();sync.run();});
    sync.run();return frame;
  }
}
