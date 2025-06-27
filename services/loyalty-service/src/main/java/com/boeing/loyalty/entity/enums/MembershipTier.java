package com.boeing.loyalty.entity.enums;

import org.springframework.beans.factory.annotation.Value;

public enum MembershipTier {
    SILVER,GOLD,PLATINUM;

    @Value("${membership.silver.points}")
    private static int silverPoints;
    @Value("${membership.gold.points}")
    private static int goldPoints;
    @Value("${membership.platinum.points}")
    private static int platinumPoints;

    public static MembershipTier fromPoints(int points) {
        if (points < silverPoints) {
            return SILVER;
        } else if (points < goldPoints) {
            return GOLD;
        } else {
            return PLATINUM;
        }
    }

}
