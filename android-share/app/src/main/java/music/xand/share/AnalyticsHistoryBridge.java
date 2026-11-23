package music.xand.share;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Somente histórico de bloqueio. Nunca cria posts, rascunhos, fotos ou vídeos. */
public final class AnalyticsHistoryBridge {
    public static final int MAX_JSON_CHARS = 1024 * 1024;
    private AnalyticsHistoryBridge() { }

    private static String shortString(JSONObject row, String key, int max) throws JSONException {
        String value = row.optString(key, "").trim();
        if (value.length() > max) throw new JSONException("Campo muito longo: " + key);
        return value;
    }

    private static String soundId(JSONObject row) throws JSONException {
        String value = shortString(row, "soundId", 40);
        if (!value.isEmpty() && !value.matches("[0-9]{1,40}")) throw new JSONException("ID de som inválido");
        return value;
    }

    public static JSONObject validate(String raw) throws JSONException {
        if (raw == null || raw.length() > MAX_JSON_CHARS) throw new JSONException("Arquivo acima do limite de 1 MB");
        JSONObject source = new JSONObject(raw);
        if (!"xand-music-analytics".equals(source.optString("app"))
            || !"motorola-history".equals(source.optString("type"))
            || source.optInt("version") != 1
            || !"local_browser".equals(source.optString("source"))
            || !"partial".equals(source.optString("coverage"))) {
            throw new JSONException("Arquivo não corresponde ao histórico do Motorola v1");
        }
        JSONArray input = source.optJSONArray("used");
        if (input == null || input.length() > 10000) throw new JSONException("Lista de histórico inválida");
        JSONArray cleaned = new JSONArray();
        for (int i = 0; i < input.length(); i++) {
            JSONObject row = input.optJSONObject(i);
            if (row == null) throw new JSONException("Registro de histórico inválido");
            String status = shortString(row, "status", 8);
            if (!"used".equals(status) && !"possible".equals(status)) throw new JSONException("Estado musical inválido");
            String id = soundId(row), title = shortString(row, "title", 180), artist = shortString(row, "artist", 180);
            String postId = shortString(row, "postId", 150);
            if (id.isEmpty() && (title.isEmpty() || artist.isEmpty())) throw new JSONException("Música sem identificação verificável");
            Object rawViews = row.opt("postViews");
            Long views = null;
            if (rawViews != null && rawViews != JSONObject.NULL) {
                if (!(rawViews instanceof Number) || !rawViews.toString().matches("[0-9]{1,18}")) throw new JSONException("Visualizações inválidas");
                views = ((Number) rawViews).longValue();
            }
            JSONObject safe = new JSONObject();
            safe.put("soundId", id); safe.put("title", title); safe.put("artist", artist);
            safe.put("postId", postId); safe.put("postViews", views == null ? JSONObject.NULL : views);
            safe.put("status", status); cleaned.put(safe);
        }
        JSONObject result = new JSONObject();
        result.put("app", "xand-music-analytics"); result.put("type", "motorola-history");
        result.put("version", 1); result.put("coverage", "partial"); result.put("source", "local_browser");
        result.put("used", cleaned);
        return result;
    }

    public static JSONObject merge(String current, String incoming) throws JSONException {
        JSONObject next = validate(incoming);
        JSONArray merged = new JSONArray();
        Set<String> known = new HashSet<>();
        if (current != null && !current.isEmpty()) append(merged, known, validate(current).getJSONArray("used"));
        append(merged, known, next.getJSONArray("used"));
        if (merged.length() > 10000) throw new JSONException("Histórico acumulado excede 10 mil entradas");
        next.put("used", merged);
        if (next.toString().length() > MAX_JSON_CHARS) throw new JSONException("Histórico acumulado excede 1 MB");
        return next;
    }

    private static void append(JSONArray target, Set<String> known, JSONArray input) throws JSONException {
        for (int i = 0; i < input.length(); i++) {
            JSONObject row = input.getJSONObject(i);
            String key = row.optString("soundId") + "\u0000" + row.optString("title").toLowerCase(java.util.Locale.ROOT)
                + "\u0000" + row.optString("artist").toLowerCase(java.util.Locale.ROOT)
                + "\u0000" + row.optString("postId") + "\u0000" + row.optString("status");
            if (known.add(key)) target.put(row);
        }
    }

    public static List<MusicSelectionPolicy.PublishedSong> blockedSongs(String stored) throws JSONException {
        List<MusicSelectionPolicy.PublishedSong> result = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return result;
        JSONArray rows = validate(stored).getJSONArray("used");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject x = rows.optJSONObject(i);
            if (x == null) continue;
            result.add(new MusicSelectionPolicy.PublishedSong(x.optString("postId"), x.optString("soundId"),
                x.optString("title"), x.optString("artist"), x.isNull("postViews") ? null : x.optLong("postViews")));
        }
        return result;
    }
}
