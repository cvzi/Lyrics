package sexy.lyrics;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static sexy.lyrics.DatabaseOpenHelper.FIELD_ARTIST;
import static sexy.lyrics.DatabaseOpenHelper.FIELD_GENIUS_ID;
import static sexy.lyrics.DatabaseOpenHelper.FIELD_JSON;
import static sexy.lyrics.DatabaseOpenHelper.FIELD_TIMESTAMP;
import static sexy.lyrics.DatabaseOpenHelper.FIELD_TITLE;
import static sexy.lyrics.DatabaseOpenHelper.TABLE_LOCAL_TRACKS;
import static sexy.lyrics.DatabaseOpenHelper.TABLE_SONGS;

class Genius {
    private static final int REQUEST_TIMEOUT = 20000; // 0 == block indefinitely
    private final SQLiteDatabase database;
    private final DatabaseOpenHelper dbHelper;
    private final String clientAccessToken;

    Genius(Context context) throws Throwable {
        dbHelper = new DatabaseOpenHelper(context);
        database = dbHelper.getWritableDatabase();

        ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
        Bundle bundle = ai.metaData;
        clientAccessToken = bundle.getString(".geniusClientAccessToken");
    }

    void close() {
        database.close();
        dbHelper.close();
    }

    Lyrics fromCache(String localArtist, String localTitle) {
        if (localArtist == null || localTitle == null || localArtist.length() == 0 || localTitle.length() == 0) {
            return null;
        }

        String sql = "SELECT " + TABLE_SONGS + "." + FIELD_JSON + " " +
                "FROM " + TABLE_LOCAL_TRACKS + ", " + TABLE_SONGS + " " +
                "WHERE " + TABLE_LOCAL_TRACKS + "." + FIELD_GENIUS_ID + " = " + TABLE_SONGS + "." + FIELD_GENIUS_ID + " " +
                "AND " + TABLE_LOCAL_TRACKS + "." + FIELD_TITLE + " LIKE " + DatabaseUtils.sqlEscapeString(localTitle) + " " +
                "AND " + TABLE_LOCAL_TRACKS + "." + FIELD_ARTIST + " LIKE " + DatabaseUtils.sqlEscapeString(localArtist) + " " +
                "LIMIT 1";
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
        if (localArtist == null || localTitle == null || localArtist.length() == 0 || localTitle.length() == 0) {
            return;
        }
        database.delete(TABLE_LOCAL_TRACKS, TABLE_LOCAL_TRACKS + "." + FIELD_TITLE + " LIKE ? AND " + TABLE_LOCAL_TRACKS + "." + FIELD_ARTIST + " LIKE ?", new String[]{localTitle, localArtist});
    }

    Lyrics fromWeb(GeniusLookUpResult geniusLookUpResult, String localArtist, String localTitle) {
        return fromWeb("" + geniusLookUpResult.getId(), localArtist, localTitle);
    }

    Lyrics fromWeb(String geniusId, String localArtist, String localTitle) {

        try {
            URL url = new URL("https://api.genius.com/songs/" + geniusId + "?text_format=plain");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setReadTimeout(REQUEST_TIMEOUT);
            connection.setRequestProperty("Authorization", "Bearer " + clientToken());

            if (!url.getHost().equals(connection.getURL().getHost())) {
                Log.e("fromWeb", "Redirect???"); // TODO
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
                lyrics.setArtist(song.getArtist());
                lyrics.setTitle(song.getTitle());
                lyrics.setUrl(song.getUrl());
                lyrics.setStatus(true);
                lyrics.setLyrics(song.getPlainLyrics());

                return lyrics;
            } finally {
                connection.disconnect();
            }


        } catch (Exception e) {
            Log.e("Genius", e.toString());
        }

        return null;
    }

    GeniusLookUpResult[] lookUp(String artist, String song) {
        JSONObject jsonObject;
        try {
            JSONObject queries = new JSONObject();
            queries.put("key", new JSONArray());
            queries.put("title", song);
            queries.put("artist", artist);
            jsonObject = new JSONObject();
            jsonObject.put("queries", queries);
        } catch (JSONException e) {
            Log.e("lookUp()", "JSONException", e);
            return null;
        }
        String query = jsonObject.toString();  //  {"queries":[{"key":{},"title":"QUERY_TITLE","artist":"QUERY_ARTIST"}]}
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
                Log.e("lookUp()", "Redirect???"); // TODO
                return null;
            }

            try {
                InputStream in = new BufferedInputStream(connection.getInputStream());

                String result = readStream(in);

                JSONObject json = parseGeniusJson(result);
                if (json != null) {
                    return parseGeniusLookup(json);

                } else {
                    return null;
                }
            } finally {
                connection.disconnect();
            }


        } catch (Exception e) {
            Log.e("lookUp()", e.toString());
        }

        return null;
    }


    private JSONObject parseGeniusJson(String jsonString) {
        try {
            JSONObject root = new JSONObject(jsonString);

            if (root.getJSONObject("meta").getInt("status") != 200) {
                Log.e("parseGeniusJson", "parseGeniusJson: status = " + root.getJSONObject("meta").getInt("status"));
                return null;
            }
            return root.getJSONObject("response");
        } catch (JSONException e) {
            Log.e("parseGeniusJson", e.toString());
            return null;
        }
    }

    private GeniusLookUpResult[] parseGeniusLookup(JSONObject response) {
        try {
            JSONArray hits = response.getJSONArray("hits");
            if (hits.length() == 0) {
                return null;
            }
            ArrayList<GeniusLookUpResult> results = new ArrayList<>();

            for (int i = 0; i < hits.length(); i++) {
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
                    Log.e("Genius Hits", e.toString());
                }
            }
            return results.toArray(new GeniusLookUpResult[0]);
        } catch (JSONException e) {
            Log.e("parseGeniusLookup", e.toString());
            return null;
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
            result.setPlainLyrics(song.getJSONObject("lyrics").getString("plain"));

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
                try {
                    Reader reader = new BufferedReader(
                            new InputStreamReader(is, StandardCharsets.UTF_8));
                    int n;
                    while ((n = reader.read(buffer)) != -1) {
                        writer.write(buffer, 0, n);
                    }
                } finally {
                    is.close();
                }
                return writer.toString();
            } else {
                return "";
            }
        } catch (IOException e) {
            return "";
        }
    }


    @SuppressWarnings("SpellCheckingInspection")
    private void writeToCache(GeniusSong song, String jsonData, String localArtist, String localTitle) {
        Long tsLong = System.currentTimeMillis() / 1000;
        String timestamp = tsLong.toString();

        ContentValues values = new ContentValues();
        values.put(FIELD_GENIUS_ID, song.getId());
        values.put(FIELD_TIMESTAMP, timestamp);
        values.put(FIELD_TITLE, song.getTitle());
        values.put(FIELD_ARTIST, song.getArtist());
        values.put(FIELD_JSON, jsonData);

        database.insertWithOnConflict(TABLE_SONGS, null, values, SQLiteDatabase.CONFLICT_REPLACE);

        values = new ContentValues();
        values.put(FIELD_TITLE, localTitle);
        values.put(FIELD_ARTIST, localArtist);
        values.put(FIELD_GENIUS_ID, song.getId());

        // This is important if there were multiple results, and the user reloaded to choose another result
        database.delete(TABLE_LOCAL_TRACKS, FIELD_TITLE + "=? AND " + FIELD_ARTIST + "=?", new String[]{localTitle, localArtist});

        database.insert(TABLE_LOCAL_TRACKS, null, values);
    }

    @SuppressWarnings("unused")
    public class GeniusLookUpResult {
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
    class GeniusSong extends GeniusLookUpResult {

        private int timestamp;
        private String lyrics_plain;

        public int getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(int timestamp) {
            this.timestamp = timestamp;
        }

        String getPlainLyrics() {
            return lyrics_plain;
        }

        void setPlainLyrics(String lyrics_plain) {
            this.lyrics_plain = lyrics_plain;
        }
    }


}


