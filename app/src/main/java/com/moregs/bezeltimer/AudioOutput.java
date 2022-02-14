package com.moregs.bezeltimer;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import java.util.Arrays;

public class AudioOutput {
    private final AudioManager audioManager;
    private final Context parent_context;

    public AudioOutput(Context context) {
        parent_context = context;
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean audioOutputAvailable(int type) {
        AudioDeviceInfo[] outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        return Arrays.stream(outputs).anyMatch((i) -> i.getType() == type);
    }

    public AudioDeviceInfo get_internal_speaker() {
        AudioDeviceInfo[] outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        return Arrays.stream(outputs).filter((i) -> i.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).findFirst().get();
    }

    public MediaPlayer prepare_alarm_player() {
        Log.i("Main", "Sound the alarm");

        if (audioOutputAvailable(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)) {
            MediaPlayer mediaPlayer = MediaPlayer.create(parent_context, R.raw.alarm2);

            try {
                mediaPlayer.setPreferredDevice(get_internal_speaker());
                mediaPlayer.setOnPreparedListener(player -> Log.i("mediaPlayer", "Prepared"));
                mediaPlayer.setOnCompletionListener(player -> Log.i("mediaPlayer", "Alarm complete"));
                mediaPlayer.setOnErrorListener((player, i, i1) -> {
                    Log.d("mediaPlayer", "Alarm error: " + i + " : " + i1);
                    return false;
                });
                return mediaPlayer;
            } catch (IllegalArgumentException e) {
                Log.d("mediaPlayer", "IllegalArgumentException playing alarm: " + e.getMessage());
                e.printStackTrace();
            } catch (SecurityException e) {
                Log.d("mediaPlayer", "SecurityException playing alarm: " + e.getMessage());
                e.printStackTrace();
            } catch (IllegalStateException e) {
                Log.d("mediaPlayer", "IllegalStateException playing alarm: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return null;
    }
}



