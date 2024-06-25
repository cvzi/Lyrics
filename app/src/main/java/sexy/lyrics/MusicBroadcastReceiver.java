package sexy.lyrics;


import static android.content.Context.RECEIVER_EXPORTED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import java.lang.ref.WeakReference;

public class MusicBroadcastReceiver extends BroadcastReceiver {
    private String last;
    private WeakReference<LyricsViewActivity> activity = new WeakReference<>(null);
    private String artist = null;
    private String track = null;

    public boolean hasSong() {
        return artist != null && track != null;
    }

    public String getArtist() {
        return artist;
    }

    public String getTrack() {
        return track;
    }

    @SuppressWarnings("SpellCheckingInspection")
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        try {
            if ("com.amazon.mp3.metachanged".equals(action)) {
                artist = intent.getStringExtra("com.amazon.mp3.artist");
                track = intent.getStringExtra("com.amazon.mp3.track");
            } else {
                artist = intent.getStringExtra("artist");
                track = intent.getStringExtra("track");
            }
        } catch (Throwable e) {
            return;
        }
        if (last == null || !last.equals(artist + ":" + track)) {
            last = artist + ":" + track;

            LyricsViewActivity lyricsViewActivity = activity.get();
            if (lyricsViewActivity != null) {
                lyricsViewActivity.loadLyrics(artist, track);
            }
        }
    }

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

        for (String app : musicApps) {
            for (String action : musicActions) {
                intentFilter.addAction(app + "." + action);
            }
        }

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

}
