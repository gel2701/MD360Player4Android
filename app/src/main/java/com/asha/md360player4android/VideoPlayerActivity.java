package com.asha.md360player4android;

import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.TextView;

import com.asha.vrlib.MD360Director;
import com.asha.vrlib.MD360DirectorFactory;
import com.asha.vrlib.MDVRLibrary;
import com.asha.vrlib.model.BarrelDistortionConfig;
import com.asha.vrlib.model.MDPinchConfig;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.video.VideoSize;

/**
 * Diagnostic 360 demo player. Uses ExoPlayer for SAF content URIs and robust MP4 handling.
 * Surface may become available before or after player creation, so keep it and reattach.
 */
public class VideoPlayerActivity extends MD360PlayerActivity {
    private static final String TAG = "MD360PlaybackV2";
    private ExoPlayer player;
    private Surface videoSurface;
    private TextView playbackStatus;
    private boolean resumePlayback = true;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        playbackStatus = findViewById(R.id.playback_status);
        showStatus("V2 - Videospeler wordt gestart");

        try {
            DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true);
            player = new ExoPlayer.Builder(this, renderers).build();
            if (videoSurface != null) {
                player.setVideoSurface(videoSurface);
            }
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_BUFFERING) {
                        showStatus("Video wordt ingelezen...");
                    } else if (state == Player.STATE_READY) {
                        cancelBusy();
                        getVRLibrary().notifyPlayerChanged();
                        showStatus("Video geladen - beeld wordt gestart");
                    } else if (state == Player.STATE_ENDED) {
                        cancelBusy();
                        showStatus("Video afgelopen. Druk op next om opnieuw te starten.");
                    }
                }

                @Override
                public void onVideoSizeChanged(VideoSize size) {
                    if (size.width > 0 && size.height > 0 && getVRLibrary() != null) {
                        getVRLibrary().onTextureResize(size.width, size.height);
                    }
                }

                @Override
                public void onRenderedFirstFrame() {
                    cancelBusy();
                    playbackStatus.setVisibility(View.GONE);
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    cancelBusy();
                    Log.e(TAG, "ExoPlayer playback error", error);
                    String detail = error.getErrorCodeName();
                    Throwable cause = error.getCause();
                    if (cause != null && cause.getMessage() != null) {
                        detail += ": " + cause.getMessage();
                    }
                    showStatus("KAN VIDEO NIET OPENEN\n" + detail + "\nMaak een screenshot van deze melding.");
                }
            });

            Uri uri = getUri();
            if (uri == null) {
                cancelBusy();
                showStatus("Geen video geselecteerd. Ga terug en kies 'Open video from phone'.");
                return;
            }
            // ExoPlayer reads content:// document URIs through Android's ContentResolver.
            player.setMediaItem(MediaItem.fromUri(uri));
            player.setPlayWhenReady(true);
            showStatus("Video openen: " + uri.getScheme());
            player.prepare();
        } catch (RuntimeException e) {
            cancelBusy();
            Log.e(TAG, "Failed to initialize video playback", e);
            showStatus("STARTFOUT: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        findViewById(R.id.control_next).setOnClickListener(v -> {
            if (player != null) {
                player.seekTo(0);
                player.play();
            }
        });
    }

    private void showStatus(String message) {
        if (playbackStatus != null) {
            playbackStatus.setText(message);
            playbackStatus.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected MDVRLibrary createVRLibrary() {
        return MDVRLibrary.with(this)
                .displayMode(MDVRLibrary.DISPLAY_MODE_NORMAL)
                .interactiveMode(MDVRLibrary.INTERACTIVE_MODE_MOTION_WITH_TOUCH)
                .asVideo(surface -> runOnUiThread(() -> {
                    videoSurface = surface;
                    if (player != null) {
                        player.setVideoSurface(surface);
                    }
                }))
                .ifNotSupport(mode -> Log.w(TAG, "Unsupported interaction mode: " + mode))
                .pinchConfig(new MDPinchConfig().setMin(1.0f).setMax(8.0f).setDefaultValue(1.0f))
                .pinchEnabled(true)
                .directorFactory(new MD360DirectorFactory() {
                    @Override
                    public MD360Director createDirector(int index) {
                        return MD360Director.builder().setPitch(90).build();
                    }
                })
                .projectionFactory(new CustomProjectionFactory())
                .barrelDistortionConfig(new BarrelDistortionConfig().setDefaultEnabled(false).setScale(0.95f))
                .build((GLSurfaceView) findViewById(R.id.gl_view));
    }

    @Override
    protected void onPause() {
        if (player != null) {
            resumePlayback = player.getPlayWhenReady();
            player.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && resumePlayback) {
            player.play();
        }
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.clearVideoSurface();
            player.release();
            player = null;
        }
        videoSurface = null;
        super.onDestroy();
    }
}
