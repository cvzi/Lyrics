package sexy.lyrics;


import static android.content.Context.RECEIVER_EXPORTED;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;

public class MusicBroadcastReceiver extends BroadcastReceiver {
    private String last;
    private WeakReference<LyricsViewActivity> activity = new WeakReference<>(null);
    private String artist = null;
    private String track = null;

    @NonNull
    private static IntentFilter getIntentFilter(String[] musicApps) {
        @SuppressWarnings("SpellCheckingInspection")
        String[] musicActions = new String[]{
                "metachanged",
                "metadatachanged",
                "playstatechange",
                "playstatechanged",
                "playbackstatechanged",
                "queuechanged"
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(NowPlayingListener.ACTION_NOW_PLAYING);
        for (String app : musicApps) {
            for (String action : musicActions) {
                intentFilter.addAction(app + "." + action);
            }
        }
        return intentFilter;
    }

    public boolean hasSong() {
        return artist != null && track != null;
    }

    public String getArtist() {
        return artist;
    }

    public String getTrack() {
        return track;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        PlayingInfo result;
        if (NowPlayingListener.ACTION_NOW_PLAYING.equals(action)) {
            result = parseInternalBroadcast(intent);
        } else {
            result = parseLegacyBroadcast(intent);
        }
        if (result != null) {
            artist = result.artist();
            track = result.title();
        }
        if (last == null || !last.equals(artist + ":" + track)) {
            last = artist + ":" + track;

            LyricsViewActivity lyricsViewActivity = activity.get();
            if (lyricsViewActivity != null) {
                lyricsViewActivity.loadLyricsAsync(artist, track);
            }
        }
    }

    private PlayingInfo parseInternalBroadcast(Intent intent) {
        // From MediaSession listener
        String artist = intent.getStringExtra(NowPlayingListener.EXTRA_ARTIST);
        String track = intent.getStringExtra(NowPlayingListener.EXTRA_TITLE);
        if (artist != null && artist.endsWith(" - Topic")) { // Youtube artists have this suffix
            artist = artist.substring(0, artist.length() - 8);
        }
        return new PlayingInfo(artist, track);
    }

    private PlayingInfo parseLegacyBroadcast(Intent intent) {
        String action = intent.getAction();
        // Legacy broadcast from music apps
        String artist, track;
        try {
            if ("com.amazon.mp3.metachanged".equals(action)) {
                artist = intent.getStringExtra("com.amazon.mp3.artist");
                track = intent.getStringExtra("com.amazon.mp3.track");
            } else {
                artist = intent.getStringExtra("artist");
                track = intent.getStringExtra("track");
            }
            return new PlayingInfo(artist, track);
        } catch (Throwable e) {
            return null;
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public void register(LyricsViewActivity context) {
        this.activity = new WeakReference<>(context);

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
        IntentFilter intentFilter = getIntentFilter(musicApps);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, intentFilter, RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(this, intentFilter);
        }
    }

    public void unRegister() {
        Context context = this.activity.get();
        if (context != null) {
            context.unregisterReceiver(this);
            this.activity.clear();
        }
        this.activity = null;
    }

    private record PlayingInfo(String artist, String title) {
    }

}
