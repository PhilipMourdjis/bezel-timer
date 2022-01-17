package com.example.bezeltimer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.InputDevice;
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

import com.example.bezeltimer.databinding.ActivityMainBinding;

import java.util.concurrent.Executor;

public class MainActivity extends Activity {
    private ActivityMainBinding binding;
    Executor mainExecutor;
    private ImageButton start_button;
    private ImageButton stop_button;
    private Button mode_button;
    private boolean running = false;
    private boolean paused = false;
    private CountDownTimer countDownTimer;
    private TextView time_re;
    private RadialProgress progress_bar;
    private Integer start_color;
    private Integer stop_color;
    private Integer progress_color;
    private boolean mode_seconds = false;
    private final long one_minute = 60000;
    private final long one_second = 1000;
    private long selected_time = one_minute;
    private long remaining_time;

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (running) {
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

    public void timerStart(long time) {
        // startService(new Intent(this, BackgroundTimer.class));
        countDownTimer = new CountDownTimer(time, 25) {
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
                        resetTimer();
                    }
                });
            }
        };
        countDownTimer.start();
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
    }

    private void resetTimer() {
        running = false;
        paused = false;
        progress_bar.setProgress(selected_time);
        stop_button.setVisibility(View.GONE);
        start_button.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
        updateTimeDisplay(selected_time);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mainExecutor = ContextCompat.getMainExecutor(this);

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
            }
        });

        stop_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (running) {
                    // we cancel the countdown timer execution when user click on the stop button
                    countDownTimer.cancel();
                    resetTimer();
                    progress_bar.setProgress_color(progress_color);
                }
            }
        });

        start_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (running && paused) {
                    // unpause
                    paused = false;
                    timerStart(remaining_time);
                    start_button.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
                    progress_bar.setProgress_color(start_color);
                } else if (running) {
                    // pause
                    paused = true;
                    countDownTimer.cancel();
                    start_button.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
                    progress_bar.setProgress_color(progress_color);
                } else {
                    // start
                    running = true;
                    paused = false;
                    stop_button.setVisibility(View.VISIBLE);
                    start_button.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);

                    progress_bar.setProgress_max(selected_time);
                    progress_bar.setProgress_color(start_color);
                    timerStart(selected_time);
                }
            }
        });
    }
}