package com.dogetennant.dholograms.hologram.line;

public enum AnimationType {
    NONE,
    RAINBOW,   // full hue cycle across characters, phase shifts per tick
    WAVE,      // two-colour sine wave sweeping through text
    BURN,      // colour-front sweeps left-to-right revealing second colour
    TYPEWRITER,// characters revealed one-by-one, then resets
    SCROLL;    // sliding window through the text

    public static AnimationType fromString(String s) {
        try { return valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return NONE; }
    }
}
