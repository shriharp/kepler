package com.prj.keplerv0;

/**
 * Pure on-device spherical astronomy calculator.
 *
 * Determines whether a star is above the local horizon given the observer's
 * GPS coordinates and the current UTC time — no network or external API needed.
 *
 * Algorithm:
 *  1. UTC millis → Julian Date (JD)
 *  2. JD → Greenwich Mean Sidereal Time (GMST) in degrees
 *  3. GMST + longitude → Local Sidereal Time (LST)
 *  4. LST − RA → Hour Angle (HA)
 *  5. Altitude = asin( sin(lat)·sin(dec) + cos(lat)·cos(dec)·cos(HA) )
 *
 * Reference: Meeus, "Astronomical Algorithms", Chapter 12.
 */
public class SkyCalculator {

    /**
     * Returns the altitude of a star above the local horizon in degrees.
     * Positive values mean the star is above the horizon.
     *
     * @param raDeg     Star's Right Ascension in degrees [0, 360)
     * @param decDeg    Star's Declination in degrees [−90, +90]
     * @param latDeg    Observer latitude  in degrees (negative = south)
     * @param lonDeg    Observer longitude in degrees (negative = west)
     * @param utcMillis Current UTC time in milliseconds (System.currentTimeMillis())
     * @return Altitude in degrees; positive = above the horizon
     */
    public static double altitude(double raDeg, double decDeg,
                                  double latDeg, double lonDeg,
                                  long utcMillis) {

        // 1. Julian Date
        double jd = utcMillis / 86_400_000.0 + 2_440_587.5;

        // 2. Greenwich Mean Sidereal Time in degrees
        //    d  = days since J2000.0 epoch (2000-Jan-01 12:00 TT)
        //    T  = Julian centuries from J2000.0
        double d    = jd - 2_451_545.0;
        double T    = d  / 36_525.0;
        double gmst = 280.46061837
                    + 360.98564736629 * d          // mean rotation
                    + T * T * 0.000387933           // second-order correction
                    - T * T * T / 38_710_000.0;    // third-order correction
        gmst = normDeg(gmst);

        // 3. Local Sidereal Time
        double lst = normDeg(gmst + lonDeg);

        // 4. Hour Angle of the star
        double ha = normDeg(lst - raDeg);

        // 5. Altitude via the spherical triangle formula
        double latR = Math.toRadians(latDeg);
        double decR = Math.toRadians(decDeg);
        double haR  = Math.toRadians(ha);

        double sinAlt = Math.sin(latR) * Math.sin(decR)
                      + Math.cos(latR) * Math.cos(decR) * Math.cos(haR);

        // Clamp sinAlt to [-1, 1] to guard against floating-point rounding
        sinAlt = Math.max(-1.0, Math.min(1.0, sinAlt));

        return Math.toDegrees(Math.asin(sinAlt));
    }

    /**
     * Returns the zenith direction as a unit vector in the celestial Cartesian frame.
     * The zenith is the point directly overhead: its RA equals the Local Sidereal Time
     * and its Dec equals the observer's latitude.
     *
     * This vector is used by {@link StarRenderer} to orient the horizon ring.
     *
     * @return float[3] = {x, y, z} in the same coordinate system as the star positions
     */
    public static float[] zenithDirection(double latDeg, double lonDeg, long utcMillis) {
        double jd   = utcMillis / 86_400_000.0 + 2_440_587.5;
        double d    = jd - 2_451_545.0;
        double T    = d  / 36_525.0;
        double gmst = 280.46061837
                    + 360.98564736629 * d
                    + T * T * 0.000387933
                    - T * T * T / 38_710_000.0;
        double lst = normDeg(gmst + lonDeg);

        double latR = Math.toRadians(latDeg);
        double lstR = Math.toRadians(lst);

        // Zenith RA = LST, Dec = latitude → Cartesian
        return new float[]{
            (float)(Math.cos(latR) * Math.cos(lstR)),
            (float)(Math.cos(latR) * Math.sin(lstR)),
            (float) Math.sin(latR)
        };
    }

    /** Normalises an angle (in degrees) to the range [0, 360). */
    private static double normDeg(double deg) {
        deg = deg % 360.0;
        return deg < 0 ? deg + 360.0 : deg;
    }

    /**
     * Returns a 4x4 matrix mapping Equatorial coordinates to Topocentric coordinates.
     */
    public static float[] equatorialToTopocentricMatrix(double latDeg, double lonDeg, long utcMillis) {
        float[] mat = new float[16];
        android.opengl.Matrix.setIdentityM(mat, 0);

        if (Double.isNaN(latDeg) || Double.isNaN(lonDeg)) {
            return mat; // Identity fallback
        }

        double jd   = utcMillis / 86_400_000.0 + 2_440_587.5;
        double d    = jd - 2_451_545.0;
        double T    = d  / 36_525.0;
        double gmst = 280.46061837
                    + 360.98564736629 * d
                    + T * T * 0.000387933
                    - T * T * T / 38_710_000.0;
        double lst = normDeg(gmst + lonDeg);

        double latR = Math.toRadians(latDeg);
        double lstR = Math.toRadians(lst);

        float cosLat = (float)Math.cos(latR);
        float sinLat = (float)Math.sin(latR);
        float cosLst = (float)Math.cos(lstR);
        float sinLst = (float)Math.sin(lstR);

        // X_topo (East) in Eq
        float x1 = -sinLst;
        float x2 = cosLst;
        float x3 = 0f;

        // Y_topo (North) in Eq
        float y1 = -sinLat * cosLst;
        float y2 = -sinLat * sinLst;
        float y3 = cosLat;

        // Z_topo (Zenith) in Eq
        float z1 = cosLat * cosLst;
        float z2 = cosLat * sinLst;
        float z3 = sinLat;

        // M maps Eq to Topo (rows are the Eq vectors of the Topo basis)
        mat[0] = x1; mat[4] = x2; mat[8]  = x3; mat[12] = 0;
        mat[1] = y1; mat[5] = y2; mat[9]  = y3; mat[13] = 0;
        mat[2] = z1; mat[6] = z2; mat[10] = z3; mat[14] = 0;
        mat[3] = 0;  mat[7] = 0;  mat[11] = 0;  mat[15] = 1;

        return mat;
    }
}
