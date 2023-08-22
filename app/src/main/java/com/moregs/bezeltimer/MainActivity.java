package com.moregs.bezeltimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
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

import androidx.constraintlayout.widget.ConstraintLayout;
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
    // Action for updating the display in ambient mode, per our custom refresh cycle.
    private static final String AMBIENT_UPDATE_ACTION = "com.moregs.bezeltimer.action.AMBIENT_UPDATE";
    private static final long one_second = 1000;
    private static final long one_minute = 60 * one_second;
    private static final long one_hour = 60 * one_minute;
    
    private EditMode step_selection_mode = EditMode.SMART;
    private MediaPlayer alarm_player = null;
    private State state = State.READY;
    
    private AlarmManager ambientUpdateAlarmManager;
    private BroadcastReceiver ambientUpdateBroadcastReceiver;
    private CountDownTimer timer;
    private ImageButton start_button;
    private ImageButton stop_button;
    private ImageView background;
    private ImageView warning_sign;
    private PendingIntent ambientUpdatePendingIntent;
    private RadialProgress progress_bar;
    private SharedPreferences settings;
    private TextView step_setting;
    private TextView time_re;
    private long remaining_time;
    private long selected_time;
    
    Executor mainExecutor;

    /* AMBIENT MODE SECTION */

    /**
     * Sets the class to use to handle ambient mode transitions
     */
    @Override
    public AmbientModeSupport.AmbientCallback getAmbientCallback() {
        return new MyAmbientCallback();
    }

    /**
     * Sets the behaviour to handle ambient mode transitions
     */
    private class MyAmbientCallback extends AmbientModeSupport.AmbientCallback {
        /**
         * Hides large element and dims screen when entering ambient mode.
         */
        @Override
        public void onEnterAmbient(Bundle ambientDetails) {
            // Handle entering ambient mode
            super.onEnterAmbient(ambientDetails);
            Log.i("Ambient", "Enter");
            start_button.setVisibility(View.GONE);
            stop_button.setVisibility(View.GONE);
            time_re.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.ambient));
            background.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(getApplicationContext(), R.color.ambient)));
            refreshDisplayAndSetNextUpdate();
        }

        /**
         * Re-shows hidden elements and brightens screen on exiting ambient mode.
         */
        @Override
        public void onExitAmbient() {
            // Handle exiting ambient mode
            super.onExitAmbient();
            ambientUpdateAlarmManager.cancel(ambientUpdatePendingIntent);
            Log.i("Ambient", "Exit");
            checkDoneWakeUp();
            start_button.setVisibility(View.VISIBLE);
            stop_button.setVisibility(View.VISIBLE);
            time_re.setTextColor(ContextCompat.getColor(getApplicationContext(), android.R.color.white));
            background.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(getApplicationContext(), android.R.color.white)));
            time_re.setVisibility(View.VISIBLE);
        }

        /**
         * Make sure countdown and progress are still shown in ambient mode.
         */
        @Override
        public void onUpdateAmbient() {
            // Update the content
            super.onUpdateAmbient();
            Log.i("Ambient", "Update");
            refreshDisplayAndSetNextUpdate();
        }
    }

    /**
     * During ambient mode, only update the display occasionally to preserve battery.
     * These timers are imprecise so this applies a fudge factor based on the difference between the current time and 
     * the time we expect it to be so that overall we stay close to the expected time.
     */
    private void refreshDisplayAndSetNextUpdate() {
        if (state == State.DONE) {
            float max = 0.8f;
            float min = 0.2f;
            float x = (float) (Math.random() * (max - min)) + min;
            float y = (float) (Math.random() * (max - min)) + min;
            float r = (float) Math.random() * 360.0f;
            setWarningLocation(x, y, r);
        }
        // Schedule a new alarm
        long timeMs = System.currentTimeMillis();
        // Calculate the next trigger time - try to adjust for slow previous updates by shortening next update window
        long delayMs = one_second - (timeMs % one_second);
        Log.i("Main", "refreshing ambient in: " + delayMs);
        long triggerTimeMs = timeMs + delayMs;
        ambientUpdateAlarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, ambientUpdatePendingIntent);
    }

    /**
     * Handle resuming a paused timer, re-enable ambient behaviours and status updates
     */
    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(AMBIENT_UPDATE_ACTION);
        registerReceiver(ambientUpdateBroadcastReceiver, filter);
    }

    /**
     * Handle pausing (don't enter ambient mode and pause status updates)
     */
    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(ambientUpdateBroadcastReceiver);
        ambientUpdateAlarmManager.cancel(ambientUpdatePendingIntent);
    }

    /* PHYSICAL CONTROLS */

    /**
     * Detect, filter and handle changes to the position of the rotary encoder.
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        checkDoneWakeUp();
        List<State> no_input = Arrays.asList(State.RUNNING, State.PAUSED);
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
            if (scroll_delta > 0) {
                rotateClockwise();
            } else if (scroll_delta < 0) {
                rotateCounterClockwise();
            }
            // Clamp minimum time value
            if (selected_time < 1 * one_second) {
                selected_time = 0;
            }
            updateTimeDisplay(selected_time);
            setProgress(selected_time);
            return true;
        }
        return false;
    }

    /**
     * Handle rotating the bezel clockwise this increases the amount of time on the timer.
     * In smart mode the amount adjusted for each step of the bezel depends on the current length of the timer.
     * This behaviour is summarized in README.mc
     */
    public void rotateClockwise() {
        switch (step_selection_mode) {
            case SMART:
                if (selected_time < one_minute) {
                    selected_time += 5 * one_second;
                    selected_time -= selected_time % (5 * one_second);
                } else if (selected_time < 5 * one_minute) {
                    selected_time += 30 * one_second;
                    selected_time -= selected_time % (30 * one_second);
                } else if (selected_time < one_hour) {
                    selected_time += one_minute;
                    selected_time -= selected_time % (one_minute);
                } else {
                    selected_time += 5 * one_minute;
                    selected_time -= selected_time % (5 * one_minute);
                }
                break;
            case HOURS:
                selected_time += one_hour;
                break;
            case MINUTES:
                selected_time += one_minute;
                break;
            case SECONDS:
                selected_time += one_second;
                break;
        }
    }

    /**
     * Handle rotating the bezel anti-clockwise this reduces the amount of time on the timer.
     * In smart mode the amount adjusted for each step of the bezel depends on the current length of the timer.
     * This behaviour is summarized in README.mc
     */
    public void rotateCounterClockwise() {
        switch (step_selection_mode) {
            case SMART:
                if (selected_time > one_hour) {
                    selected_time -= 5 * one_minute;
                    selected_time -= selected_time % (5 * one_minute);
                } else if (selected_time > 5 * one_minute) {
                    selected_time -= one_minute;
                    selected_time -= selected_time % (one_minute);
                } else if (selected_time > one_minute) {
                    selected_time -= 30 * one_second;
                    selected_time -= selected_time % (30 * one_second);
                } else {
                    selected_time -= 5 * one_second;
                    selected_time -= selected_time % (5 * one_second);
                }
                break;
            case HOURS:
                selected_time -= one_hour;
                break;
            case MINUTES:
                selected_time -= one_minute;
                break;
            case SECONDS:
                selected_time -= one_second;
                break;
        }
    }

    /* HELPER FUNCTIONS */

    /**
     * Start the timer and create an event to trigger the alarm when the timer ends.
     */
    public void timerStart(long time) {
        
        timer = new CountDownTimer(time, 25) {
            @Override
            public void onTick(long millisUntilFinished) {
                remaining_time = millisUntilFinished;
                setProgress(remaining_time);
                updateTimeDisplay(remaining_time);
            }

            @Override
            public void onFinish() {
                // execution is finished, we set default values
                mainExecutor.execute(() -> {
                    alarm_player.start();
                    state = State.DONE;
                    setWarningLocation(0.5f, 0.5f, 0.0f);
                    warning_sign.setVisibility(View.VISIBLE);
                    start_button.setVisibility(View.GONE);
                    stop_button.setVisibility(View.GONE);
                    time_re.setVisibility(View.GONE);
                });
            }
        };
        timer.start();
    }

    /**
     * Draw a warning sign at a given x,y position with r rotation.
     */
    private void setWarningLocation(float x, float y, float r) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) warning_sign.getLayoutParams();
        params.horizontalBias = x;
        params.verticalBias = y;
        warning_sign.setLayoutParams(params);
        warning_sign.setRotation(r);
    }

    /**
     * If the alarm has gone off (state == DONE) and the watch is waking up.
     * Reset to the timer selection (state == READY) state
     */
    private void checkDoneWakeUp() {
        if (state == State.DONE) {
            warning_sign.setVisibility(View.GONE);
            time_re.setVisibility(View.VISIBLE);
            start_button.setVisibility(View.VISIBLE);
            resetTimer();
            state = State.READY;
            step_setting.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Handle behaviour of play/pause button being pressed dependent on state.
     */
    private void play_pause() {
        stop_button.setImageResource(R.drawable.ic_baseline_cancel_24);
        if (state == State.PAUSED) {
            // unpause
            state = State.RUNNING;
            timerStart(remaining_time);
            start_button.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
            step_setting.setVisibility(View.GONE);
        } else if (state == State.RUNNING) {
            // pause
            state = State.PAUSED;
            timer.cancel();
            start_button.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
            step_setting.setVisibility(View.VISIBLE);
            step_setting.setText(getString(R.string.edit_mode_pause));
        } else {
            // start
            state = State.RUNNING;
            stop_button.setVisibility(View.VISIBLE);
            start_button.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
            step_setting.setVisibility(View.GONE);
            timerStart(selected_time);

            SharedPreferences.Editor editor = settings.edit();
            editor.putLong("last_selected_time", selected_time);
            editor.apply();
        }
    }

    /**
     * Updates remaining time display including a small movement to reduce chance of screen burn in.
     */
    private void updateTimeDisplay(long time) {
        // we update the counter during the execution
        double remainingSeconds = Math.ceil((double) time / one_second);
        double remainingMinutes = Math.floor(remainingSeconds / 60);
        long hours = (long) (remainingMinutes / 60);
        long minutes = (long) (remainingMinutes % 60);
        long seconds = (long) (remainingSeconds % 60);

        if (hours > 0.0) {
            time_re.setText(getString(R.string.time_remaining_hours, hours, minutes, seconds));
        } else {
            time_re.setText(getString(R.string.time_remaining_minutes, minutes, seconds));
        }

        // Burn in protection by rotating remaining time
        float end_angle = 0.0f;
        if (state == State.RUNNING) {
            end_angle = 360.0f - ((selected_time - remaining_time) * 360.0f / selected_time);
            end_angle = 20.0f * (float) Math.sin(Math.toRadians(end_angle));
        }
        time_re.setRotation(end_angle);
    }

    /**
     * Update text display based on current edit mode.
     */
    private void updateTextLabel() {
        switch (step_selection_mode) {
            case SMART:
                step_setting.setText(getString(R.string.edit_mode_smart));
                break;
            case MINUTES:
                step_setting.setText(getString(R.string.edit_mode_minutes));
                break;
            case SECONDS:
                step_setting.setText(getString(R.string.edit_mode_seconds));
                break;
            case HOURS:
                step_setting.setText(getString(R.string.edit_mode_hours));
                break;
        }
    }

    /**
     * Update the progress bar, the maximum changes based on the range the timer is operating over.
     */
    private void setProgress(long time) {
        long max = one_minute;
        if (time > one_hour) {
            max = 12 * one_hour;
        } else if (time > one_minute) {
            max = one_hour;
        }
        progress_bar.setProgress_max(max);
        progress_bar.setProgress(time);
    }

    /**
     * Called to reset the timer's state after cancelling or ending
     */
    protected void resetTimer() {
        setProgress(selected_time);
        stop_button.setImageResource(R.drawable.ic_baseline_cancel_disabled_24);
        start_button.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
        updateTimeDisplay(selected_time);
        updateTextLabel();
    }

    /**
     * Called on app start to set up event listeners and create all on screen elements.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("Main", "Created");
        com.moregs.bezeltimer.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Handle ambient updates
        mainExecutor = ContextCompat.getMainExecutor(this);
        AmbientModeSupport.attach(this);
        ambientUpdateAlarmManager =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent ambientUpdateIntent = new Intent(AMBIENT_UPDATE_ACTION);
        ambientUpdatePendingIntent = PendingIntent.getBroadcast(
                this, 0, ambientUpdateIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ambientUpdateBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshDisplayAndSetNextUpdate();
            }
        };

        // Load settings
        settings = this.getPreferences(Context.MODE_PRIVATE);
        selected_time = settings.getLong("last_selected_time", one_minute);

        // Find GUI components
        time_re = findViewById(R.id.time_re);
        step_setting = findViewById(R.id.step_setting);
        start_button = findViewById(R.id.start_button);
        stop_button = findViewById(R.id.stop_button);
        Button hour_mode_button = findViewById(R.id.hour_mode_button);
        Button minute_mode_button = findViewById(R.id.minute_mode_button);
        Button second_mode_button = findViewById(R.id.second_mode_button);
        progress_bar = findViewById(R.id.progress);
        warning_sign = findViewById(R.id.warning_sign);
        background = findViewById(R.id.background);

        // Set initial conditions
        background.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(getApplicationContext(), android.R.color.white)));
        alarm_player = new AudioOutput(this).prepare_alarm_player();
        step_setting.setVisibility(View.VISIBLE);
        warning_sign.setVisibility(View.GONE);
        resetTimer();

        // Create on click event listeners
        hour_mode_button.setOnClickListener(view -> {
            if (state == State.DONE) {
                checkDoneWakeUp();
            } else {
                // Cycle modes
                switch (step_selection_mode) {
                    case HOURS:
                        step_selection_mode = EditMode.SMART;
                        break;
                    default:
                        step_selection_mode = EditMode.HOURS;
                        break;
                }
                updateTextLabel();
            }
        });

        minute_mode_button.setOnClickListener(view -> {
            if (state == State.DONE) {
                checkDoneWakeUp();
            } else {
                // Cycle modes
                switch (step_selection_mode) {
                    case MINUTES:
                        step_selection_mode = EditMode.SMART;
                        break;
                    default:
                        step_selection_mode = EditMode.MINUTES;
                        break;
                }
                updateTextLabel();
            }
        });

        second_mode_button.setOnClickListener(view -> {
            if (state == State.DONE) {
                checkDoneWakeUp();
            } else {
                // Cycle modes
                switch (step_selection_mode) {
                    case SECONDS:
                        step_selection_mode = EditMode.SMART;
                        break;
                    default:
                        step_selection_mode = EditMode.SECONDS;
                        break;
                }
                updateTextLabel();
            }
        });

        stop_button.setOnClickListener(view -> {
            Log.i("Main", "Stop button pressed");
            if (state == State.RUNNING || state == State.PAUSED) {
                // we cancel the countdown timer execution when user click on the stop button
                timer.cancel();
                state = State.READY;
            }
            step_setting.setVisibility(View.VISIBLE);
            resetTimer();
        });

        start_button.setOnClickListener(view -> {
            Log.i("Main", "Play / Pause button pressed");
            play_pause();
        });
    }
}