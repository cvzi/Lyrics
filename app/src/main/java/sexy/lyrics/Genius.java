package sexy.lyrics;

import static java.lang.Integer.min;
import static sexy.lyrics.DatabaseOpenHelper.FIELD_ARTIST;
import static sexy.lyrics.DatabaseOpenHelper.FIELD_GENIUS_ID;
import static sexy.lyrics.DatabaseOpenHelper.FIELD_JSON;
import static sexy.lyrics.DatabaseOpenHelper.FIELD_TIMESTAMP;
import static sexy.lyrics.DatabaseOpenHelper.FIELD_TITLE;
import static sexy.lyrics.DatabaseOpenHelper.TABLE_LOCAL_TRACKS;
import static sexy.lyrics.DatabaseOpenHelper.TABLE_SONGS;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;

class Genius {
    private static final int REQUEST_TIMEOUT = 20000; // 0 == block indefinitely
    private final SQLiteDatabase database;
    private final DatabaseOpenHelper dbHelper;
    private final String clientAccessToken;

    Genius(Context context) throws Exception {
        dbHelper = new DatabaseOpenHelper(context);
        database = dbHelper.getWritableDatabase();

        ApplicationInfo ai = context.getPackageManager().getApplicationInfo(
                context.getPackageName(),
                PackageManager.GET_META_DATA);
        Bundle bundle = ai.metaData;
        clientAccessToken = bundle.getString(".geniusClientAccessToken");
    }

    /**
     * Extract lyrics from Genius.com HTML using the __PRELOADED_STATE__ JSON if present.
     * See https://github.com/cvzi/genius-downloader/blob/master/id3rapgenius.py
     *
     * @param html HTML content of the Genius lyrics page
     * @return Extracted lyrics or null if not found
     */
    public static String extractLyricsFromHtml(String html) {
        if (html == null || html.isEmpty()) return null;
        try {
            // Check for __PRELOADED_STATE__ JSON
            String marker = "__PRELOADED_STATE__ = JSON.parse('";
            int idx = html.indexOf(marker);
            if (idx != -1) {
                int start = idx + marker.length();
                int end = html.indexOf("');\n", start);
                if (end > start) {
                    String jsonStr = html.substring(start, end);
                    // Correct unescaping: first \\ -> \, then \" -> "
                    jsonStr = jsonStr.replace("\\\\", "\\");
                    jsonStr = jsonStr.replace("\\\"", "\"");
                    try {
                        JSONObject jdata = new JSONObject(jsonStr);
                        JSONObject body = jdata.getJSONObject("songPage")
                                .getJSONObject("lyricsData")
                                .getJSONObject("body");
                        StringBuilder lyricsBuilder = new StringBuilder();
                        parseJdata(body, lyricsBuilder);
                        String lyrics = lyricsBuilder.toString();
                        lyrics = lyrics.replaceAll("\r", "").replaceAll("\n{2,}", "\n").trim();

                        if (lyrics.isEmpty()) {
                            if (!jdata.getJSONObject("songPage")
                                    .getJSONObject("lyricsData").isNull("lyricsPlaceholderReason")) {
                                String reason = jdata.getJSONObject("songPage")
                                        .getJSONObject("lyricsData").
                                        getString("lyricsPlaceholderReason");
                                lyrics = "• No lyrics found. Reason: '" + reason + "' •\n\n\nTry 'View on genius.com' in the menu for more information.";
                            } else {
                                lyrics = "• Lyrics are empty •";
                            }
                        }
                        return lyrics;
                    } catch (JSONException e) {
                        // Parsing failed
                        Log.e("extractLyricsFromHtml", "JSONException: " + e);
                    }
                } else {
                    Log.e("extractLyricsFromHtml", "No end of __PRELOADED_STATE__");
                }
            } else {
                Log.e("extractLyricsFromHtml", "No __PRELOADED_STATE__");
            }
            Log.e("extractLyricsFromHtml", "No lyrics extracted");
            // No lyrics found
            return null;
        } catch (Exception e) {
            // Log error if needed
            Log.e("extractLyricsFromHtml", "Error: " + e);
            return null;
        }
    }

    /**
     * Recursively parse Genius.com lyrics JSON structure to extract text and line breaks.
     */
    private static void parseJdata(JSONObject obj, StringBuilder arr) {
        if (!obj.has("children")) return;
        JSONArray children = obj.optJSONArray("children");
        if (children == null) return;
        for (int i = 0; i < children.length(); i++) {
            Object child = children.opt(i);
            if (child instanceof String) {
                arr.append((String) child);
            } else if (child instanceof JSONObject childObj) {
                parseJdata(childObj, arr);
                if ("br".equals(childObj.optString("tag"))) {
                    arr.append("\n");
                }
            }
        }
    }

    /**
     * Download HTML from a given URL using HttpURLConnection.
     */
    public static String downloadLyricsHtml(String urlStr) {
        try {
            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                java.io.InputStream is = conn.getInputStream();
                java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                String html = s.hasNext() ? s.next() : "";
                is.close();
                return html;
            }
        } catch (Exception e) {
            Log.e("downloadLyricsHtml", e.toString());
        }
        return null;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    void close() {
        database.close();
        dbHelper.close();
    }

    Lyrics fromCache(String localArtist, String localTitle) {
        if (localArtist == null
                || localTitle == null
                || localArtist.isEmpty()
                || localTitle.isEmpty()) {
            return null;
        }

        String sql = "SELECT " + TABLE_SONGS + "." + FIELD_JSON + " "
                + "FROM " + TABLE_LOCAL_TRACKS + ", " + TABLE_SONGS + " "
                + "WHERE " + TABLE_LOCAL_TRACKS + "." + FIELD_GENIUS_ID
                + " = " + TABLE_SONGS + "." + FIELD_GENIUS_ID + " "
                + "AND " + TABLE_LOCAL_TRACKS + "." + FIELD_TITLE
                + " LIKE " + DatabaseUtils.sqlEscapeString(localTitle) + " "
                + "AND " + TABLE_LOCAL_TRACKS + "." + FIELD_ARTIST
                + " LIKE " + DatabaseUtils.sqlEscapeString(localArtist) + " "
                + "LIMIT 1";
        Cursor cursor = database.rawQuery(sql, null);
        cursor.moveToFirst();

        if (cursor.isAfterLast() || cursor.getCount() == 0) {
            return null;
        }


        JSONObject json = parseGeniusJson(cursor.getString(0));
        if (json == null) {
            return null;
        }
        GeniusSong song = parseGeniusSong(json);
        if (song == null) {
            return null;
        }
        cursor.close();

        Lyrics lyrics = new Lyrics();
        lyrics.setArtist(song.getArtist());
        lyrics.setTitle(song.getTitle());
        lyrics.setUrl(song.getUrl());
        lyrics.setStatus(true);
        lyrics.setLyrics(song.getPlainLyrics());

        return lyrics;
    }

    void deleteFromCache(String localArtist, String localTitle) {
        if (localArtist == null
                || localTitle == null
                || localArtist.isEmpty()
                || localTitle.isEmpty()) {
            return;
        }
        database.delete(TABLE_LOCAL_TRACKS,
                TABLE_LOCAL_TRACKS + "." + FIELD_TITLE + " LIKE ? "
                        + " AND " + TABLE_LOCAL_TRACKS + "." + FIELD_ARTIST + " LIKE ?",
                new String[]{localTitle, localArtist});
    }

    Lyrics fromWeb(GeniusLookUpResult geniusLookUpResult, String localArtist, String localTitle) {
        return fromWeb(Integer.toString(geniusLookUpResult.getId()), localArtist, localTitle);
    }

    Lyrics fromWeb(String geniusId, String localArtist, String localTitle) {
        final String TAG = "fromWeb()";
        URL url;
        try {
            url = new URL(
                    "https://api.genius.com/songs/" + geniusId + "?text_format=plain");
        } catch (MalformedURLException e) {
            Log.e(TAG, "Redirect???"); // TODO
            return null;
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setReadTimeout(REQUEST_TIMEOUT);
            connection.setRequestProperty("Authorization", "Bearer " + clientToken());

            if (!url.getHost().equals(connection.getURL().getHost())) {
                Log.e(TAG, "Redirect???"); // TODO
                return null;
            }

            try {
                InputStream in = new BufferedInputStream(connection.getInputStream());

                String result = readStream(in);

                JSONObject json = parseGeniusJson(result);
                if (json == null) {
                    return null;
                }
                GeniusSong song = parseGeniusSong(json);
                if (song == null) {
                    return null;
                }
                writeToCache(song, result, localArtist, localTitle);

                Lyrics lyrics = new Lyrics();
                lyrics.setId(song.getId());
                lyrics.setArtist(song.getArtist());
                lyrics.setTitle(song.getTitle());
                lyrics.setUrl(song.getUrl());
                lyrics.setStatus(true);
                lyrics.setLyrics(song.getPlainLyrics());

                return lyrics;
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
        return null;
    }

    /**
     * This API call seems to be no longer documented. It still works to some extend
     * but it requires relatively exact values. If the artist or song names are missing
     * words, this will likely fail.
     *
     * @param artist Name of artist, as close as possible to actual name
     * @param song   Title of song, as close as possible to actual title
     * @return Array of GeniusLookUpResult, empty if no results or errors occurred
     */
    GeniusLookUpResult[] lookUp(String artist, String song) {
        final String TAG = "lookUp()";
        JSONObject jsonObject;
        try {
            JSONObject queries = new JSONObject();
            queries.put("key", new JSONArray());
            queries.put("title", song);
            queries.put("artist", artist);
            jsonObject = new JSONObject();
            jsonObject.put("queries", queries);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException", e);
            return new GeniusLookUpResult[0];
        }
        String query = jsonObject.toString();
        //  {"queries":[{"key":{},"title":"QUERY_TITLE","artist":"QUERY_ARTIST"}]}
        try {
            URL url = new URL("https://api.genius.com/songs/lookup");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setReadTimeout(REQUEST_TIMEOUT);
            connection.setRequestProperty("Authorization", "Bearer " + clientToken());
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            Writer writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write(query);
            writer.flush();
            writer.close();

            if (!url.getHost().equals(connection.getURL().getHost())) {
                Log.e(TAG, "Redirect???"); // TODO
                return new GeniusLookUpResult[0];
            }

            try {
                InputStream in = new BufferedInputStream(connection.getInputStream());

                String result = readStream(in);

                JSONObject json = parseGeniusJson(result);
                if (json != null) {
                    return parseGeniusLookup(json);

                } else {
                    return new GeniusLookUpResult[0];
                }
            } finally {
                connection.disconnect();
            }


        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        return new GeniusLookUpResult[0];
    }

    /**
     * This is the current search API from the
     * <a href="https://docs.genius.com/#search-h2">API docs</a>
     * It gives much more results, than lookUp, but most of them are useless.
     *
     * @param artist Name of artist, as close as possible to actual name
     * @param song   Title of song, as close as possible to actual title
     * @return Array of GeniusLookUpResult, empty if no results or errors occurred
     */
    @SuppressWarnings("CharsetObjectCanBeUsed")
    GeniusLookUpResult[] search(String artist, String song) {
        final String TAG = "search()";
        try {
            String urlStr = "https://api.genius.com/search?q=" + URLEncoder.encode(artist + " " + song, "UTF-8");
            URL url = new URL(urlStr);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(REQUEST_TIMEOUT);
            connection.setRequestProperty("Authorization", "Bearer " + clientToken());

            if (!url.getHost().equals(connection.getURL().getHost())) {
                Log.e(TAG, "Redirect???"); // TODO
                return new GeniusLookUpResult[0];
            }
            try {
                InputStream in = new BufferedInputStream(connection.getInputStream());
                String result = readStream(in);
                JSONObject json = parseGeniusJson(result);
                if (json != null) {
                    return parseGeniusSearch(json, artist, song);
                } else {
                    return new GeniusLookUpResult[0];
                }
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        return new GeniusLookUpResult[0];
    }

    private JSONObject parseGeniusJson(String jsonString) {
        try {
            JSONObject root = new JSONObject(jsonString);

            if (root.getJSONObject("meta").getInt("status") != 200) {
                Log.e("parseGeniusJson1", "parseGeniusJson: status = "
                        + root.getJSONObject("meta").getInt("status"));
                return null;
            }
            return root.getJSONObject("response");
        } catch (JSONException e) {
            Log.e("parseGeniusJson2", e.toString());
            return null;
        }
    }

    private GeniusLookUpResult[] parseGeniusLookup(JSONObject response) {
        try {
            JSONArray hits = response.getJSONArray("hits");
            if (hits.length() == 0) {
                return new GeniusLookUpResult[0];
            }
            ArrayList<GeniusLookUpResult> results = new ArrayList<>();

            for (int i = 0; i < min(hits.length(), 1000); i++) {
                try {
                    JSONObject hit = hits.getJSONObject(i);
                    JSONObject song = hit.getJSONObject("song");

                    GeniusLookUpResult result = new GeniusLookUpResult();
                    result.setId(song.getInt("id"));
                    result.setTitle(song.getString("title"));
                    result.setUrl(song.getString("url"));
                    result.setThumbnail(song.getString("header_image_thumbnail_url"));
                    result.setArtist(song.getJSONObject("primary_artist").getString("name"));

                    results.add(result);
                } catch (JSONException e) {
                    Log.e("parseGeniusLookup1", e.toString());
                }
            }
            return results.toArray(new GeniusLookUpResult[0]);
        } catch (JSONException e) {
            Log.e("parseGeniusLookup2", e.toString());
            return new GeniusLookUpResult[0];
        }
    }

    private GeniusLookUpResult[] parseGeniusSearch(JSONObject response, String artist, String song) {
        try {
            JSONArray hits = response.getJSONArray("hits");
            if (hits.length() == 0) {
                return new GeniusLookUpResult[0];
            }
            ArrayList<GeniusLookUpResult> results = new ArrayList<>();

            for (int i = 0; i < min(hits.length(), 1000); i++) {
                try {
                    JSONObject hit = hits.getJSONObject(i);
                    if (!hit.getString("type").equals("song")) {
                        continue;
                    }
                    JSONObject hitResult = hit.getJSONObject("result");

                    GeniusLookUpResult result = new GeniusLookUpResult();
                    result.setId(hitResult.getInt("id"));
                    result.setTitle(hitResult.getString("title"));
                    result.setUrl(hitResult.getString("url"));
                    result.setThumbnail(hitResult.getString("header_image_thumbnail_url"));
                    result.setArtist(hitResult.getJSONObject("primary_artist").getString("name"));

                    results.add(result);
                } catch (JSONException e) {
                    Log.e("parseGeniusSearch1", e.toString());
                }
            }

            GeniusLookUpResult[] arr = results.toArray(new GeniusLookUpResult[0]);

            if (results.size() > 3) {
                final String artistFinal = artist.toLowerCase().trim();
                final String songFinal = song.toLowerCase().trim();

                // Sort results
                Arrays.sort(arr, (a, b) -> {
                    int scoreA = 0;
                    int scoreB = 0;
                    String artistA = a.artist.toLowerCase().trim();
                    String songA = a.title.toLowerCase();
                    String artistB = b.artist.toLowerCase().trim();
                    String songB = b.title.toLowerCase();

                    if (artistA.equals(artistFinal)) {
                        scoreA += 3;
                    } else if (artistA.startsWith(artistFinal)) {
                        scoreA += 2;
                    } else if (artistFinal.startsWith(artistA)) {
                        scoreA += 2;
                    } else if (artistA.contains(artistFinal)) {
                        scoreA += 1;
                    } else if (artistFinal.contains(artistA)) {
                        scoreA += 1;
                    }
                    if (songA.equals(songFinal)) {
                        scoreA += 3;
                    } else if (songA.startsWith(songFinal)) {
                        scoreA += 2;
                    } else if (songFinal.startsWith(songA)) {
                        scoreA += 2;
                    } else if (songA.contains(songFinal)) {
                        scoreA += 1;
                    } else if (songFinal.contains(songA)) {
                        scoreA += 1;
                    }

                    if (artistB.equals(artistFinal)) {
                        scoreB += 3;
                    } else if (artistB.startsWith(artistFinal)) {
                        scoreB += 2;
                    } else if (artistFinal.startsWith(artistB)) {
                        scoreB += 2;
                    } else if (artistB.contains(artistFinal)) {
                        scoreB += 1;
                    } else if (artistFinal.contains(artistB)) {
                        scoreB += 1;
                    }
                    if (songB.equals(songFinal)) {
                        scoreB += 3;
                    } else if (songB.startsWith(songFinal)) {
                        scoreB += 2;
                    } else if (songFinal.startsWith(songB)) {
                        scoreB += 2;
                    } else if (songB.contains(songFinal)) {
                        scoreB += 1;
                    } else if (songFinal.contains(songB)) {
                        scoreB += 1;
                    }
                    return Integer.compare(scoreB, scoreA);
                });
            }

            return arr;
        } catch (JSONException e) {
            Log.e("parseGeniusSearch2", e.toString());
            return new GeniusLookUpResult[0];
        }
    }

    private GeniusSong parseGeniusSong(JSONObject response) {
        try {
            JSONObject song = response.getJSONObject("song");

            GeniusSong result = new GeniusSong();

            result.setId(song.getInt("id"));
            result.setTitle(song.getString("title"));
            result.setUrl(song.getString("url"));
            result.setThumbnail(song.getString("header_image_thumbnail_url"));
            result.setArtist(song.getJSONObject("primary_artist").getString("name"));
            if (song.has("lyrics")) {
                result.setPlainLyrics(song.getJSONObject("lyrics").getString("plain"));
            } else {
                result.setPlainLyrics(null);
            }

            return result;
        } catch (JSONException e) {
            Log.e("parseGeniusSong", e.toString());
            return null;
        }
    }

    private String clientToken() {
        return clientAccessToken;
    }

    private String readStream(InputStream is) {
        // http://stackoverflow.com/a/5713929
        try {
            if (is != null) {
                Writer writer = new StringWriter();

                char[] buffer = new char[1024];
                try (is) {
                    Reader reader = new BufferedReader(
                            new InputStreamReader(is, StandardCharsets.UTF_8));
                    int count;
                    while ((count = reader.read(buffer)) != -1) {
                        writer.write(buffer, 0, count);
                    }
                }
                return writer.toString();
            } else {
                return "";
            }
        } catch (IOException e) {
            return "";
        }
    }

    private void writeToCache(Lyrics result) {
        GeniusSong song = new GeniusSong() {{
            setId(result.getId());
            setTitle(result.getTitle());
            setArtist(result.getArtist());
            setUrl(result.getUrl());
            setPlainLyrics(result.getLyrics());
        }};

        // This is a scraped result, not from the API, so create a fake API result
        String jsonData = "{" +
                "\"meta\":{\"status\":200}," +
                "\"response\":{\"song\":{" +
                "\"primary_artist\":{\"name\":\"" + escapeJson(song.getArtist()) + "\"}," +
                "\"primary_artist_names\":\"" + escapeJson(song.getArtist()) + "\"," +
                "\"url\":\"" + escapeJson(song.getUrl()) + "\"," +
                "\"title\":\"" + escapeJson(song.getTitle()) + "\"," +
                "\"id\":" + song.getId() + "," +
                "\"header_image_thumbnail_url\":\"\"," +
                "\"lyrics\":{\"plain\":\"" + escapeJson(song.getPlainLyrics()) + "\"}" +
                "}}}";

        writeToCache(song, jsonData, result.getArtist(), result.getTitle());
    }

    void writeToCache(GeniusSong song,
                      String jsonData,
                      String localArtist,
                      String localTitle) {
        long tsLong = System.currentTimeMillis() / 1000;
        String timestamp = String.valueOf(tsLong);

        ContentValues values = new ContentValues();
        values.put(FIELD_GENIUS_ID, song.getId());
        values.put(FIELD_TIMESTAMP, timestamp);
        values.put(FIELD_TITLE, song.getTitle());
        values.put(FIELD_ARTIST, song.getArtist());
        values.put(FIELD_JSON, jsonData);

        database.insertWithOnConflict(TABLE_SONGS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE);

        values = new ContentValues();
        values.put(FIELD_TITLE, localTitle);
        values.put(FIELD_ARTIST, localArtist);
        values.put(FIELD_GENIUS_ID, song.getId());

        // This is important if there were multiple results,
        // and the user reloaded to choose another result
        database.delete(TABLE_LOCAL_TRACKS, FIELD_TITLE + "=? AND "
                        + FIELD_ARTIST + "=?",
                new String[]{localTitle, localArtist});

        database.insert(TABLE_LOCAL_TRACKS, null, values);
    }

    /**
     * Asynchronously download and extract lyrics from a Genius.com URL.
     * Calls the callback with the updated Lyrics object.
     */
    public void fetchLyricsFromWebAsync(final Lyrics result, final Callback<Lyrics> callback) {
        try (java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.execute(() -> {
                Log.v("fetchLyricsFromWebAsync", "Downloading lyrics from " + result.getUrl());
                String html = downloadLyricsHtml(result.getUrl());
                String lyrics = extractLyricsFromHtml(html);
                if (lyrics != null && !lyrics.isEmpty()) {
                    result.setLyrics(lyrics);
                    // Build a JSON string similar to the Genius API response
                    writeToCache(result);
                } else {
                    Log.e("fetchLyricsFromWebAsync", "No lyrics found in HTML");
                }
                // Post result to main thread
                new Handler(Looper.getMainLooper()).post(() -> callback.onResult(result));
            });
        } catch (Exception e) {
            Log.e("fetchLyricsFromWebAsync", e.toString());
            result.setLyrics(e.toString());
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(result));
        }
    }

    /**
     * Simple callback interface for async results.
     */
    public interface Callback<T> {
        void onResult(T value);
    }

    @SuppressWarnings("unused")
    public static class GeniusLookUpResult {
        private int id;
        private String url;
        private String artist;
        private String title;
        private String thumbnail;

        int getId() {
            return id;
        }

        void setId(int id) {
            this.id = id;
        }

        String getUrl() {
            return url;
        }

        void setUrl(String url) {
            this.url = url;
        }

        String getArtist() {
            return artist;
        }

        void setArtist(String artist) {
            this.artist = artist;
        }

        String getTitle() {
            return title;
        }

        void setTitle(String title) {
            this.title = title;
        }

        public String getThumbnail() {
            return thumbnail;
        }

        void setThumbnail(String thumbnail) {
            this.thumbnail = thumbnail;
        }
    }


    @SuppressWarnings("unused")
    static class GeniusSong extends GeniusLookUpResult {

        private int timestamp;
        private String lyricsPlain;

        public int getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(int timestamp) {
            this.timestamp = timestamp;
        }

        String getPlainLyrics() {
            return lyricsPlain;
        }

        void setPlainLyrics(String lyricsPlain) {
            this.lyricsPlain = lyricsPlain;
        }
    }


}
