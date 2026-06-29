package com.example.slagalicavpl.model;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class SerbiaRegions {

    public static final class Region {
        public final String  id;
        public final String  displayName;
        public final String  icon;
        public final int     fillColor;
        public final float[] polygon;   // normalized [0-1] x,y pairs
        public final RectF   dotBounds; // area for random dot placement

        public Region(String id, String displayName, String icon,
                      int fillColor, float[] polygon, RectF dotBounds) {
            this.id          = id;
            this.displayName = displayName;
            this.icon        = icon;
            this.fillColor   = fillColor;
            this.polygon     = polygon;
            this.dotBounds   = dotBounds;
        }

        public PointF getDotForUser(String uid) {
            Random rng = new Random(uid.hashCode());
            float x = dotBounds.left + rng.nextFloat() * dotBounds.width();
            float y = dotBounds.top  + rng.nextFloat() * dotBounds.height();
            return new PointF(x, y);
        }
    }

    private static final Map<String, Region> MAP = new LinkedHashMap<>();

    public static final String VOJVODINA      = "vojvodina";
    public static final String BEOGRAD        = "beograd";
    public static final String SUMADIJA_ZAPAD = "sumadija_zapad";
    public static final String RASKA_ZLATIBOR = "raska_zlatibor";
    public static final String JUZNA_ISTOCNA  = "juzna_istocna";

    // ─────────────────────────────────────────────────────────────────────────
    // Normalized coordinate system
    //   Bounding box: lat 41.85–46.18 (height 4.33°), lon 18.82–23.00 (width 4.18°)
    //   x = (lon − 18.82) / 4.18   (0 = west, 1 = east)
    //   y = (46.18 − lat) / 4.33   (0 = north, 1 = south)
    //
    // Serbia is taller than wide in km (~480 × ~330), aspect ≈ 0.69.
    // SerbiaMapView applies letterboxing so the shape is never stretched.
    //
    // Shared internal border vertices (prefixed for clarity):
    //   A = (0.05, 0.34)  Sava/Vojvodina SW corner
    //   B = (0.22, 0.32)  Vojvodina-Beograd-Šumadija triple
    //   C = (0.46, 0.30)  Vojvodina-Beograd-Južna triple
    //   D = (0.63, 0.22)  Vojvodina NE / Romania border (Iron Gates north)
    //   E = (0.22, 0.50)  Beograd SW / Šumadija NE
    //   F = (0.46, 0.52)  Beograd SE / Šumadija E / Južna W
    //   G = (0.44, 0.72)  Šumadija-Raška-Južna triple
    //   H = (0.28, 0.72)  Šumadija-Raška boundary mid
    //   I = (0.16, 0.70)  Šumadija-Raška boundary W
    //   J = (0.08, 0.70)  W outer / Raška-Šumadija start
    //   K = (0.30, 1.00)  Raška-Južna S border point
    // ─────────────────────────────────────────────────────────────────────────

    static {
        // ── 1. VOJVODINA ─────────────────────────────────────────────────────
        // North flat Hungarian border, then NE Romanian border.
        // Internal south border follows Sava-Danube: D→C→B→A
        MAP.put(VOJVODINA, new Region(
                VOJVODINA, "Vojvodina", "🌾",
                Color.parseColor("#E8A020"),
                new float[]{
                    // outer N border (Hungarian border, almost flat)
                    0.00f,0.08f,  0.02f,0.03f,  0.17f,0.00f,
                    0.34f,0.00f,  0.50f,0.07f,
                    // NE – Romanian border starts, Iron Gates north = D
                    0.63f,0.22f,
                    // internal S border: D→C→B→A (Danube/Sava going west)
                    0.46f,0.30f,  0.22f,0.32f,  0.05f,0.34f,
                    // outer NW going back
                    0.02f,0.22f
                },
                new RectF(0.06f, 0.04f, 0.55f, 0.28f)
        ));

        // ── 2. BEOGRAD ────────────────────────────────────────────────────────
        // Small quadrilateral at the Sava-Danube confluence.
        // Shares top with Vojvodina (B→C), left+bottom with Šumadija, right with Južna (closes C→B via F→E).
        MAP.put(BEOGRAD, new Region(
                BEOGRAD, "Beograd", "🏙️",
                Color.parseColor("#C0392B"),
                new float[]{
                    0.22f,0.32f,  0.46f,0.30f,  // top (B→C), shared with Vojvodina
                    0.46f,0.52f,                  // right (C→F), shared with Južna
                    0.22f,0.50f                   // bottom-left (F→E→B), shared with Šumadija
                },
                new RectF(0.24f, 0.33f, 0.44f, 0.49f)
        ));

        // ── 3. ŠUMADIJA I ZAPADNA SRBIJA ─────────────────────────────────────
        // Center-west. Borders: Vojvodina (N), Beograd (NE), Južna (E), Raška (S), outer W.
        MAP.put(SUMADIJA_ZAPAD, new Region(
                SUMADIJA_ZAPAD, "Šumadija i Zapadna Srbija", "🌲",
                Color.parseColor("#27AE60"),
                new float[]{
                    // top: A→B shared with Vojvodina
                    0.05f,0.34f,  0.22f,0.32f,
                    // right of Beograd: B→E→F shared with Beograd left+bottom
                    0.22f,0.50f,  0.46f,0.52f,
                    // east border with Južna: F→G
                    0.44f,0.72f,
                    // south border: G→H→I→J shared with Raška top
                    0.28f,0.72f,  0.16f,0.70f,  0.08f,0.70f,
                    // outer W border going north: J→...→A
                    0.06f,0.58f,  0.10f,0.44f
                },
                new RectF(0.07f, 0.36f, 0.42f, 0.68f)
        ));

        // ── 4. RAŠKA I ZLATIBOR ───────────────────────────────────────────────
        // Southwest mountainous region.
        // Borders: Šumadija (N), Južna (E), outer SW (Bosnia/Montenegro border).
        MAP.put(RASKA_ZLATIBOR, new Region(
                RASKA_ZLATIBOR, "Raška i Zlatibor", "⛰️",
                Color.parseColor("#8E5F3A"),
                new float[]{
                    // top: J→I→H→G shared with Šumadija south
                    0.08f,0.70f,  0.16f,0.70f,  0.28f,0.72f,  0.44f,0.72f,
                    // east border with Južna: G→K (internal)
                    0.40f,0.84f,  0.34f,0.96f,  0.30f,1.00f,
                    // outer SW border: K→…→J (Bosnia/Montenegro)
                    0.18f,0.88f,  0.12f,0.80f
                },
                new RectF(0.10f, 0.72f, 0.40f, 0.92f)
        ));

        // ── 5. JUŽNA I ISTOČNA SRBIJA ─────────────────────────────────────────
        // Large SE region. Borders: Vojvodina/Beograd/Šumadija/Raška (W), outer E+S.
        MAP.put(JUZNA_ISTOCNA, new Region(
                JUZNA_ISTOCNA, "Južna i Istočna Srbija", "🏔️",
                Color.parseColor("#2980B9"),
                new float[]{
                    // top-left: C (where Vojvodina and Beograd meet Južna)
                    0.46f,0.30f,
                    // NE: D (Iron Gates / Romanian border heading south)
                    0.63f,0.22f,
                    // outer E border going SE (Romanian then Bulgarian border)
                    0.70f,0.34f,  0.88f,0.40f,  0.97f,0.56f,  0.97f,0.66f,
                    // SE corner – North Macedonia border
                    0.78f,0.76f,  0.72f,0.86f,  0.64f,1.00f,
                    // S outer going west to K
                    0.30f,1.00f,
                    // internal N border with Raška: K→G (reversed of Raška east)
                    0.34f,0.96f,  0.40f,0.84f,  0.44f,0.72f,
                    // internal N border with Šumadija: G→F (reversed of Šumadija east)
                    0.46f,0.52f
                    // path closes back to C (0.46,0.30) — right side of Beograd
                },
                new RectF(0.50f, 0.34f, 0.93f, 0.92f)
        ));
    }

    public static Map<String, Region> all()           { return MAP; }
    public static Region              get(String id)  { return MAP.get(id); }

    public static String idFromDisplayName(String name) {
        if (name == null) return BEOGRAD;
        for (Region r : MAP.values())
            if (r.displayName.equalsIgnoreCase(name.trim())) return r.id;
        if (MAP.containsKey(name.toLowerCase().trim())) return name.toLowerCase().trim();
        return BEOGRAD;
    }

    public static String displayNameFromId(String id) {
        Region r = MAP.get(id);
        return r != null ? r.displayName : id;
    }
}
