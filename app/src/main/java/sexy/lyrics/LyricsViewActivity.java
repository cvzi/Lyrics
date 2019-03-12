package sexy.lyrics;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;

import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class LyricsViewActivity extends AppCompatActivity {
    private static final String BY_ID = "byId";
    private static final String NO_CACHE = "no_cache";
    private static final String NO_RESULTS = "#noresults";
    private static final String NO_RESULTS_AFTER_SEARCH = "#noresultsaftersearch";
    private static final String MULTIPLE_RESULTS = "#multipleresults";
    private static final String FROM_MENU = "#frommenu";
    private static final String FROM_SELECTOR = "#fromselector";
    private static final String OFFLINE = "#offline";
    private final MusicBroadcastReceiver musicBroadcastReceiver = new MusicBroadcastReceiver();
    private final LyricsViewActivity activity = this;
    private Genius genius;
    private String currentArtist = null;
    private String currentTitle = null;
    private Lyrics currentLyrics = null;
    private float fontSize = 0;
    private AudioManager audioManager = null;

    @SuppressWarnings("StringConcatenationInLoop")
    private static String multiTrim(String str) {
        String[] lines = str.split("\\r?\\n");
        String re = "";
        for (String line : lines) {
            re += line.trim() + "\n";
        }
        return re.trim();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lyrics_view);

        final TextView textViewResult = findViewById(R.id.result);

        try {
            genius = new Genius(this);
        } catch (Exception e) {
            setActionBarSubtitle("No API Key");
            Log.e("Lyrics onCreate", "Could not find API Key");
            return;
        }

        @SuppressWarnings("SpellCheckingInspection")
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
        @SuppressWarnings("SpellCheckingInspection")
        String[] musicActions = new String[]{
                "metachanged",
                "playstatechange",
                "playstatechanged",
                "playbackstatechanged",
                "queuechanged"
        };

        IntentFilter intentFilter = new IntentFilter();

        for (String app : musicApps) {
            for (String action : musicActions) {
                intentFilter.addAction(app + "." + action);
            }
        }

        musicBroadcastReceiver.setActivity(this);
        registerReceiver(musicBroadcastReceiver, intentFilter);


        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        fontSize = textViewResult.getTextSize();

        if (savedInstanceState != null) {
            currentArtist = savedInstanceState.getString("currentArtist", null);
            currentTitle = savedInstanceState.getString("currentTitle", null);
        }

        if (currentArtist != null && currentTitle != null) {
            loadLyrics(currentArtist, currentTitle);
        } else if (musicBroadcastReceiver.hasSong()) {
            currentTitle = musicBroadcastReceiver.getTrack();
            currentArtist = musicBroadcastReceiver.getArtist();
            loadLyrics(currentArtist, currentTitle);
        } else if (audioManager.isMusicActive()) {
            // Pause and immediately play music to trigger a broadcast of the current song
            sendMediaButton(KeyEvent.KEYCODE_MEDIA_PAUSE);
            sendMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY);
        }


        textViewResult.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (!textViewResult.isTextSelectable()) {
                    textViewResult.setTextIsSelectable(true);
                }
                return false;
            }
        });


    }

    private void sendMediaButton(int keyCode) {
        long eventTime = SystemClock.uptimeMillis();

        KeyEvent downEvent = new KeyEvent(
                eventTime,
                eventTime,
                KeyEvent.ACTION_DOWN,
                keyCode,
                0);
        audioManager.dispatchMediaKeyEvent(downEvent);
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
        genius.close();
        unregisterReceiver(musicBroadcastReceiver);
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
            case R.id.action_force_reload:
                if (currentTitle != null && currentArtist != null) {
                    loadLyrics(currentArtist, currentTitle, false);
                }
                break;

            case R.id.action_view_website:
                openGeniusCom(currentTitle, currentArtist);
                break;

            case R.id.action_search:
                showSearchButton(FROM_MENU);
                break;

            case R.id.action_make_font_bigger:
                fontSize *= 1.1f;
                ((TextView) findViewById(R.id.result)).setTextSize(
                        TypedValue.COMPLEX_UNIT_PX,
                        fontSize);
                break;

            case R.id.action_make_font_smaller:
                fontSize *= 0.9f;
                ((TextView) findViewById(R.id.result)).setTextSize(
                        TypedValue.COMPLEX_UNIT_PX,
                        fontSize);
                break;


            default:
                break;
        }
        return true;
    }

    private void openGeniusCom(String localArtist, String localTitle) {
        String url = "https://genius.com/";

        if (currentLyrics != null) {
            url = currentLyrics.getUrl();
        } else if (localArtist != null && localTitle != null) {
            try {
                url = "https://genius.com/search?q="
                        + URLEncoder.encode(localArtist + " " + localTitle, "utf-8");
            } catch (UnsupportedEncodingException e) {
                Log.e("openGeniusCom", "UnsupportedEncodingException", e);
            }
        }

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (browserIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(browserIntent);
        }
    }

    public void loadLyrics(String localArtist, String localTitle) {
        loadLyrics(localArtist, localTitle, true);
    }

    private void loadLyrics(String localArtist, String localTitle, boolean useCache) {
        currentArtist = localArtist;
        currentTitle = localTitle;

        if (localArtist == null
                || localTitle == null
                || localArtist.length() == 0
                || localTitle.length() == 0) {
            return;
        }

        hideSongSelector();
        hideSearchButton();

        setActionBarTitle(R.string.loading);
        setActionBarSubtitle(localTitle + " " + getResources().getString(R.string.by_artist) + " " + localArtist);
        String[] param;
        if (useCache) {
            param = new String[]{localArtist, localTitle};
        } else {
            param = new String[]{localArtist, localTitle, NO_CACHE};
        }
        new RequestTask(activity).execute(param);
    }

    private void hideSongSelector() {
        LinearLayout linearLayout = findViewById(R.id.songSelectorLayout);
        linearLayout.removeAllViews();
    }

    private void showSongSelector(final Lyrics lyrics) {
        hideSearchButton();
        hideSongSelector();

        LinearLayout linearLayout = findViewById(R.id.songSelectorLayout);

        setActionBarTitle("");
        setActionBarSubtitle(R.string.please_select_lyrics);
        ((TextView) findViewById(R.id.result)).setText("");

        final Genius.GeniusLookUpResult[] results = lyrics.getResults();
        for (final Genius.GeniusLookUpResult result : results) {
            Button resultButton = new Button(this);
            resultButton.setText(getString(R.string.artist_minus_title, result.getArtist(), result.getTitle()));
            resultButton.setTextSize(22);
            resultButton.setTextColor(ContextCompat.getColor(
                    this,
                    R.color.colorLinksText));
            resultButton.setOnClickListener(
                    new OnSelectSong(result, lyrics.getArtist(), lyrics.getTitle()));
            linearLayout.addView(resultButton);
        }

        Button searchButton = new Button(this);
        searchButton.setText(R.string.search);
        searchButton.setTextSize(22);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSearchButton(FROM_SELECTOR);
            }
        });
        linearLayout.addView(searchButton);

    }

    private void hideSearchButton() {
        LinearLayout linearLayout = findViewById(R.id.searchButtonLayout);
        linearLayout.removeAllViews();
    }

    private void showSearchButton(String status) {
        showSearchButton(status, null);
    }

    private void showSearchButton(String status, Lyrics lyrics) {
        hideSearchButton();
        hideSongSelector();

        String searchTitle = currentTitle != null ? currentTitle : "";
        String searchArtist = currentArtist != null ? currentArtist : "";
        if (lyrics != null) {
            searchTitle = lyrics.getTitle();
            searchArtist = lyrics.getArtist();
        }

        ((TextView) findViewById(R.id.result)).setText("");

        TextView textView;

        LinearLayout linearLayout = findViewById(R.id.searchButtonLayout);

        if (status.equals(NO_RESULTS)) {
            textView = new TextView(this);
            textView.setText(R.string.sorry_no_results);
            linearLayout.addView(textView);
        }

        textView = new TextView(this);
        textView.setText(getString(R.string.x_newline_y,
                getString(R.string.search_more_results),
                getString(R.string.artist_colon)));
        linearLayout.addView(textView);

        final EditText editTextArtist = new EditText(this);
        editTextArtist.setText(searchArtist);
        linearLayout.addView(editTextArtist);

        textView = new TextView(this);
        textView.setText(R.string.title_colon);
        linearLayout.addView(textView);

        final EditText editTextTitle = new EditText(this);
        editTextTitle.setText(searchTitle);
        linearLayout.addView(editTextTitle);

        Button button = new Button(this);
        button.setText(R.string.search_go);
        button.setTextSize(22);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideSearchButton();

                String searchArtist = editTextArtist.getText().toString();
                String searchTitle = editTextTitle.getText().toString();

                // Removed cached entry:
                genius.deleteFromCache(searchArtist, searchTitle);

                setActionBarTitle(R.string.search);
                setActionBarSubtitle(searchArtist + " - " + searchTitle);

                String[] param = new String[]{
                        searchArtist,
                        searchTitle,
                        currentArtist,
                        currentTitle};
                new RequestTask(activity).execute(param);
            }
        });
        linearLayout.addView(button);

        if (status.equals(NO_RESULTS_AFTER_SEARCH)) {
            textView = new TextView(this);
            textView.setText(R.string.sorry_no_search_results);
            linearLayout.addView(textView);
        }

    }

    private boolean online() {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    /**
     * Set the action bar's title if the action exists.
     *
     * @param resId Resource ID of title string to set
     */
    private void setActionBarTitle(@StringRes int resId) {
        setActionBarTitle(getString(resId));
    }

    /**
     * Set the action bar's title if the action exists.
     *
     * @param title Subtitle to set
     */
    private void setActionBarTitle(CharSequence title) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
        }
    }

    /**
     * Set the action bar's subtitle if the action exists.
     *
     * @param resId Resource ID of subtitle string to set
     */
    private void setActionBarSubtitle(@StringRes int resId) {
        setActionBarSubtitle(getString(resId));
    }

    /**
     * Set the action bar's subtitle if the action exists.
     *
     * @param subtitle Subtitle to set
     */
    private void setActionBarSubtitle(CharSequence subtitle) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(subtitle);
        }
    }

    static class RequestTask extends AsyncTask<String, String, Lyrics> {

        private final WeakReference<LyricsViewActivity> activityReference;

        // only retain a weak reference to the activity
        RequestTask(LyricsViewActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected Lyrics doInBackground(String... songData) {
            // Request type by number of parameter:
            // Normal request:  (2) artist, song
            // Nocache request: (3) artist, song, NO_CACHE
            // By id request:   (4) artist, song, id, BY_ID
            // search request:  (4) searchArtist, searchTitle, localArtist, localSong

            LyricsViewActivity activity = activityReference.get();
            String artist = songData[0];
            String song = songData[1];
            if (songData.length < 4) {
                boolean useCache = true;
                if (songData.length > 2 && songData[2].equals(NO_CACHE)) {
                    useCache = false;
                }

                Lyrics cached = null;
                if (useCache) {
                    cached = activity.genius.fromCache(artist, song);
                }

                if (cached != null && cached.status()) {
                    return cached;
                } else if (activity.online()) {
                    Genius.GeniusLookUpResult[] results = activity.genius.lookUp(artist, song);

                    if (results.length == 0) {
                        return new Lyrics(NO_RESULTS);
                    } else if (results.length == 1) {
                        return activity.genius.fromWeb(results[0], artist, song);
                    } else {
                        Lyrics multipleLyrics = new Lyrics(MULTIPLE_RESULTS);
                        multipleLyrics.setArtist(artist);
                        multipleLyrics.setTitle(song);
                        multipleLyrics.setResults(results);
                        return multipleLyrics;
                    }
                } else {
                    return new Lyrics(OFFLINE);
                }
            } else if (songData[3].equals(BY_ID)) {
                // Direct download by id
                return activity.genius.fromWeb(songData[2], artist, song);
            } else {
                // Search
                String searchArtist = songData[0];
                String searchTitle = songData[1];
                String localArtist = songData[2];
                String localSong = songData[3];
                Genius.GeniusLookUpResult[] results = activity.genius.lookUp(searchArtist, searchTitle);

                if (results.length == 0) {
                    Lyrics noResultsLyrics = new Lyrics(NO_RESULTS_AFTER_SEARCH);
                    noResultsLyrics.setArtist(searchArtist);
                    noResultsLyrics.setTitle(searchTitle);
                    return noResultsLyrics;
                } else {
                    Lyrics multipleResultsLyrics = new Lyrics(MULTIPLE_RESULTS);
                    multipleResultsLyrics.setArtist(localArtist);
                    multipleResultsLyrics.setTitle(localSong);
                    multipleResultsLyrics.setResults(results);
                    return multipleResultsLyrics;
                }
            }
        }

        @Override
        protected void onPostExecute(Lyrics result) {
            super.onPostExecute(result);

            LyricsViewActivity activity = activityReference.get();

            TextView textViewResult = activity.findViewById(R.id.result);
            if (result.status()) {
                textViewResult.setText(multiTrim(result.getLyrics()));

                activity.setActionBarTitle(result.getArtist() + " - " + result.getTitle());
                activity.setActionBarSubtitle("");

                // Scroll to top
                activity.findViewById(R.id.scrollView).scrollTo(0, 0);

                activity.currentLyrics = result;
            } else {
                activity.currentLyrics = null;
                switch (result.getErrorMessage()) {
                    case MULTIPLE_RESULTS:
                        activity.showSongSelector(result);
                        break;
                    case NO_RESULTS:
                        activity.setActionBarTitle(R.string.search);
                        activity.setActionBarSubtitle("");
                        activity.showSearchButton(NO_RESULTS);
                        break;
                    case NO_RESULTS_AFTER_SEARCH:
                        activity.setActionBarTitle(R.string.search);
                        activity.setActionBarSubtitle(R.string.sorry);
                        activity.showSearchButton(NO_RESULTS_AFTER_SEARCH, result);
                        break;
                    default:
                        textViewResult.setText("");
                        activity.setActionBarTitle(result.getErrorMessage());
                        activity.setActionBarSubtitle(R.string.sorry);
                        break;
                }
            }
        }
    }

    class OnSelectSong implements View.OnClickListener {
        private final Genius.GeniusLookUpResult result;
        private final String originalArtist;
        private final String originalTitle;

        OnSelectSong(Genius.GeniusLookUpResult result,
                     String originalArtist,
                     String originalTitle) {
            this.result = result;
            this.originalArtist = originalArtist;
            this.originalTitle = originalTitle;
        }

        @Override
        public void onClick(View view) {

            hideSongSelector();

            setActionBarTitle(R.string.loading);
            setActionBarSubtitle(result.getTitle() + " " + getResources().getString(R.string.by_artist) + " " + result.getArtist());

            new RequestTask(activity).execute(
                    this.originalArtist,
                    this.originalTitle,
                    Integer.toString(result.getId()),
                    BY_ID);
        }
    }

}
