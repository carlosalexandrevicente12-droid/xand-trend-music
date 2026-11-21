package music.xand.share;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Renders one original-logo-preserving, 1080 x 1920 PNG in memory. */
public final class ArtworkRenderer {
  private ArtworkRenderer() {}
  private static final int WIDTH = 1080;
  private static final int HEIGHT = 1920;
  private static final int ORANGE = Color.rgb(255,133,0);
  private static final int WHITE = Color.rgb(245,245,245);

  public static void export(Context context, Uri backgroundUri, Uri logoUri,
      String title, String artist, String genre, String logoPosition, OutputStream stream) throws IOException {
    export(context,backgroundUri,logoUri,title,artist,genre,logoPosition,-1,-1,-1,stream);
  }

  public static void export(Context context, Uri backgroundUri, Uri logoUri,
      String title, String artist, String genre, String logoPosition,
      int logoXPercent, int logoYPercent, int logoWidthPercent, OutputStream stream) throws IOException {
    if (title == null || title.trim().isEmpty() || artist == null || artist.trim().isEmpty())
      throw new IOException("Informe o nome da música e do artista antes de exportar.");
    if (backgroundUri == null || logoUri == null)
      throw new IOException("Escolha a imagem de fundo e o arquivo original da logo.");
    Bitmap output = null;
    Bitmap background = null;
    Bitmap logo = null;
    try {
      background = decodeImage(context, backgroundUri);
      logo = decodeImage(context, logoUri);
      output = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(output);
      canvas.drawColor(Color.rgb(9,9,9));
      drawFit(canvas, background, new RectF(0,0,WIDTH,HEIGHT));
      drawFooter(canvas, title.trim(), artist.trim(), genre == null ? "" : genre.trim(), logoYPercent>=50);
      drawLogo(canvas, logo, logoPosition, logoXPercent, logoYPercent, logoWidthPercent);
      if (!output.compress(Bitmap.CompressFormat.PNG, 100, stream))
        throw new IOException("Falha ao codificar a arte em PNG.");
      stream.flush();
    } catch (OutOfMemoryError oom) {
      throw new IOException("Imagem grande demais para a memória do celular. Escolha uma imagem menor.",oom);
    } finally {
      if (background != null) background.recycle();
      if (logo != null) logo.recycle();
      if (output != null) output.recycle();
    }
  }

  private static Bitmap decodeImage(Context context, Uri uri) throws IOException {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    try (InputStream input = context.getContentResolver().openInputStream(uri)) {
      if (input == null) throw new IOException("Não foi possível ler uma das imagens selecionadas.");
      BitmapFactory.decodeStream(input, null, bounds);
    } catch (SecurityException ex) {
      throw new IOException("Acesso à imagem expirou. Escolha novamente na galeria.",ex);
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
      throw new IOException("Formato de imagem incompatível. Escolha JPG, PNG ou WebP.");
    int sample = 1;
    while ((long)Math.max(bounds.outWidth,bounds.outHeight)/sample > 2048) sample *= 2;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sample;
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    try (InputStream input = context.getContentResolver().openInputStream(uri)) {
      if (input == null) throw new IOException("A imagem selecionada não está mais disponível.");
      Bitmap bitmap = BitmapFactory.decodeStream(input,null,options);
      if (bitmap == null) throw new IOException("Não foi possível decodificar a imagem selecionada.");
      return bitmap;
    } catch (SecurityException ex) {
      throw new IOException("Sem permissão para ler a imagem. Escolha-a novamente.",ex);
    }
  }

  private static RectF fitRect(Bitmap bitmap, RectF box) {
    float scale = Math.min(box.width()/bitmap.getWidth(),box.height()/bitmap.getHeight());
    float width = bitmap.getWidth()*scale;
    float height = bitmap.getHeight()*scale;
    float left = box.centerX()-width/2;
    float top = box.centerY()-height/2;
    return new RectF(left,top,left+width,top+height);
  }

  private static void drawFit(Canvas canvas, Bitmap bitmap, RectF box) {
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    canvas.drawBitmap(bitmap,null,fitRect(bitmap,box),paint);
  }

  private static StaticLayout layout(String value, int size, int color, boolean bold) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    paint.setTextSize(size);
    paint.setColor(color);
    paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
    paint.setShadowLayer(2,1,2,Color.BLACK);
    return StaticLayout.Builder.obtain(value,0,value.length(),paint,920)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build();
  }

  private static void drawLayout(Canvas canvas, StaticLayout text, int x, int y) {
    canvas.save(); canvas.translate(x,y); text.draw(canvas); canvas.restore();
  }

  private static void drawFooter(Canvas canvas, String title, String artist, String genre, boolean bottomLogo) throws IOException {
    StaticLayout titleText=null,artistText=null,genreText=null;
    int size=90;
    int totalHeight=0;
    do {
      titleText=layout(title,size,WHITE,true);
      artistText=layout(artist,Math.max(32,(int)(size*.67f)),ORANGE,true);
      genreText=genre.isEmpty()?null:layout(genre.toUpperCase(java.util.Locale.ROOT),Math.max(26,(int)(size*.42f)),WHITE,false);
      totalHeight=titleText.getHeight()+artistText.getHeight()+(genreText==null?0:genreText.getHeight())+88;
      if(totalHeight<=535)break;
      size-=3;
    } while(size>=33);
    if(totalHeight>535)
      throw new IOException("Nome da música ou artista longo demais para uma arte sem cortes.");

    int top=bottomLogo?Math.max(500,1050-totalHeight):HEIGHT-112-totalHeight;
    Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
    scrim.setShader(new LinearGradient(0,Math.max(bottomLogo?350:850,top-150),0,bottomLogo?1170:HEIGHT,
      new int[]{Color.TRANSPARENT,Color.argb(220,0,0,0),Color.argb(247,0,0,0)},
      new float[]{0f,.40f,1f},Shader.TileMode.CLAMP));
    canvas.drawRect(0,Math.max(bottomLogo?350:850,top-150),WIDTH,bottomLogo?1170:HEIGHT,scrim);

    Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
    line.setColor(ORANGE);
    canvas.drawRoundRect(new RectF(86,top-24,266,top-15),5,5,line);
    int y=top+16;
    if(genreText!=null){drawLayout(canvas,genreText,86,y);y+=genreText.getHeight()+20;}
    drawLayout(canvas,titleText,86,y);
    y+=titleText.getHeight()+28;
    drawLayout(canvas,artistText,86,y);
    TextPaint note=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    note.setTextSize(25);note.setColor(Color.rgb(184,184,184));note.setTypeface(Typeface.DEFAULT_BOLD);
    canvas.drawText("XAND MUSIC  •  MUSIC ANALYTICS",86,bottomLogo?1130:1860,note);
  }

  /** Trims only transparent margins; every visible logo pixel is retained. */
  private static Rect visibleLogoBounds(Bitmap logo) throws IOException {
    int w=logo.getWidth(),h=logo.getHeight();
    int minX=w,minY=h,maxX=-1,maxY=-1;
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
      if((logo.getPixel(x,y)>>>24)>2){
        if(x<minX)minX=x;if(x>maxX)maxX=x;
        if(y<minY)minY=y;if(y>maxY)maxY=y;
      }
    }
    if(maxX<minX)throw new IOException("A logo selecionada não contém pixels visíveis.");
    int pad=Math.max(4,Math.min(w,h)/250);
    return new Rect(Math.max(0,minX-pad),Math.max(0,minY-pad),
      Math.min(w,maxX+1+pad),Math.min(h,maxY+1+pad));
  }

  /** Generates an in-memory preview without touching the original image file. */
  public static Bitmap loadLogoPreview(Context context,Uri uri) throws IOException {
    Bitmap original=decodeImage(context,uri);
    Bitmap preview=null;
    try {
      Rect area=visibleLogoBounds(original);
      preview=Bitmap.createBitmap(original,area.left,area.top,area.width(),area.height());
      return preview;
    } finally {if(preview!=original)original.recycle();}
  }

  /** Uses percentages of the final 1080x1920 canvas, without cropping visible logo pixels. */
  private static void drawLogo(Canvas canvas, Bitmap logo, String position,
      int centerXPercent,int centerYPercent,int widthPercent) throws IOException {
    Rect src=visibleLogoBounds(logo);
    RectF fitted;
    if(centerXPercent>=0&&centerYPercent>=0&&widthPercent>=0){
      float desiredWidth=WIDTH*Math.max(15,Math.min(80,widthPercent))/100f;
      float scale=Math.min(desiredWidth/src.width(),HEIGHT*.48f/src.height());
      float width=src.width()*scale,height=src.height()*scale;
      float x=WIDTH*Math.max(0,Math.min(100,centerXPercent))/100f;
      float y=HEIGHT*Math.max(0,Math.min(100,centerYPercent))/100f;
      float left=Math.max(18,Math.min(WIDTH-width-18,x-width/2f));
      float top=Math.max(18,Math.min(HEIGHT-height-18,y-height/2f));
      fitted=new RectF(left,top,left+width,top+height);
    }else{
      boolean top=position==null || position.startsWith("Superior");
      boolean centered=position!=null && position.endsWith("centro");
      float left=centered?330:WIDTH-86-420;
      float y=top?85:1020;
      RectF space=new RectF(left,y,left+420,y+225);
      float scale=Math.min(space.width()/src.width(),space.height()/src.height());
      float width=src.width()*scale,height=src.height()*scale;
      fitted=new RectF(space.centerX()-width/2,space.centerY()-height/2,
        space.centerX()+width/2,space.centerY()+height/2);
    }
    Paint back=new Paint(Paint.ANTI_ALIAS_FLAG);
    back.setColor(Color.argb(145,0,0,0));
    canvas.drawRoundRect(new RectF(fitted.left-20,fitted.top-14,fitted.right+20,fitted.bottom+14),16,16,back);
    Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    canvas.drawBitmap(logo,src,fitted,paint);
  }


  /** Creates a branded, original geometric background locally: no remote image or API. */
  private static void drawOriginalBackdrop(Canvas canvas,long seed,String desiredStyle){
    java.util.Random rng=new java.util.Random(seed);
    int style;
    if("orbitas".equals(desiredStyle))style=0;
    else if("ondas".equals(desiredStyle))style=1;
    else if("prismas".equals(desiredStyle))style=2;
    else if("grade".equals(desiredStyle))style=3;
    else style=(int)Math.floorMod(seed,4L);
    int tint=Color.rgb(255,105+rng.nextInt(65),rng.nextInt(28));
    Paint base=new Paint(Paint.ANTI_ALIAS_FLAG);
    base.setShader(new LinearGradient(120,0,970,HEIGHT,
      new int[]{Color.rgb(5,5,9),Color.rgb(30,12,14),Color.rgb(4,4,7)},
      new float[]{0f,.48f,1f},Shader.TileMode.CLAMP));
    canvas.drawRect(0,0,WIDTH,HEIGHT,base);
    Paint shape=new Paint(Paint.ANTI_ALIAS_FLAG);
    if(style==0){
      float cx=350+rng.nextInt(420),cy=590+rng.nextInt(260);
      shape.setStyle(Paint.Style.STROKE);
      for(int i=0;i<18;i++){
        float radius=96+i*(30+rng.nextInt(6));
        shape.setStrokeWidth(2+(i%3)*2);
        shape.setColor(Color.argb(42+i%5*17,255,125+i%4*21,32));
        canvas.drawCircle(cx,cy,radius,shape);
      }
      shape.setStyle(Paint.Style.FILL);
      shape.setColor(Color.argb(120,255,128,0));
      canvas.drawCircle(cx,cy,95,shape);
      for(int i=0;i<70;i++){
        shape.setColor(Color.argb(45+rng.nextInt(100),255,143,30));
        canvas.drawCircle(rng.nextInt(WIDTH),rng.nextInt(1400),1+rng.nextInt(5),shape);
      }
    }else if(style==1){
      shape.setStyle(Paint.Style.STROKE);
      for(int w=0;w<32;w++){
        android.graphics.Path path=new android.graphics.Path();
        float offset=w*24f,phase=rng.nextFloat()*6.28f;
        for(int x=-80;x<WIDTH+80;x+=18){
          float y=230+offset+(float)Math.sin(x/145f+phase+w*.26f)*(70+w*1.4f);
          if(x==-80)path.moveTo(x,y);else path.lineTo(x,y);
        }
        shape.setColor(Color.argb(36+(w%5)*14,255,110+(w*4)%100,20));
        shape.setStrokeWidth(2+(w%4));canvas.drawPath(path,shape);
      }
    }else if(style==2){
      for(int i=0;i<55;i++){
        float x=rng.nextInt(WIDTH),y=rng.nextInt(1450);
        float size=60+rng.nextInt(310);
        android.graphics.Path tri=new android.graphics.Path();
        tri.moveTo(x,y);tri.lineTo(x+size*.72f,y+size*.35f);
        tri.lineTo(x-size*.31f,y+size);tri.close();
        shape.setStyle(i%3==0?Paint.Style.STROKE:Paint.Style.FILL);
        shape.setStrokeWidth(3);
        shape.setColor(Color.argb(i%3==0?130:22+rng.nextInt(52),255,105+rng.nextInt(90),0));
        canvas.drawPath(tri,shape);
      }
    }else{
      shape.setStyle(Paint.Style.STROKE);
      for(int i=-22;i<50;i++){
        int x=i*64;
        shape.setColor(Color.argb(36+(Math.abs(i)%4)*16,255,130,10));
        shape.setStrokeWidth(i%4==0?4:2);
        canvas.drawLine(x,-100,x+560,1600,shape);
        canvas.drawLine(-100,i*64,1200,i*64+350,shape);
      }
      shape.setStyle(Paint.Style.FILL);
      for(int i=0;i<9;i++){
        shape.setColor(Color.argb(35+rng.nextInt(70),255,130+rng.nextInt(70),0));
        float x=rng.nextInt(WIDTH),y=rng.nextInt(1300);
        canvas.drawRoundRect(new RectF(x,y,x+30+rng.nextInt(170),y+12+rng.nextInt(90)),8,8,shape);
      }
    }
    shape.setStyle(Paint.Style.FILL);
    shape.setShader(new LinearGradient(0,0,0,HEIGHT,
      new int[]{Color.argb(20,0,0,0),Color.TRANSPARENT,Color.argb(200,0,0,0)},
      null,Shader.TileMode.CLAMP));
    canvas.drawRect(0,0,WIDTH,HEIGHT,shape);
  }

  /** Generates a finished 9:16 cover using the complete official logo. */
  public static void exportGenerated(Context context,Uri logoUri,String title,String artist,
      String genre,String style,long seed,int logoXPercent,int logoYPercent,int logoWidthPercent,
      OutputStream stream) throws IOException {
    if(title==null||title.trim().isEmpty()||artist==null||artist.trim().isEmpty())
      throw new IOException("Créditos da música não confirmados.");
    if(logoUri==null)throw new IOException("Registre a logo original para gerar uma capa.");
    Bitmap bitmap=null,logo=null;
    try{
      logo=decodeImage(context,logoUri);
      bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
      Canvas canvas=new Canvas(bitmap);
      drawOriginalBackdrop(canvas,seed,style);
      drawFooter(canvas,title.trim(),artist.trim(),genre==null?"":genre.trim(), logoYPercent>=50);
      drawLogo(canvas,logo,"Superior direita",logoXPercent,logoYPercent,logoWidthPercent);
      if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))
        throw new IOException("Não foi possível gerar a arte PNG.");
      stream.flush();
    }catch(OutOfMemoryError oom){
      throw new IOException("Memória insuficiente para gerar a arte.",oom);
    }finally{
      if(logo!=null)logo.recycle();
      if(bitmap!=null)bitmap.recycle();
    }
  }

}
