package sexy.lyrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MusicBroadcastReceiver extends BroadcastReceiver {
    private String last;
    private LyricsViewActivity activity = null;
    private String artist = null;
    private String track = null;
    private String album = null;

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

    @Override
    public void onReceive(Context context, Intent intent) {
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

            if(activity != null) {
                activity.loadLyrics(artist, track);
            }
        }
    }

}
