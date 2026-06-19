package com.example.slagalicavpl.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Animirani globus — kontinenti se pomijeraju horizontalno unutar kruga,
 * identično JSX earthScroll animaciji.
 */
public class GlobeView extends View {

    private static final long SCROLL_DURATION_MS = 28_000L;

    private final Paint oceanPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint landPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint landEdge       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Svi kontinenti u 200×200 koordinatnom prostoru
    private final List<Path> continents = new ArrayList<>();

    private float scrollFraction = 0f; // 0..1, jedna puna rotacija
    private long  lastFrameMs    = 0L;

    public GlobeView(Context ctx)                            { super(ctx);       init(); }
    public GlobeView(Context ctx, AttributeSet a)           { super(ctx, a);    init(); }
    public GlobeView(Context ctx, AttributeSet a, int s)    { super(ctx, a, s); init(); }

    private void init() {
        oceanPaint.setColor(0xFF3AA3E8);
        oceanPaint.setStyle(Paint.Style.FILL);

        landPaint.setColor(0xFF2FA84A);
        landPaint.setStyle(Paint.Style.FILL);

        landEdge.setColor(0xFF1E7A36);
        landEdge.setStyle(Paint.Style.STROKE);
        landEdge.setStrokeWidth(1.5f);

        shadowPaint.setColor(0xFF0D2B54);
        shadowPaint.setStyle(Paint.Style.FILL);

        highlightPaint.setColor(0x33FFFFFF);
        highlightPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(0xFF0D2B54);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(5f);

        buildPaths();
    }

    // ── Kontinenti ───────────────────────────────────────────────────────────

    private void buildPaths() {
        // Koordinate u 200×200 prostoru (isti viewport kao JSX)
        continents.add(poly(70,6,  88,4,  90,18, 80,24, 68,18));                                           // Grenland
        continents.add(poly(12,40, 27,30, 40,38, 47,55, 46,75, 39,88, 30,85, 24,98, 19,90, 14,72));       // Sjeverna Amerika
        continents.add(poly(52,30, 60,28, 61,40, 56,46, 51,40));                                           // Karibi
        continents.add(poly(35,92, 41,98, 42,110,39,116,35,110));                                          // Centralna Amerika
        continents.add(poly(42,118,52,120,55,140,50,165,44,178,39,170,37,148,40,128));                     // Južna Amerika
        continents.add(poly(132,38,150,34,159,44,154,56,144,58,136,52));                                   // Evropa
        continents.add(poly(158,36,183,40,192,58,185,80,172,84,162,76,158,58));                            // Evroazija
        continents.add(poly(164,78,174,80,170,98,164,96));                                                 // Bliski Istok
        continents.add(poly(142,60,158,63,162,88,156,116,148,136,140,126,138,98,140,76));                  // Afrika
        continents.add(poly(168,126,186,128,188,146,178,154,166,144));                                     // Australija
    }

    private Path poly(float... xy) {
        Path p = new Path();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.close();
        return p;
    }

    // ── Crtanje ──────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Napredi scroll
        long now = System.currentTimeMillis();
        if (lastFrameMs != 0L) {
            scrollFraction += (float)(now - lastFrameMs) / SCROLL_DURATION_MS;
            if (scrollFraction >= 1f) scrollFraction -= 1f;
        }
        lastFrameMs = now;

        float w  = getWidth();
        float h  = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r  = Math.min(w, h) / 2f - 8f;

        // Razmjer: 200 jedinica → dijametar globusa u pikselima
        float scale = (r * 2f) / 200f;

        // Senka (offset dolje)
        canvas.drawCircle(cx, cy + r * 0.06f, r, shadowPaint);

        // Okean
        canvas.drawCircle(cx, cy, r, oceanPaint);

        // ── Kontinenti (isječeni na krug) ─────────────────────────────────
        canvas.save();
        Path clip = new Path();
        clip.addCircle(cx, cy, r - 1f, Path.Direction.CW);
        canvas.clipPath(clip);

        // tx: pomjeraj u pikselima (0 → -r*2 = -jedna puna širina)
        float tx = -scrollFraction * 200f * scale;

        // Tri kopije: prethodna, tekuća, sljedeća — osiguravaju glatku petlju
        drawStrip(canvas, cx - r + tx - 200f * scale, cy - r, scale);
        drawStrip(canvas, cx - r + tx,                cy - r, scale);
        drawStrip(canvas, cx - r + tx + 200f * scale, cy - r, scale);

        canvas.restore();

        // Odsjaj (gornji lijevi krug)
        canvas.save();
        Path hclip = new Path();
        hclip.addCircle(cx, cy, r, Path.Direction.CW);
        canvas.clipPath(hclip);
        canvas.drawCircle(cx - r * 0.28f, cy - r * 0.3f, r * 0.38f, highlightPaint);
        canvas.restore();

        // Rub
        canvas.drawCircle(cx, cy, r, borderPaint);

        postInvalidateOnAnimation();
    }

    private void drawStrip(Canvas canvas, float originX, float originY, float scale) {
        canvas.save();
        canvas.translate(originX, originY);
        canvas.scale(scale, scale);
        for (Path p : continents) {
            canvas.drawPath(p, landPaint);
            canvas.drawPath(p, landEdge);
        }
        canvas.restore();
    }
}
