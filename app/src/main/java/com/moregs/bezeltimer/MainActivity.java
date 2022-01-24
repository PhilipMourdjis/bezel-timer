package com.moregs.bezeltimer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Button;

import androidx.core.content.ContextCompat;
import androidx.core.view.InputDeviceCompat;
import androidx.core.view.MotionEventCompat;
import androidx.core.view.ViewConfigurationCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.wear.ambient.AmbientModeSupport;

import com.moregs.bezeltimer.databinding.ActivityMainBinding;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

public class MainActivity extends FragmentActivity
        implements AmbientModeSupport.AmbientCallbackProvider {
    private ActivityMainBinding binding;
    private AmbientModeSupport.AmbientController ambientController;
    private SharedPreferences settings;
    Executor mainExecutor;
    private CountDownTimer timer;
    private ImageButton start_button;
    private ImageButton stop_button;
    private Button mode_button;
    private State state = State.Ready;
    private boolean is_ambient = false;
    private boolean done_drawn = false;
    private TextView time_re;
    private RadialProgress progress_bar;
    private Integer start_color;
    private Integer stop_color;
    private Integer progress_color;
    private boolean mode_seconds = false;
    private final long one_minute = 60000;
    private final long one_second = 1000;
    private long selected_time;
    private long remaining_time;

    /* AMBIENT MODE SECTION */

    @Override
    public AmbientModeSupport.AmbientCallback getAmbientCallback() {
        return new MyAmbientCallback();
    }

    private class MyAmbientCallback extends AmbientModeSupport.AmbientCallback {
        @Override
        public void onEnterAmbient(Bundle ambientDetails) {
            // Handle entering ambient mode
            super.onEnterAmbient(ambientDetails);
            is_ambient = true;

            Log.i("Ambient", "Enter");
            start_button.setVisibility(View.GONE);
            stop_button.setVisibility(View.GONE);
            progress_bar.setProgress_color(ContextCompat.getColor(getApplicationContext(), R.color.ambient));
            time_re.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.ambient));
        }

        @Override
        public void onExitAmbient() {
            // Handle exiting ambient mode
            super.onExitAmbient();
            is_ambient = false;

            Log.i("Ambient", "Exit");
            start_button.setVisibility(View.VISIBLE);
            time_re.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.white));
            switch (state) {
                case Ready:
                    progress_bar.setProgress_color(ContextCompat.getColor(getApplicationContext(), R.color.progress_color));
                    break;
                case Running:
                    stop_button.setVisibility(View.VISIBLE);
                    progress_bar.setProgress_color(ContextCompat.getColor(getApplicationContext(), R.color.start_color));
                    break;
                case Paused:
                    stop_button.setVisibility(View.VISIBLE);
                    progress_bar.setProgress_color(ContextCompat.getColor(getApplicationContext(), R.color.progress_color));
                    break;
                case Done:
                    progress_bar.setProgress_color(ContextCompat.getColor(getApplicationContext(), R.color.stop_color));
                    state = State.Ready;
                    break;
            }
            time_re.setVisibility(View.VISIBLE);
        }

        @Override
        public void onUpdateAmbient() {
            // Update the content
            super.onUpdateAmbient();
            Log.i("Ambient", "Update");
            if (state == State.Done) {
                time_re.setVisibility(View.GONE);
                if (done_drawn) {
                    progress_bar.setProgress_color(ContextCompat.getColor(getApplicationContext(), R.color.black));
                    done_drawn = false;
                } else {
                    progress_bar.setProgress_color(ContextCompat.getColor(getApplicationContext(), R.color.stop_color));
                    done_drawn = true;
                }
            }
        }
    }

    /* PHYSICAL CONTROLS */

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        List<State> no_input = Arrays.asList(State.Running, State.Paused);
        if (no_input.contains(state)) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
            // Don't forget the negation here
            Context context = getApplicationContext();
            float scroll_delta = -event.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
                    ViewConfigurationCompat.getScaledVerticalScrollFactor(
                            ViewConfiguration.get(context), context
                    );
            long time_delta = mode_seconds ? one_second : one_minute;

            // Swap these axes if you want to do horizontal scrolling instead
            if (scroll_delta > 0) {
                selected_time += time_delta;
            } else if (scroll_delta < 0) {
                selected_time -= time_delta;
                if (selected_time < time_delta) {
                    selected_time += time_delta;
                }
            }
            progress_bar.setProgress_color(progress_color);
            updateTimeDisplay(selected_time);
            return true;
        }
        return false;
    }

    /* HELPER FUNCTIONS */

    public void timerStart(long time) {
        // startService(new Intent(this, BackgroundTimer.class));
        timer = new CountDownTimer(time, 25) {
            @Override
            public void onTick(long millisUntilFinished) {
                remaining_time = millisUntilFinished;
                progress_bar.setProgress(millisUntilFinished);
                updateTimeDisplay(remaining_time);
            }

            @Override
            public void onFinish() {
                // execution is finished, we set default values
                mainExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        progress_bar.setProgress_color(stop_color);
                        sound_the_alarm();
                        state = State.Done;
                        resetTimer();
                    }
                });
            }
        };
        timer.start();
    }

    private void sound_the_alarm() {
        Log.i("Main", "Sound the alarm");
        AudioOutput audioOutput = new AudioOutput(getApplicationContext());
        if (audioOutput.audioOutputAvailable(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)) {
            MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.alarm2);

            try {
                mediaPlayer.setPreferredDevice(audioOutput.get_internal_speaker());
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer player) {
                        Log.i("Main", "Playing");
                        player.start();
                    }
                });
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        Log.i("Main", "Alarm complete");
                    }
                });
                mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                        Log.d("Main", "Alarm error: " + i + " : " + i1);
                        return false;
                    }
                });
            } catch (IllegalArgumentException e) {
                Log.d("Main", "IllegalArgumentException playing alarm: " + e.getMessage());
                e.printStackTrace();
            } catch (SecurityException e) {
                Log.d("Main", "SecurityException playing alarm: " + e.getMessage());
                e.printStackTrace();
            } catch (IllegalStateException e) {
                Log.d("Main", "IllegalStateException playing alarm: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void play_pause() {
        if (state == State.Paused) {
            // unpause
            state = State.Running;
            timerStart(remaining_time);
            start_button.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
            progress_bar.setProgress_color(start_color);
        } else if (state == State.Running) {
            // pause
            state = State.Paused;
            timer.cancel();
            start_button.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
            progress_bar.setProgress_color(progress_color);
        } else {
            // start
            state = State.Running;
            stop_button.setVisibility(View.VISIBLE);
            start_button.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);

            progress_bar.setProgress_max(selected_time);
            progress_bar.setProgress_color(start_color);
            timerStart(selected_time);

            SharedPreferences.Editor editor = settings.edit();
            editor.putLong("last_selected_time", selected_time);
            editor.apply();
        }
    }

    private void updateTimeDisplay(long time) {
        // we update the counter during the execution
        long remainingSeconds = time / one_second;
        long remainingMinutes = remainingSeconds / 60;
        long hours = remainingMinutes / 60;
        long minutes = remainingMinutes % 60;
        long seconds = remainingSeconds % 60;

        if (hours > 0) {
            time_re.setText(getString(R.string.time_remaining_hours, hours, minutes, seconds));
        } else {
            time_re.setText(getString(R.string.time_remaining_minutes, minutes, seconds));
        }

        float end_angle = 0.0f;
        end_angle = 360.0f - ((selected_time - remaining_time) * 360.0f / selected_time);
        end_angle = 45.0f * (float) Math.sin(Math.toRadians(end_angle));
        time_re.setRotation(end_angle);
    }

    protected void resetTimer() {
        progress_bar.setProgress(selected_time);
        stop_button.setVisibility(View.GONE);
        start_button.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
        updateTimeDisplay(selected_time);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("Main", "Created");
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mainExecutor = ContextCompat.getMainExecutor(this);
        ambientController = AmbientModeSupport.attach(this);
        is_ambient = ambientController.isAmbient();
        settings = this.getPreferences(Context.MODE_PRIVATE);
        selected_time = settings.getLong("last_selected_time", one_minute);

        time_re = findViewById(R.id.time_re);
        start_button = findViewById(R.id.start_button);
        stop_button = findViewById(R.id.stop_button);
        mode_button = findViewById(R.id.mode_button);
        progress_bar = findViewById(R.id.progress);
        start_color = ContextCompat.getColor(getApplicationContext(), R.color.start_color);
        stop_color = ContextCompat.getColor(getApplicationContext(), R.color.stop_color);
        progress_color = ContextCompat.getColor(getApplicationContext(), R.color.progress_color);

        resetTimer();

        mode_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mode_seconds = !mode_seconds;
                Log.i("Main", "Mode button pressed");
            }
        });

        stop_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("Main", "Stop button pressed");
                // we cancel the countdown timer execution when user click on the stop button
                timer.cancel();
                resetTimer();
                state = State.Ready;
                progress_bar.setProgress_color(progress_color);
            }
        });

        start_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("Main", "Play / Pause button pressed");
                play_pause();
            }
        });
    }
}