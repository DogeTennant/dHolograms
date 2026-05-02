package com.dogetennant.dholograms.hologram;

public enum HologramFlag {
    DISABLE_UPDATING,      // skip PAPI refresh ticks
    DISABLE_PLACEHOLDERS,  // skip PAPI even if globally enabled
    DISABLE_ANIMATIONS,    // skip animation ticks for all lines
    DISABLE_ACTIONS;       // ignore click interactions

    public static HologramFlag fromString(String s) {
        try { return valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
