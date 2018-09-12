package sexy.lyrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LyricsViewActivity extends AppCompatActivity {
    private IntentFilter iF;
    private Genius G;
    private String currentArtist = null;
    private String currentTitle = null;
    private float fontSize = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lyrics_view);

        try {
            G = new Genius(this);
        } catch (Throwable e) {
            getSupportActionBar().setSubtitle("No API Key");
            Log.e("Lyrics onCreate", "Could not find API Key");
            return;
        }

        String[] musicApps = new String[]{
                "com.amazon.mp3",
                "com.andrew.apollo",
                "com.android.music",
                "com.htc.music",
                "com.miui.player",
                "com.nullsoft.winamp",
                "com.samsung.sec.android.MusicPlayer",
                "com.sec.android.app.music",
                "com.sonyericsson.music",
                "com.spotify.music",
                "com.real.IMP",
                "com.rdio.android",
                "fm.last.android"
        };
        String[] musicActions = new String[]{
                "metachanged", "playstatechange", "playstatechanged", "playbackstatechanged", "queuechanged"
        };

        iF = new IntentFilter();

        for (String app : musicApps) {
            for (String action : musicActions) {
                iF.addAction(app + "." + action);
            }
        }

        registerReceiver(mReceiver, iF);


        fontSize = ((TextView) findViewById(R.id.result)).getTextSize();

        if(savedInstanceState != null) {
            currentArtist = savedInstanceState.getString("currentArtist", null);
            currentTitle = savedInstanceState.getString("currentTitle", null);
        }

        if(currentArtist != null && currentTitle != null) {
            loadLyrics(currentArtist, currentTitle);
        }

    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putString("currentTitle", currentTitle);
        savedInstanceState.putString("currentArtist", currentArtist);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                if (currentTitle != null && currentArtist != null) {
                    loadLyrics(currentArtist, currentTitle);
                }
                break;
            case R.id.action_forcereload:
                if (currentTitle != null && currentArtist != null) {
                    loadLyrics(currentArtist, currentTitle, false);
                }
                break;

            case R.id.action_exit:
                onPause();
                unregisterReceiver(mReceiver);
                G.close();
                G = null;
                this.finish();
                System.gc();
                System.exit(0);
                break;

            case R.id.action_makeFontBigger:
                fontSize *= 1.1f;
                ((TextView) findViewById(R.id.result)).setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
                break;

            case R.id.action_makeFontSmaller:
                fontSize *= 0.9f;
                ((TextView) findViewById(R.id.result)).setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
                break;


            default:
                break;
        }
        return true;
    }


    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        private String last;

        @Override
        public void onReceive(Context context, Intent intent) {
            String artist;
            String track;
            String album;

            String action = intent.getAction();

            try {
                if (action.equals("com.amazon.mp3.metachanged")) {
                    artist = intent.getStringExtra("com.amazon.mp3.artist");
                    track = intent.getStringExtra("com.amazon.mp3.track");
                    album = intent.getStringExtra("com.amazon.mp3.album");
                } else {
                    artist = intent.getStringExtra("artist");
                    track = intent.getStringExtra("track");
                    album = intent.getStringExtra("album");
                }
            } catch (Throwable e) {
                return;
            }
            if (last == null || !last.equals(artist + ":" + album + ":" + track)) {
                last = artist + ":" + album + ":" + track;
                loadLyrics(artist, track);
            }
        }
    };

    private void loadLyrics(String localArtist, String localTitle) {
        loadLyrics(localArtist, localTitle, true);
    }

    private void loadLyrics(String localArtist, String localTitle, boolean useCache) {
        currentArtist = localArtist;
        currentTitle = localTitle;

        hideSongSelector();

        getSupportActionBar().setTitle(R.string.loading);
        getSupportActionBar().setSubtitle(localTitle + " " + getResources().getString(R.string.by_artist) + " " + localArtist);
        String[] param;
        if (useCache) {
            param = new String[]{localArtist, localTitle};
        } else {
            param = new String[]{localArtist, localTitle, "nocache"};
        }
        new RequestTask().execute(param);
    }

    private void hideSongSelector() {
        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.songSelectorLayout);
        linearLayout.removeAllViews();
    }

    private void showSongSelector(Lyrics lyrics) {
        hideSongSelector();

        Genius.GeniusLookUpResult[] results = lyrics.getResults();

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.songSelectorLayout);

        getSupportActionBar().setTitle("");
        getSupportActionBar().setSubtitle(R.string.please_select_lyrics);
        ((TextView) findViewById(R.id.result)).setText("");

        for (final Genius.GeniusLookUpResult result : results) {
            Button v = new Button(this);
            v.setText(result.getArtist() + " - " + result.getTitle());
            v.setTextSize(22);
            v.setTextColor(ContextCompat.getColor(this, R.color.colorLinksText));
            v.setOnClickListener(new OnSelectSong(result, lyrics.getArtist(), lyrics.getTitle()));
            linearLayout.addView(v);
        }


    }

    class OnSelectSong implements View.OnClickListener {
        private Genius.GeniusLookUpResult result;
        private String originalArtist;
        private String originalTitle;

        public OnSelectSong(Genius.GeniusLookUpResult result, String originalArtist, String originalTitle) {
            this.result = result;
            this.originalArtist = originalArtist;
            this.originalTitle = originalTitle;
        }

        @Override
        public void onClick(View v) {

            hideSongSelector();

            getSupportActionBar().setTitle(R.string.loading);
            getSupportActionBar().setSubtitle(result.getTitle() + " " + getResources().getString(R.string.by_artist) + " " + result.getArtist());

            new RequestTask().execute(new String[]{this.originalArtist, this.originalTitle, "" + result.getId(), "byid"});
        }
    }

    class RequestTask extends AsyncTask<String, String, Lyrics> {

        @Override
        protected Lyrics doInBackground(String... songData) {
            // Reqest type by number of parameter:
            // Normal request:  (2) artist, song
            // Nocache request: (3) artist, song, "nocache"
            // By id request:   (4) artist, song, id, "byid"
            String artist = songData[0];
            String song = songData[1];
            if (songData.length < 4) {
                boolean usecache = true;
                if (songData.length > 2 && songData[2].equals("nocache")) {
                    usecache = false;
                }

                Lyrics cached = null;
                if (usecache) {
                    cached = G.fromCache(artist, song);
                }

                if (cached != null && cached.status()) {
                    return cached;
                } else if (online()) {
                    Genius.GeniusLookUpResult[] results = G.lookUp(artist, song);

                    if (results == null || results.length == 0) {
                        return new Lyrics("#noresults");
                    } else if (results.length == 1) {
                        return G.fromWeb(results[0], artist, song);
                    } else {
                        Lyrics l = new Lyrics("#multipleresults");
                        l.setArtist(artist);
                        l.setTitle(song);
                        l.setResults(results);
                        return l;
                    }
                } else {
                    return new Lyrics("#offline");
                }
            } else {
                // Direct download by id
                return G.fromWeb(songData[2], artist, song);
            }
        }

        @Override
        protected void onPostExecute(Lyrics result) {
            super.onPostExecute(result);

            TextView textViewResult = (TextView) findViewById(R.id.result);
            if (result.status()) {
                textViewResult.setText(multiTrim(result.getLyrics()));

                getSupportActionBar().setTitle(result.getArtist() + " - " + result.getTitle());
                getSupportActionBar().setSubtitle("");

                // Scroll to top
                findViewById(R.id.scrollView).scrollTo(0,0);
            } else {
                if (result.getErrorMessage().equals("#multipleresults")) {
                    showSongSelector(result);
                } else {
                    textViewResult.setText("");
                    getSupportActionBar().setTitle(result.getErrorMessage());
                    getSupportActionBar().setSubtitle(R.string.sorry);
                }
            }
        }
    }

    private boolean online() {
        ConnectivityManager c = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = c.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private String multiTrim(String s) {
        String lines[] = s.split("\\r?\\n");
        String re = "";
        for (String line : lines) {
            re += line.trim() + "\n";
        }
        return re.trim();
    }

}
