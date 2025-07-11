package sexy.lyrics;

import static java.lang.Integer.max;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.util.Function;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.concurrent.Executors;

import sexy.lyrics.databinding.ActivityLyricsViewBinding;

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
    private ActivityLyricsViewBinding binding;

    private static String multiTrim(String str) {
        String[] lines = str.split("\\r?\\n");
        StringBuilder re = new StringBuilder();
        for (String line : lines) {
            re.append(line.trim()).append("\n");
        }
        return re.toString().trim();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLyricsViewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        // Adjust toolbar for cutout on Android P+ and for Android 15 edge-to-edge mode
        binding.getRoot().setOnApplyWindowInsetsListener((view, insets) -> {
            WindowInsetsCompat insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets, view);
            int topInset = insetsCompat.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            DisplayCutoutCompat displayCutout = insetsCompat.getDisplayCutout();
            int cutoutOffset = displayCutout != null ? displayCutout.getSafeInsetTop() : 0;
            view.setPadding(0, max(topInset, cutoutOffset), 0, 0);

            // Adjust the toolbar for the navigation bar on Android 15+ edge-to-edge mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                int navBarBottom = insetsCompat.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                binding.scrollView.setPadding(
                        binding.scrollView.getPaddingLeft(),
                        binding.scrollView.getPaddingTop(),
                        binding.scrollView.getPaddingRight(),
                        navBarBottom + 3);
            }
            return insets;
        });

        try {
            genius = new Genius(this);
        } catch (Exception e) {
            setActionBarSubtitle("No API Key");
            Log.e("Lyrics onCreate", "Could not find API Key");
            return;
        }

        musicBroadcastReceiver.register(this);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        fontSize = binding.result.getTextSize();

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
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> sendMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY), 20);

        }

        binding.result.setOnLongClickListener(view -> {
            if (!binding.result.isTextSelectable()) {
                binding.result.setTextIsSelectable(true);
            }
            return false;
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
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putString("currentTitle", currentTitle);
        savedInstanceState.putString("currentArtist", currentArtist);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        genius.close();
        musicBroadcastReceiver.unRegister();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int action_refresh = R.id.action_refresh;
        final int action_force_reload = R.id.action_force_reload;
        final int action_view_website = R.id.action_view_website;
        final int action_search = R.id.action_search;
        final int action_make_font_bigger = R.id.action_make_font_bigger;
        final int action_make_font_smaller = R.id.action_make_font_smaller;

        final int itemId = item.getItemId();
        if (itemId == action_refresh) {
            if (currentTitle != null && currentArtist != null) {
                loadLyrics(currentArtist, currentTitle);
            }
        } else if (itemId == action_force_reload) {
            if (currentTitle != null && currentArtist != null) {
                loadLyrics(currentArtist, currentTitle, false);
            }
        } else if (itemId == action_view_website) {
            openGeniusCom(currentTitle, currentArtist);
        } else if (itemId == action_search) {
            showSearchButton(FROM_MENU);
        } else if (itemId == action_make_font_bigger) {
            fontSize *= 1.1f;
            binding.result.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
        } else if (itemId == action_make_font_smaller) {
            fontSize *= 0.9f;
            binding.result.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
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
                || localArtist.isEmpty()
                || localTitle.isEmpty()) {
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
        new AsyncRunner(param).run(LyricsViewActivity.this::showLyrics);
    }

    private void hideSongSelector() {
        binding.songSelectorLayout.removeAllViews();
    }

    private void showSongSelector(final Lyrics lyrics) {
        hideSearchButton();
        hideSongSelector();

        setActionBarTitle("");
        setActionBarSubtitle(R.string.please_select_lyrics);
        binding.result.setText("");

        final Genius.GeniusLookUpResult[] results = lyrics.getResults();
        for (final Genius.GeniusLookUpResult result : results) {
            Button resultButton = new Button(this);
            resultButton.setText(getString(R.string.artist_minus_title, result.getArtist(), result.getTitle()));
            resultButton.setTextSize(22);
            resultButton.setTextColor(ContextCompat.getColor(this, R.color.colorLinksText));
            resultButton.setOnClickListener(new OnSelectSong(result, lyrics.getArtist(), lyrics.getTitle()));
            binding.songSelectorLayout.addView(resultButton);
        }

        Button searchButton = new Button(this);
        searchButton.setText(R.string.search);
        searchButton.setTextSize(22);
        searchButton.setOnClickListener(view -> showSearchButton(FROM_SELECTOR));
        binding.songSelectorLayout.addView(searchButton);

    }

    private void hideSearchButton() {
        binding.searchButtonLayout.removeAllViews();
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

        binding.result.setText("");

        TextView textView;

        if (status.equals(NO_RESULTS)) {
            textView = new TextView(this);
            textView.setText(R.string.sorry_no_results);
            binding.searchButtonLayout.addView(textView);
        }

        textView = new TextView(this);
        textView.setText(getString(R.string.x_newline_y,
                getString(R.string.search_more_results),
                getString(R.string.artist_colon)));
        binding.searchButtonLayout.addView(textView);

        final EditText editTextArtist = new EditText(this);
        editTextArtist.setText(searchArtist);
        binding.searchButtonLayout.addView(editTextArtist);

        textView = new TextView(this);
        textView.setText(R.string.title_colon);
        binding.searchButtonLayout.addView(textView);

        final EditText editTextTitle = new EditText(this);
        editTextTitle.setText(searchTitle);
        binding.searchButtonLayout.addView(editTextTitle);

        Button button = new Button(this);
        button.setText(R.string.search_go);
        button.setTextSize(22);
        button.setOnClickListener(createOnClickHandler(editTextArtist, editTextTitle));
        binding.searchButtonLayout.addView(button);

        if (status.equals(NO_RESULTS_AFTER_SEARCH)) {
            textView = new TextView(this);
            textView.setText(R.string.sorry_no_search_results);
            binding.searchButtonLayout.addView(textView);
        }
    }

    private View.OnClickListener createOnClickHandler(EditText editTextArtist, EditText editTextTitle) {
        return view -> {
            hideSearchButton();

            String searchArtist1 = editTextArtist.getText().toString();
            String searchTitle1 = editTextTitle.getText().toString();

            // Removed cached entry:
            genius.deleteFromCache(searchArtist1, searchTitle1);

            setActionBarTitle(R.string.search);
            setActionBarSubtitle(searchArtist1 + " - " + searchTitle1);

            new AsyncRunner(searchArtist1, searchTitle1, currentArtist, currentTitle)
                    .run(LyricsViewActivity.this::showLyrics);
        };
    }

    private boolean online() {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return networkCapabilities != null
                    && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } else {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null;
        }
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

            new AsyncRunner(
                    this.originalArtist,
                    this.originalTitle,
                    Integer.toString(result.getId()),
                    BY_ID).run(LyricsViewActivity.this::showLyrics);

        }
    }

    private Lyrics loadLyrics(String... songData) {
        // Request type by number of parameter:
        // Normal request:  (2) artist, song
        // Nocache request: (3) artist, song, NO_CACHE
        // By id request:   (4) artist, song, id, BY_ID
        // search request:  (4) searchArtist, searchTitle, localArtist, localSong
        String artist = songData[0];
        String song = songData[1];
        if (songData.length < 4) {
            boolean useCache = songData.length <= 2 || !songData[2].equals(NO_CACHE);

            Lyrics cached = null;
            if (useCache) {
                cached = activity.genius.fromCache(artist, song);
            }

            if (cached != null && cached.status()) {
                return cached;
            } else if (activity.online()) {
                Genius.GeniusLookUpResult[] results = activity.genius.lookUp(artist, song);

                if (results.length == 0) {
                    Log.d("loadLyrics", "Search again with broader search function");
                    results = activity.genius.search(artist, song);
                }

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
                Log.d("loadLyrics", "Search again with broader search function");
                results = activity.genius.search(searchArtist, searchTitle);
            }

            if (results.length == 0) {
                // Check for diacritics
                String normalizedArtist = Normalizer.normalize(searchArtist, Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
                String normalizedTitle = Normalizer.normalize(searchTitle, Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

                if (normalizedArtist.equals(searchArtist) && normalizedTitle.equals(searchTitle)) {
                    Lyrics noResultsLyrics = new Lyrics(NO_RESULTS_AFTER_SEARCH);
                    noResultsLyrics.setArtist(searchArtist);
                    noResultsLyrics.setTitle(searchTitle);
                    return noResultsLyrics;
                } else {
                    // Search again without the diacritics
                    Log.d("loadLyrics", "Search again without the diacritics");
                    results = activity.genius.lookUp(normalizedArtist, normalizedTitle);
                    if (results.length == 0) {
                        Log.d("loadLyrics", "Search again without the diacritics with broader search function");
                        results = activity.genius.search(searchArtist, searchTitle);
                    }
                    if (results.length == 0) {
                        Lyrics noResultsLyrics = new Lyrics(NO_RESULTS_AFTER_SEARCH);
                        noResultsLyrics.setArtist(localArtist);
                        noResultsLyrics.setTitle(localSong);
                        return noResultsLyrics;
                    } else {
                        Lyrics multipleResultsLyrics = new Lyrics(MULTIPLE_RESULTS);
                        multipleResultsLyrics.setArtist(localArtist);
                        multipleResultsLyrics.setTitle(localSong);
                        multipleResultsLyrics.setResults(results);
                        return multipleResultsLyrics;
                    }
                }
            } else {
                Lyrics multipleResultsLyrics = new Lyrics(MULTIPLE_RESULTS);
                multipleResultsLyrics.setArtist(localArtist);
                multipleResultsLyrics.setTitle(localSong);
                multipleResultsLyrics.setResults(results);
                return multipleResultsLyrics;
            }
        }
    }

    private Void showLyrics(Lyrics result) {
        if (result.status()) {
            binding.result.setText(multiTrim(result.getLyrics()));

            activity.setActionBarTitle(result.getArtist() + " - " + result.getTitle());
            activity.setActionBarSubtitle("");

            // Scroll to top
            binding.scrollView.scrollTo(0, 0);

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
                    binding.result.setText("");
                    activity.setActionBarTitle(result.getErrorMessage());
                    activity.setActionBarSubtitle(R.string.sorry);
                    break;
            }
        }
        return null;
    }

    private class AsyncRunner {
        private final String[] params;

        public AsyncRunner(String... songData) {
            params = songData;
        }

        private void run(Function<Lyrics, Void> callback) {
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.execute(() -> {
                    Lyrics result = loadLyrics(params);
                    new Handler(Looper.getMainLooper()).post(() -> callback.apply(result));
                });
            } catch (Exception e) {
                Log.e("AsyncRunner", "Error running async lyrics load", e);
            }
        }
    }


}
