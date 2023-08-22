package com.moregs.bezeltimer;

/**
 * The EditMode enum defines the modes in which the timer can be modified
 */
public enum EditMode {
    // Smart: Changes automatically based on current length of timer, see README.md
    SMART,

    // Hours: Change just the hours component of the timer
    HOURS,

    // Minutes: Change just the minutes component of the timer
    MINUTES,
    
     // Seconds: Change just the seconds component of the timer
    SECONDS
}
