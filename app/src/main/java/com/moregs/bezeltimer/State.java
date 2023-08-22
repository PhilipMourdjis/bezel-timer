package com.moregs.bezeltimer;

/**
 * The State enum defines what the timer is currently doing
 * This is used to define what to do on state transitions
 */

public enum State {
    // Ready: Showing "Set Timer" and ready to start.
    READY,

    // Running: Timer currently counting down.
    RUNNING,

    // Paused: Timer has started but is currently paused.
    PAUSED,

    // Done: Alarm should go off.
    DONE
}
