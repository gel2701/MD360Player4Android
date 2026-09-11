package com.asha.md360player4android;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.Surface;

import java.io.IOException;

/**
 * Lightweight wrapper around Android's built-in MediaPlayer.
 * Replaces the legacy IJKPlayer dependency while preserving the 360 video flow.
 */
public class MediaPlayerWrapper implements MediaPlayer.OnPreparedListener {
    protected MediaPlayer mPlayer;
    private MediaPlayer.OnPreparedListener mPreparedListener;

    private static final int STATUS_IDLE = 0;
    private static final int STATUS_PREPARING = 1;
    private static final int STATUS_PREPARED = 2;
    private static final int STATUS_STARTED = 3;
    private static final int STATUS_PAUSED = 4;
    private static final int STATUS_STOPPED = 5;
    private int mStatus = STATUS_IDLE;

    public void init() {
        destroy();
        mStatus = STATUS_IDLE;
        mPlayer = new MediaPlayer();
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mPlayer.setOnPreparedListener(this);
    }

    public void setSurface(Surface surface) {
        if (mPlayer != null) {
            mPlayer.setSurface(surface);
        }
    }

    public void openRemoteFile(String url) {
        if (mPlayer == null) return;
        try {
            mPlayer.setDataSource(url);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to open media source: " + url, e);
        }
    }

    public void openAssetFile(Context context, String assetPath) {
        if (mPlayer == null) return;
        AssetFileDescriptor afd = null;
        try {
            afd = context.getAssets().openFd(assetPath);
            mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to open asset: " + assetPath, e);
        } finally {
            if (afd != null) {
                try {
                    afd.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public MediaPlayer getPlayer() {
        return mPlayer;
    }

    public void prepare() {
        if (mPlayer == null) return;
        if (mStatus == STATUS_IDLE || mStatus == STATUS_STOPPED) {
            mPlayer.prepareAsync();
            mStatus = STATUS_PREPARING;
        }
    }

    public void stop() {
        if (mPlayer == null) return;
        if (mStatus == STATUS_STARTED || mStatus == STATUS_PAUSED || mStatus == STATUS_PREPARED) {
            mPlayer.stop();
            mStatus = STATUS_STOPPED;
        }
    }

    public void pause() {
        if (mPlayer == null) return;
        if (mStatus == STATUS_STARTED && mPlayer.isPlaying()) {
            mPlayer.pause();
            mStatus = STATUS_PAUSED;
        }
    }

    private void start() {
        if (mPlayer == null) return;
        if (mStatus == STATUS_PREPARED || mStatus == STATUS_PAUSED) {
            mPlayer.start();
            mStatus = STATUS_STARTED;
        }
    }

    public void setPreparedListener(MediaPlayer.OnPreparedListener preparedListener) {
        this.mPreparedListener = preparedListener;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mStatus = STATUS_PREPARED;
        start();
        if (mPreparedListener != null) {
            mPreparedListener.onPrepared(mp);
        }
    }

    public void resume() {
        start();
    }

    public void destroy() {
        if (mPlayer != null) {
            try {
                stop();
            } catch (IllegalStateException ignored) {
            }
            mPlayer.setSurface(null);
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }
        mStatus = STATUS_IDLE;
    }
}
