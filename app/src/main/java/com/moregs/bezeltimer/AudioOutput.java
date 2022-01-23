package com.moregs.bezeltimer;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import java.util.Arrays;

public class AudioOutput {
    private final AudioManager audioManager;

    public AudioOutput(Context context) {
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
}

