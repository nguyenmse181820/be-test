package com.boeing.loyalty.entity.enums;

public enum MembershipTier {
    SILVER,GOLD,PLATINUM;

    // FIX 2: Removed @Value annotations from enum and implemented proper tier calculation
    // Enums cannot have @Value injected fields because they are loaded at class loading time
    // before Spring context is available. Using constants instead for business logic.
    // These values should be configurable via application properties and passed to the service layer
    private static final int SILVER_THRESHOLD = 0;
    private static final int GOLD_THRESHOLD = 200000;    // ~20M VND at 1% rate
    private static final int PLATINUM_THRESHOLD = 1400000; // ~100M VND total spending

    public static MembershipTier fromPoints(int points) {
        // FIX 2: Fixed tier calculation logic to use proper thresholds
        // Previous logic was backwards - checking if points < silverPoints would never work
        // because silverPoints is typically 0, so all users would be SILVER
        if (points >= PLATINUM_THRESHOLD) {
            return PLATINUM;
        } else if (points >= GOLD_THRESHOLD) {
            return GOLD;
        } else {
            return SILVER;
        }
    }

    public int getThreshold() {
        return switch (this) {
            case SILVER -> SILVER_THRESHOLD;
            case GOLD -> GOLD_THRESHOLD;
            case PLATINUM -> PLATINUM_THRESHOLD;
        };
    }
}
