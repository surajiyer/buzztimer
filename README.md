# BuzzTimer

BuzzTimer is an Android application that allows users to set up circular sequences of haptic feedback (vibrations) tuned to a timer.

## Features

- Add multiple timer intervals with minutes and seconds
- Edit or delete existing intervals
- Enable circular mode to repeat the sequence continuously
- Receive haptic feedback (vibration) when each interval completes
- Visual countdown of the current timer
- Visual count of the laps. 1 lap = 1 loop through all timers in a sequence.

## How to Use

1. **Add Timer Intervals**
   - Tap the "Add Interval" button to create a new timer interval
   - Enter the desired minutes and seconds
   - Save the interval to add it to the sequence

2. **Configure Sequence Mode**
   - Check the "Circular Sequence" option to make the timer loop continuously
   - Uncheck it to stop once all intervals are complete

3. **Control the Timer**
   - Press "Start" to begin the timer sequence
   - Press "Stop" to cancel the timer at any time

4. **Example Sequence**
   - If you set intervals [3m 0s, 2m 0s] with circular mode enabled:
     - The device will vibrate after the first 3 minutes
     - Then after 2 more minutes
     - Then the sequence repeats (vibrates after another 3 minutes, etc.)
   - With circular mode disabled, the timer will stop after completing the sequence once

## Requirements

- Android API 24 (Android 7.0 Nougat) or higher

## Development Setup

1. Clone this repository
2. Open the project in Android Studio
3. Sync Gradle files
4. Run on a physical device (for vibration feedback) or emulator
