package com.skiarkt.shooter.ui;

import android.widget.SeekBar;

/** SeekBar listener with no-op start / stop tracking; subclasses only implement onProgressChanged. */
abstract class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}
