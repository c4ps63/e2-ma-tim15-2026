package com.example.slagalicavpl.util;

/**
 * Normalizuje srpski tekst za poređenje odgovora.
 * Prihvata i osišanu latinicu (bez dijakritika):
 *   š → s,  č/ć → c,  ž → z,  đ → dj
 */
public final class StringNormalizer {

    private StringNormalizer() {}

    /** Vraća normalizovanu verziju teksta (uppercase, bez dijakritika). */
    public static String normalize(String text) {
        if (text == null) return "";
        return text.trim()
                .toUpperCase()
                .replace("Š", "S")
                .replace("Č", "C")
                .replace("Ć", "C")
                .replace("Ž", "Z")
                .replace("ĐJ", "DJ")   // guard against double-mapping Đ → Dj → Dj
                .replace("Đ",  "DJ")
                .replace("DŽ", "DZ");
    }

    /**
     * Poređenje odgovora koje prihvata i originalni zapis i osišanu latinicu.
     * Primjeri koji prolaze za rješenje "ŽUTA":
     *   "žuta", "ŽUTA", "zuta", "ZUTA"
     */
    public static boolean matches(String input, String answer) {
        return normalize(input).equals(normalize(answer));
    }
}
