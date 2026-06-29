package com.example.slagalicavpl.model;

public final class LeagueUtil {

    // Liga 0–5: nazivi i ikonice (proizvoljni izbor per spec 6a)
    private static final String[] NAMES  = {
        "Početnik", "Bronzani", "Srebrni", "Zlatni", "Dijamantski", "Legenda"
    };
    private static final String[] ICONS  = {
        "🔰", "🥉", "🥈", "🥇", "💎", "👑"
    };
    // Pragovi za ulazak u ligu (spec 6c): liga1=100, liga2=200, liga3=400, liga4=800, liga5=1600
    private static final int[] THRESHOLDS = { 0, 100, 200, 400, 800, 1600 };

    private LeagueUtil() {}

    /** Vraća indeks lige (0–5) na osnovu broja zvezda. */
    public static int getLeague(int stars) {
        int league = 0;
        for (int i = 1; i < THRESHOLDS.length; i++) {
            if (stars >= THRESHOLDS[i]) league = i;
            else break;
        }
        return league;
    }

    public static String getName(int league) {
        return NAMES[clamp(league)];
    }

    public static String getIcon(int league) {
        return ICONS[clamp(league)];
    }

    /** Naziv + ikonica u jednom stringu, npr. "🥇 Zlatni" */
    public static String getLabel(int league) {
        return getIcon(league) + " " + getName(league);
    }

    /**
     * Dnevni tokeni za ligu (spec 6b): 5 baznih + 1 po ligi.
     * Liga 0 → 5, liga 1 → 6, ..., liga 5 → 10.
     */
    public static int getDailyTokens(int league) {
        return 5 + clamp(league);
    }

    private static int clamp(int l) { return Math.max(0, Math.min(5, l)); }
}
