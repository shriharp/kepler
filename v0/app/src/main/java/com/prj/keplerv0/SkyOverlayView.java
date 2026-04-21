package com.prj.keplerv0;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class SkyOverlayView extends View {

    private final Paint constellationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starSmallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<SkyLabel> currentLabels = new ArrayList<>();

    // Used for AABB collision
    private final List<RectF> drawnBounds = new ArrayList<>();
    private final RectF tempBounds = new RectF();

    public SkyOverlayView(Context context) {
        super(context);
        init(context);
    }

    public SkyOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // Constellation Paint
        constellationPaint.setColor(ContextCompat.getColor(context, R.color.accent_gold));
        constellationPaint.setTextSize(spToPx(context, 16));
        constellationPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        constellationPaint.setLetterSpacing(0.15f);
        constellationPaint.setTextAlign(Paint.Align.CENTER);
        // glowing effect
        constellationPaint.setShadowLayer(8f, 0f, 0f, ContextCompat.getColor(context, R.color.accent_gold));

        // Bright Star Paint
        starPaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        starPaint.setTextSize(spToPx(context, 12));
        starPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        starPaint.setLetterSpacing(0.05f);
        starPaint.setTextAlign(Paint.Align.CENTER);
        starPaint.setShadowLayer(5f, 0f, 0f, ContextCompat.getColor(context, R.color.accent_blue));

        // Normal Star Paint
        starSmallPaint.setColor(0xBBFFFFFF); // slightly transparent white
        starSmallPaint.setTextSize(spToPx(context, 10));
        starSmallPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        starSmallPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float spToPx(Context context, float sp) {
        return sp * context.getResources().getDisplayMetrics().scaledDensity;
    }

    public void setLabels(List<SkyLabel> labels) {
        this.currentLabels = labels;
        // Since StarRenderer computes this roughly at 60fps, we post invalidate
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        drawnBounds.clear();

        // Sort labels by importance: 
        // 1. Constellations (type == CONSTELLATION)
        // 2. Brightest stars (lowest magnitude)
        if (currentLabels == null || currentLabels.isEmpty()) return;

        // Create a copy to sort, avoiding concurrent modification
        List<SkyLabel> sortedLabels = new ArrayList<>(currentLabels);
        sortedLabels.sort((a, b) -> Float.compare(a.magnitude, b.magnitude));

        float padding = 8f; // pixels around text for collision

        for (SkyLabel label : sortedLabels) {
            Paint activePaint;
            if (label.type == SkyLabel.Type.CONSTELLATION) {
                activePaint = constellationPaint;
            } else if (label.magnitude < 1.0f) {
                activePaint = starPaint;
            } else {
                activePaint = starSmallPaint;
            }

            // Measure text
            float width = activePaint.measureText(label.text);
            float ascent = activePaint.ascent();
            float descent = activePaint.descent();
            float height = descent - ascent;

            // Compute bounding box centered at (label.x, label.y)
            // (Wait, normally label.y is the baseline. If it's exactly on the star, we might want to offset it)
            float xOffset = 0;
            float yOffset = (label.type == SkyLabel.Type.CONSTELLATION) ? 0 : 25f; // offset below star

            float left = label.x + xOffset - width / 2f - padding;
            float top = label.y + yOffset + ascent - padding;
            float right = label.x + xOffset + width / 2f + padding;
            float bottom = label.y + yOffset + descent + padding;

            tempBounds.set(left, top, right, bottom);

            // Check boundaries
            if (tempBounds.left < 0 || tempBounds.top < 0 || tempBounds.right > getWidth() || tempBounds.bottom > getHeight()) {
                continue; // off screen or at the edge
            }

            // Check AABB collisions
            boolean overlaps = false;
            for (RectF drawn : drawnBounds) {
                if (RectF.intersects(tempBounds, drawn)) {
                    overlaps = true;
                    break;
                }
            }

            if (!overlaps) {
                // Draw
                drawnBounds.add(new RectF(tempBounds));
                canvas.drawText(label.text, label.x + xOffset, label.y + yOffset, activePaint);
            }
        }
    }
}
