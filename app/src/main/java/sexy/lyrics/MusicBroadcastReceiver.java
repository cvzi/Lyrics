package sexy.lyrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MusicBroadcastReceiver extends BroadcastReceiver {
    private String last;
    private LyricsViewActivity activity = null;
    private String artist = null;
    private String track = null;

    public void setActivity(LyricsViewActivity activity) {
        this.activity = activity;
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

            if (activity != null) {
                activity.loadLyrics(artist, track);
            }
        }
    }

}
