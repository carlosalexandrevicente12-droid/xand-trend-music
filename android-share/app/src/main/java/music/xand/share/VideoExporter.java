package music.xand.share;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Offline image-to-MP4 export. The complete cover (including original logo,
 * title and artist) is displayed for the entire configured duration.
 * TikTok audio is not downloaded or embedded without a licensed source.
 */
final class VideoExporter {
  interface Callback {
    void onSuccess(Uri videoUri);
    void onError(String reason);
  }

  private final Activity activity;
  private Transformer transformer;
  private File temporaryOutput;

  VideoExporter(Activity activity) {
    this.activity = activity;
  }

  void start(Uri coverUri, int durationSeconds, Callback callback) {
    if (durationSeconds < 60 || durationSeconds > 180) {
      callback.onError("Escolha uma duração entre 1 e 3 minutos.");
      return;
    }
    if (coverUri == null) {
      callback.onError("A capa ainda não está disponível.");
      return;
    }
    try {
      temporaryOutput = new File(activity.getCacheDir(),
          "xand_video_" + Long.toHexString(CreativeAutomation.seed(coverUri.toString())) + ".mp4");
      if (temporaryOutput.exists() && !temporaryOutput.delete()) {
        throw new IOException("Não foi possível preparar o arquivo temporário.");
      }

      MediaItem image = new MediaItem.Builder()
          .setUri(coverUri)
          .setImageDurationMs(durationSeconds * 1000L)
          .build();
      EditedMediaItem video = new EditedMediaItem.Builder(image)
          .setFrameRate(30)
          .build();

      transformer = new Transformer.Builder(activity.getApplicationContext())
          .setVideoMimeType(MimeTypes.VIDEO_H264)
          .addListener(new Transformer.Listener() {
            @Override public void onCompleted(Composition composition, ExportResult result) {
              new Thread(() -> {
                Uri output = null;
                String failure = null;
                try {
                  verifyDuration(temporaryOutput, durationSeconds);
                  output = saveToGallery(temporaryOutput);
                } catch (Exception error) {
                  failure = error.getMessage();
                }
                final Uri saved = output;
                final String reason = failure;
                activity.runOnUiThread(() -> {
                  cleanTemporaryFile();
                  if (saved != null) callback.onSuccess(saved);
                  else callback.onError(reason == null ? "Falha ao finalizar o MP4." : reason);
                });
              }, "xand-video-save").start();
            }

            @Override public void onError(
                Composition composition, ExportResult result, ExportException exception) {
              cleanTemporaryFile();
              String details = exception.getMessage();
              callback.onError(details == null ? "Falha ao codificar o MP4 neste aparelho." : details);
            }
          })
          .build();
      transformer.start(video, temporaryOutput.getAbsolutePath());
    } catch (Exception failure) {
      cleanTemporaryFile();
      callback.onError(failure.getMessage() == null
          ? "Não foi possível iniciar a exportação MP4." : failure.getMessage());
    }
  }

  private static void verifyDuration(File file, int expectedSeconds) throws IOException {
    if (file == null || !file.exists() || file.length() < 1024) {
      throw new IOException("O codificador não produziu um MP4 válido.");
    }
    MediaMetadataRetriever reader = new MediaMetadataRetriever();
    try {
      reader.setDataSource(file.getAbsolutePath());
      String value = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      if (value == null) throw new IOException("Não foi possível conferir a duração do vídeo.");
      long actualMs = Long.parseLong(value);
      if (Math.abs(actualMs - expectedSeconds * 1000L) > 2000L) {
        throw new IOException("O vídeo exportado tem duração diferente da configurada.");
      }
    } catch (RuntimeException error) {
      throw new IOException("O vídeo exportado não pôde ser validado.", error);
    } finally {
      reader.release();
    }
  }

  private Uri saveToGallery(File file) throws IOException {
    if (Build.VERSION.SDK_INT < 29) {
      throw new IOException("Exportação para a galeria requer Android 10 ou superior.");
    }
    ContentResolver resolver = activity.getContentResolver();
    ContentValues values = new ContentValues();
    values.put(MediaStore.Video.Media.DISPLAY_NAME,
        "XAND_VIDEO_" + System.currentTimeMillis() + ".mp4");
    values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
    values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/XAND MUSIC");
    values.put(MediaStore.Video.Media.IS_PENDING, 1);
    Uri target = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
    if (target == null) throw new IOException("A galeria não aceitou o MP4.");

    try {
      try (FileInputStream input = new FileInputStream(file);
           OutputStream output = resolver.openOutputStream(target, "w")) {
        if (output == null) throw new IOException("Não foi possível abrir o destino MP4.");
        byte[] buffer = new byte[65536];
        long bytes = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
          bytes += count;
          if (bytes > 300L * 1024 * 1024) {
            throw new IOException("O MP4 ultrapassou o limite de segurança de 300 MB.");
          }
          output.write(buffer, 0, count);
        }
        output.flush();
      }
      ContentValues complete = new ContentValues();
      complete.put(MediaStore.Video.Media.IS_PENDING, 0);
      resolver.update(target, complete, null, null);
      return target;
    } catch (Exception error) {
      try { resolver.delete(target, null, null); } catch (Exception ignored) {}
      if (error instanceof IOException) throw (IOException) error;
      throw new IOException("Não foi possível salvar o MP4.", error);
    }
  }

  private void cleanTemporaryFile() {
    if (temporaryOutput != null && temporaryOutput.exists()) {
      temporaryOutput.delete();
    }
    transformer = null;
  }
}
