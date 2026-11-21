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
      drawFooter(canvas, title.trim(), artist.trim(), genre == null ? "" : genre.trim());
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

  private static void drawFooter(Canvas canvas, String title, String artist, String genre) throws IOException {
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

    int top=HEIGHT-112-totalHeight;
    Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
    scrim.setShader(new LinearGradient(0,Math.max(850,top-150),0,HEIGHT,
      new int[]{Color.TRANSPARENT,Color.argb(220,0,0,0),Color.argb(247,0,0,0)},
      new float[]{0f,.40f,1f},Shader.TileMode.CLAMP));
    canvas.drawRect(0,Math.max(850,top-150),WIDTH,HEIGHT,scrim);

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
    canvas.drawText("XAND MUSIC  •  MUSIC ANALYTICS",86,1860,note);
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

}
