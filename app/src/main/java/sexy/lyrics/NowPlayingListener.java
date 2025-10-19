package sexy.lyrics;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * MediaSession-only now playing listener.
 * Requires user-enabled Notification Access.
 */
public class NowPlayingListener extends NotificationListenerService
        implements MediaSessionManager.OnActiveSessionsChangedListener {

    public static final String ACTION_NOW_PLAYING = "sexy.lyrics.NOW_PLAYING";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_PACKAGE = "package";

    private static final String TAG = "NowPlayingMS";
    private final List<Pair<MediaController, MediaController.Callback>> tracked = new ArrayList<>();
    private MediaSessionManager msm;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (msm == null) {
            Log.w(TAG, "MediaSessionManager not available");
            return;
        }
        ComponentName component = new ComponentName(this, NowPlayingListener.class);
        msm.addOnActiveSessionsChangedListener(this, component);
        onActiveSessionsChanged(msm.getActiveSessions(component));
        Log.d(TAG, "Listener connected");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (msm != null) {
            msm.removeOnActiveSessionsChangedListener(this);
        }
        unregisterAll();
        Log.d(TAG, "Listener disconnected");
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> controllers) {
        unregisterAll();
        if (controllers == null) return;

        for (MediaController c : controllers) {
            MediaController.Callback cb = new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    maybeReport(c);
                }

                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    maybeReport(c);
                }
            };
            c.registerCallback(cb);
            tracked.add(new Pair<>(c, cb));
        }
        pickAndReportBest(controllers);
    }

    private void unregisterAll() {
        for (Pair<MediaController, MediaController.Callback> p : tracked) {
            try {
                p.first.unregisterCallback(p.second);
            } catch (Exception ignored) {
            }
        }
        tracked.clear();
    }

    /**
     * Choose the best candidate (prefer PLAYING, then BUFFERING, then PAUSED with metadata).
     */
    private void pickAndReportBest(List<MediaController> controllers) {
        MediaController best = null;
        int bestScore = -1;
        for (MediaController c : controllers) {
            int score = score(c);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best != null) maybeReport(best);
    }

    private int score(MediaController c) {
        int score = 0;
        PlaybackState st = c.getPlaybackState();
        if (st != null) {
            switch (st.getState()) {
                case PlaybackState.STATE_PLAYING:
                    score += 100;
                    break;
                case PlaybackState.STATE_BUFFERING:
                    score += 60;
                    break;
                case PlaybackState.STATE_PAUSED:
                    score += 20;
                    break;
                default:
            }
        }
        MediaMetadata md = c.getMetadata();
        if (md != null && !TextUtils.isEmpty(getTitle(md))) score += 10;
        return score;
    }

    private void maybeReport(MediaController c) {
        MediaMetadata md = c.getMetadata();
        if (md != null) {
            String title = getTitle(md);
            String artist = getArtist(md);
            if (!TextUtils.isEmpty(title)) {
                emitNowPlaying(c.getPackageName(), artist, title);
                return;
            }
        }
        // Try notification title as a last mild fallback (still MediaSession context)
        StatusBarNotification sbn = latestForPackage(c.getPackageName());
        if (sbn != null) {
            Notification n = sbn.getNotification();
            CharSequence t = n.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence a = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (!TextUtils.isEmpty(t)) {
                emitNowPlaying(c.getPackageName(),
                        a == null ? null : a.toString(),
                        t.toString());
            }
        }
    }

    private String getTitle(MediaMetadata md) {
        String t = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        if (TextUtils.isEmpty(t)) t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        return t;
    }

    private String getArtist(MediaMetadata md) {
        String a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (TextUtils.isEmpty(a)) a = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (TextUtils.isEmpty(a)) a = md.getString(MediaMetadata.METADATA_KEY_AUTHOR);
        if (TextUtils.isEmpty(a)) a = md.getString(MediaMetadata.METADATA_KEY_WRITER);
        return a;
    }

    private StatusBarNotification latestForPackage(String pkg) {
        StatusBarNotification[] list = getActiveNotifications();
        if (list == null) return null;
        StatusBarNotification latest = null;
        for (StatusBarNotification sbn : list) {
            if (!pkg.equals(sbn.getPackageName())) continue;
            if (latest == null || sbn.getPostTime() > latest.getPostTime()) latest = sbn;
        }
        return latest;
    }

    /**
     * Send a simple broadcast your app can observe (Activity, ViewModel, etc.).
     */
    private void emitNowPlaying(String pkg, String artist, String title) {
        Intent intent = new Intent(ACTION_NOW_PLAYING)
                .putExtra(EXTRA_PACKAGE, pkg)
                .putExtra(EXTRA_ARTIST, artist == null ? "" : artist)
                .putExtra(EXTRA_TITLE, title == null ? "" : title);
        Log.v(TAG, "Now playing: " + pkg + " - " + artist + " - " + title);
        sendBroadcast(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        for (Pair<MediaController, MediaController.Callback> p : tracked) {
            if (p.first.getPackageName().equals(sbn.getPackageName())) {
                maybeReport(p.first);
                break;
            }
        }
    }
}
