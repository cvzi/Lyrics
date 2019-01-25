package sexy.lyrics;

import android.content.Context;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LyricsViewActivity extends AppCompatActivity {
    private Genius G;
    private String currentArtist = null;
    private String currentTitle = null;
    private float fontSize = 0;
    private MusicBroadcastReceiver mReceiver = new MusicBroadcastReceiver();
    private AudioManager mAudioManager = null;

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

        IntentFilter iF = new IntentFilter();

        for (String app : musicApps) {
            for (String action : musicActions) {
                iF.addAction(app + "." + action);
            }
        }

        mReceiver.setActivity(this);
        registerReceiver(mReceiver, iF);


        fontSize = ((TextView) findViewById(R.id.result)).getTextSize();

        if (savedInstanceState != null) {
            currentArtist = savedInstanceState.getString("currentArtist", null);
            currentTitle = savedInstanceState.getString("currentTitle", null);
        }

        if (currentArtist != null && currentTitle != null) {
            loadLyrics(currentArtist, currentTitle);
        } else if (mReceiver.hasSong()) {
            currentTitle = mReceiver.getTrack();
            currentArtist = mReceiver.getArtist();
            loadLyrics(currentArtist, currentTitle);
        } else {
            // Pause and immediately play music to trigger a broadcast of the current song
            sendMediaButton(getApplicationContext(), KeyEvent.KEYCODE_MEDIA_PAUSE);
            sendMediaButton(getApplicationContext(), KeyEvent.KEYCODE_MEDIA_PLAY);
        }

    }


    private void sendMediaButton(Context context, int keyCode) {
        if(mAudioManager == null) {
            mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }
        long eventTime = SystemClock.uptimeMillis();

        KeyEvent downEvent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0);
        mAudioManager.dispatchMediaKeyEvent(downEvent);

        // Not necessary ?
        // KeyEvent upEvent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0);
        // mAudioManager.dispatchMediaKeyEvent(upEvent);
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

    public void loadLyrics(String localArtist, String localTitle) {
        loadLyrics(localArtist, localTitle, true);
    }

    public void loadLyrics(String localArtist, String localTitle, boolean useCache) {
        currentArtist = localArtist;
        currentTitle = localTitle;

        if (localArtist == null || localTitle == null || localArtist.length() == 0 || localTitle.length() == 0) {
            return;
        }

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
