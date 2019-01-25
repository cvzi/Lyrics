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
import java.util.ArrayList;

public class Genius {
    private static int REQUESTTIMEOUT = 20000; // 0 == block indefinitely
    private SQLiteDatabase database;
    private DatabaseOpenHelper dbHelper;
    private String clientAccessToken;

    public Genius(Context context) throws Throwable {
        dbHelper = new DatabaseOpenHelper(context);
        database = dbHelper.getWritableDatabase();

        ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
        Bundle bundle = ai.metaData;
        clientAccessToken = bundle.getString(".geniusClientAccessToken");
    }

    public void close() {
        database.close();
        dbHelper.close();
    }

    public Lyrics fromCache(String localArtist, String localTitle) {
        if (localArtist == null || localTitle == null || localArtist.length() == 0 || localTitle.length() == 0) {
            return null;
        }

        String sql = "SELECT songs.json " +
                "FROM localtracks, songs " +
                "WHERE localtracks.geniusid = songs.geniusid " +
                "AND localtracks.title LIKE " + DatabaseUtils.sqlEscapeString(localTitle) + " " +
                "AND localtracks.artist LIKE " + DatabaseUtils.sqlEscapeString(localArtist) + " " +
                "LIMIT 1";
        Cursor cursor = database.rawQuery(sql, null);
        cursor.moveToFirst();

        if (cursor.isAfterLast() || cursor.getCount() == 0) {
            return null;
        }



        JSONObject json = parseGeniusJson(cursor.getString(0));
        GeniusSong song = parseGeniusSong(json);
        cursor.close();

        Lyrics lyrics = new Lyrics();
        lyrics.setArtist(song.getArtist());
        lyrics.setTitle(song.getTitle());
        lyrics.setUrl(song.getUrl());
        lyrics.setStatus(true);
        lyrics.setLyrics(song.getPlainLyrics());

        return lyrics;
    }
    public Lyrics fromWeb(GeniusLookUpResult geniusLookUpResult, String localArtist, String localTitle) {
        return fromWeb("" + geniusLookUpResult.getId(), localArtist, localTitle);
    }
    public Lyrics fromWeb(String geniusid, String localArtist, String localTitle) {

        try {
            URL url = new URL("https://api.genius.com/songs/" + geniusid + "?text_format=plain");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setReadTimeout(REQUESTTIMEOUT);
            connection.setRequestProperty("Authorization", "Bearer " + clientToken());

            if (!url.getHost().equals(connection.getURL().getHost())) {
                Log.e("Genius", "Redirect???"); // TODO
                return null;
            }

            try {
                InputStream in = new BufferedInputStream(connection.getInputStream());

                String result = readStream(in);

                JSONObject json = parseGeniusJson(result);
                if (json != null) {
                    GeniusSong song = parseGeniusSong(json);
                    writeToCache(song, result, localArtist, localTitle);

                    Lyrics lyrics = new Lyrics();
                    lyrics.setArtist(song.getArtist());
                    lyrics.setTitle(song.getTitle());
                    lyrics.setUrl(song.getUrl());
                    lyrics.setStatus(true);
                    lyrics.setLyrics(song.getPlainLyrics());

                    return lyrics;
                } else {
                    return null;
                }
            } finally {
                connection.disconnect();
            }


        } catch (Exception e) {
            Log.e("Genius", e.toString());
        }

        return null;
    }

    public GeniusLookUpResult[] lookUp(String artist, String song) {
        String query = "{\"queries\":[{\"key\":{},\"title\":\"" + song + "\",\"artist\":\"" + artist + "\"}]}";

        try {
            URL url = new URL("https://api.genius.com/songs/lookup");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setReadTimeout(REQUESTTIMEOUT);
            connection.setRequestProperty("Authorization", "Bearer " + clientToken());
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            Writer writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write(query);
            writer.flush();
            writer.close();

            if (!url.getHost().equals(connection.getURL().getHost())) {
                Log.e("Genius", "Redirect???"); // TODO
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
            Log.e("Genius", e.toString());
        }

        return null;
    }


    private JSONObject parseGeniusJson(String jsonstring) {
        try {
            JSONObject root = new JSONObject(jsonstring);

            if (root.getJSONObject("meta").getInt("status") != 200) {
                Log.e("Genius", "parseGeniusJson: status = " + root.getJSONObject("meta").getInt("status"));
                return null;
            }
            return root.getJSONObject("response");
        } catch (JSONException e) {
            Log.e("Genius", e.toString());
            return null;
        }
    }

    private GeniusLookUpResult[] parseGeniusLookup(JSONObject response) {
        try {
            JSONArray hits = response.getJSONArray("hits");
            if (hits.length() == 0) {
                return null;
            }
            ArrayList<GeniusLookUpResult> results = new ArrayList<GeniusLookUpResult>();

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
            return results.toArray(new GeniusLookUpResult[results.size()]);
        } catch (JSONException e) {
            Log.e("Genius", e.toString());
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
            Log.e("Genius", e.toString());
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
                            new InputStreamReader(is, "UTF-8"));
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


    private void writeToCache(GeniusSong song, String jsondata, String localArtist, String localTitle) {
        Long tsLong = System.currentTimeMillis() / 1000;
        String timestamp = tsLong.toString();

        ContentValues values = new ContentValues();
        values.put("geniusid", song.getId());
        values.put("timestamp", timestamp);
        values.put("title", song.getTitle());
        values.put("artist", song.getArtist());
        values.put("json", jsondata);

        database.insertWithOnConflict(dbHelper.TABLE_SONGS, null, values, database.CONFLICT_REPLACE);

        values = new ContentValues();
        values.put("title", localTitle);
        values.put("artist", localArtist);
        values.put("geniusid", song.getId());

        database.delete(dbHelper.TABLE_LOCALTRACKS, "title=? AND artist=?",new String[] {localTitle, localArtist}); // This is important if there were multiple results, and the user reloaded to choose another result

        database.insert(dbHelper.TABLE_LOCALTRACKS, null, values);
    }


    public class GeniusLookUpResult {
        private int id;
        private String url;
        private String artist;
        private String title;
        private String thumbnail;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getThumbnail() {
            return thumbnail;
        }

        public void setThumbnail(String thumbnail) {
            this.thumbnail = thumbnail;
        }
    }


    public class GeniusSong extends GeniusLookUpResult {

        private int timestamp;
        private String lyrics_plain;

        public int getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(int timestamp) {
            this.timestamp = timestamp;
        }

        public String getPlainLyrics() {
            return lyrics_plain;
        }

        public void setPlainLyrics(String lyrics_plain) {
            this.lyrics_plain = lyrics_plain;
        }
    }


}


