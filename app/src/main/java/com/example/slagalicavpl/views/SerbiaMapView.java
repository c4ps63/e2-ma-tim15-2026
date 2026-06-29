package com.example.slagalicavpl.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.slagalicavpl.model.SerbiaRegions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SerbiaMapView extends View {

    public interface RegionClickListener {
        void onRegionClicked(String regionId);
    }

    // Serbia real dimensions: ~330 km wide × ~480 km tall → aspect W/H ≈ 0.688
    private static final float SERBIA_ASPECT = 0.688f;

    private final Map<String, Path>                    regionPaths = new HashMap<>();
    private final Map<String, android.graphics.Region> regionHits  = new HashMap<>();
    private final Map<String, List<PointF>>            userDots    = new HashMap<>();
    private String              myRegionId;
    private RegionClickListener listener;

    // Letterbox transform (set in buildPaths, used when drawing dots)
    private float mapW, mapH, offsetX, offsetY;

    private final Paint bgPaint     = new Paint();
    private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint myStroke    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SerbiaMapView(Context c)                          { super(c); init(); }
    public SerbiaMapView(Context c, AttributeSet a)          { super(c, a); init(); }
    public SerbiaMapView(Context c, AttributeSet a, int s)   { super(c, a, s); init(); }

    private void init() {
        float dp = getResources().getDisplayMetrics().density;

        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.parseColor("#C8DCF0")); // light blue – neighbouring countries

        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#1A2E44"));
        strokePaint.setStrokeWidth(1.5f * dp);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        myStroke.setStyle(Paint.Style.STROKE);
        myStroke.setColor(Color.parseColor("#FFD700"));
        myStroke.setStrokeWidth(3.5f * dp);
        myStroke.setPathEffect(
                new android.graphics.DashPathEffect(new float[]{7*dp, 4*dp}, 0));

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.WHITE);
        dotPaint.setShadowLayer(2 * dp, 0, 1 * dp, Color.parseColor("#66000000"));

        iconPaint.setTextAlign(Paint.Align.CENTER);
        iconPaint.setTextSize(20 * dp);
    }

    public void setMyRegion(String regionId) { myRegionId = regionId; invalidate(); }

    public void setUserDots(Map<String, List<PointF>> dots) {
        userDots.clear();
        userDots.putAll(dots);
        invalidate();
    }

    public void setRegionClickListener(RegionClickListener l) { listener = l; }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        buildPaths(w, h);
    }

    private void buildPaths(int vw, int vh) {
        regionPaths.clear();
        regionHits.clear();

        // Letterbox: maintain Serbia's real W/H aspect ratio
        float padding = getResources().getDisplayMetrics().density * 8;
        float availW = vw - 2 * padding;
        float availH = vh - 2 * padding;

        if (availW / availH < SERBIA_ASPECT) {
            mapW = availW;
            mapH = availW / SERBIA_ASPECT;
        } else {
            mapH = availH;
            mapW = availH * SERBIA_ASPECT;
        }
        offsetX = (vw - mapW) / 2f;
        offsetY = (vh - mapH) / 2f;

        for (SerbiaRegions.Region r : SerbiaRegions.all().values()) {
            Path path = new Path();
            float[] pts = r.polygon;
            path.moveTo(offsetX + pts[0] * mapW, offsetY + pts[1] * mapH);
            for (int i = 2; i < pts.length; i += 2)
                path.lineTo(offsetX + pts[i] * mapW, offsetY + pts[i+1] * mapH);
            path.close();
            regionPaths.put(r.id, path);

            android.graphics.Region hit = new android.graphics.Region();
            hit.setPath(path, new android.graphics.Region(0, 0, vw, vh));
            regionHits.put(r.id, hit);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Background (neighbouring countries / outside Serbia)
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        // Region fills
        for (SerbiaRegions.Region r : SerbiaRegions.all().values()) {
            Path path = regionPaths.get(r.id);
            if (path == null) continue;
            fillPaint.setColor(r.fillColor);
            canvas.drawPath(path, fillPaint);
        }

        // Borders (drawn after fills so they're always on top)
        for (SerbiaRegions.Region r : SerbiaRegions.all().values()) {
            Path path = regionPaths.get(r.id);
            if (path == null) continue;
            canvas.drawPath(path, strokePaint);
            if (r.id.equals(myRegionId))
                canvas.drawPath(path, myStroke);
        }

        // User dots
        float dotR = getResources().getDisplayMetrics().density * 5f;
        for (Map.Entry<String, List<PointF>> entry : userDots.entrySet()) {
            for (PointF p : entry.getValue()) {
                float px = offsetX + p.x * mapW;
                float py = offsetY + p.y * mapH;
                canvas.drawCircle(px, py, dotR, dotPaint);
            }
        }

        // Region icons centered on each polygon
        for (SerbiaRegions.Region r : SerbiaRegions.all().values()) {
            Path path = regionPaths.get(r.id);
            if (path == null) continue;
            RectF bounds = new RectF();
            path.computeBounds(bounds, true);
            canvas.drawText(r.icon, bounds.centerX(), bounds.centerY()
                    + iconPaint.getTextSize() / 3f, iconPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && listener != null) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            for (Map.Entry<String, android.graphics.Region> e : regionHits.entrySet()) {
                if (e.getValue().contains(x, y)) {
                    listener.onRegionClicked(e.getKey());
                    return true;
                }
            }
        }
        return true;
    }
}
