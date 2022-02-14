package com.moregs.bezeltimer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.media.AudioDeviceInfo;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageButton;
import android.widget.ImageView;
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
    private ImageView warning_sign;
    private ImageView background;
    private State state = State.Ready;
    private boolean done_drawn = false;
    private TextView time_re;
    private TextView step_setting;
    private RadialProgress progress_bar;
    private boolean step_selection_mode = false;
    private final long one_minute = 60000;
    private final long one_second = 1000;
    private long time_delta;
    private long selected_time;
    private long remaining_time;
    private MediaPlayer alarm_player = null;

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
            Log.i("Ambient", "Enter");
            start_button.setVisibility(View.GONE);
            stop_button.setVisibility(View.GONE);
            time_re.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.ambient));
            background.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(getApplicationContext(), R.color.ambient)));
        }

        @Override
        public void onExitAmbient() {
            // Handle exiting ambient mode
            super.onExitAmbient();
            Log.i("Ambient", "Exit");
            warning_sign.setVisibility(View.GONE);
            start_button.setVisibility(View.VISIBLE);
            time_re.setTextColor(ContextCompat.getColor(getApplicationContext(), android.R.color.white));
            background.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(getApplicationContext(), android.R.color.white)));
            switch (state) {
                case Ready:
                    break;
                case Running:
                case Paused:
                    stop_button.setVisibility(View.VISIBLE);
                    break;
                case Done:
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
                    warning_sign.setVisibility(View.GONE);
                    done_drawn = false;
                } else {
                    warning_sign.setVisibility(View.VISIBLE);
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

            // choose size of timer steps
            if (step_selection_mode) {
                if (scroll_delta > 0) {
                    if (time_delta < one_minute) {
                        time_delta += one_second;
                    }
                    else {
                        time_delta += one_minute;
                    }
                } else if (scroll_delta < 0) {
                    if (time_delta > one_minute) {
                        time_delta -= one_minute;
                    }
                    else {
                        time_delta -= one_second;
                    }
                    if (time_delta < one_second) {
                        time_delta += one_second;
                    }
                }
                updateTimeDisplay(time_delta);
            }

            else {
                if (scroll_delta > 0) {
                    selected_time += time_delta;
                } else if (scroll_delta < 0) {
                    selected_time -= time_delta;
                    if (selected_time < time_delta) {
                        selected_time += time_delta;
                    }
                }
                updateTimeDisplay(selected_time);
                progress_bar.setProgress(selected_time);
            }
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
                        alarm_player.start();
                        state = State.Done;
                        resetTimer();
                    }
                });
            }
        };
        timer.start();
    }

    private MediaPlayer prepare_alarm_player(Context context) {
        Log.i("Main", "Sound the alarm");
        AudioOutput audioOutput = new AudioOutput(context);
        if (audioOutput.audioOutputAvailable(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)) {
            MediaPlayer mediaPlayer = MediaPlayer.create(context, R.raw.alarm2);

            try {
                mediaPlayer.setPreferredDevice(audioOutput.get_internal_speaker());
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer player) {
                        Log.i("mediaPlayer", "Prepared");
                    }
                });
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        Log.i("mediaPlayer", "Alarm complete");
                    }
                });
                mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                        Log.d("mediaPlayer", "Alarm error: " + i + " : " + i1);
                        return false;
                    }
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

    private void play_pause() {
        if (state == State.Paused) {
            // unpause
            state = State.Running;
            timerStart(remaining_time);
            start_button.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
        } else if (state == State.Running) {
            // pause
            state = State.Paused;
            timer.cancel();
            start_button.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
        } else {
            // start
            state = State.Running;
            stop_button.setVisibility(View.VISIBLE);
            start_button.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);

            progress_bar.setProgress_max(60 * one_minute);
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

        // Burn in protection by rotating remaining time
        float end_angle = 0.0f;
        if (state == State.Running) {
            end_angle = 360.0f - ((selected_time - remaining_time) * 360.0f / selected_time);
            end_angle = 20.0f * (float) Math.sin(Math.toRadians(end_angle));
        }
        time_re.setRotation(end_angle);
    }

    protected void resetTimer() {
        progress_bar.setProgress_max(60 * one_minute);
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
        ambientController.isAmbient();

        // Load settings
        settings = this.getPreferences(Context.MODE_PRIVATE);
        selected_time = settings.getLong("last_selected_time", one_minute);
        time_delta = settings.getLong("time_delta", one_minute / 2);

        // Find GUI components
        time_re = findViewById(R.id.time_re);
        step_setting = findViewById(R.id.step_setting);
        start_button = findViewById(R.id.start_button);
        stop_button = findViewById(R.id.stop_button);
        Button mode_button = findViewById(R.id.mode_button);
        progress_bar = findViewById(R.id.progress);
        warning_sign = findViewById(R.id.warning_sign);
        background = findViewById(R.id.background);

        // Set initial conditions
        background.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(getApplicationContext(), android.R.color.white)));
        alarm_player = prepare_alarm_player(this);
        step_setting.setVisibility(View.GONE);
        resetTimer();

        // Create on click event listeners
        mode_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("Main", "Mode button pressed");
                if (step_selection_mode) {
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putLong("time_delta", time_delta);
                    editor.apply();
                    step_setting.setVisibility(View.GONE);
                    updateTimeDisplay(selected_time);
                }
                else {
                    step_setting.setVisibility(View.VISIBLE);
                    updateTimeDisplay(time_delta);
                }
                step_selection_mode = !step_selection_mode;
            }
        });

        stop_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("Main", "Stop button pressed");
                // we cancel the countdown timer execution when user click on the stop button
                timer.cancel();
                state = State.Ready;
                resetTimer();
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