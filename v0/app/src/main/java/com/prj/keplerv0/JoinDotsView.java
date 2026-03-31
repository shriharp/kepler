package com.prj.keplerv0;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class JoinDotsView extends View {

    public interface GameListener {
        void onConstellationCompleted();
    }

    private GameListener listener;
    private List<StarPoint> stars = new ArrayList<>();
    private List<Line> targetLines = new ArrayList<>();
    private List<Line> drawnLines = new ArrayList<>();
    
    private StarPoint currentStartStar = null;
    private StarPoint hoveredStar = null;
    private PointF currentTouchPoint = null;
    
    private Paint starPaint;
    private Paint linePaint;
    private Paint guidePaint;
    private Paint hintPaint;
    private Paint magnifierPaint;

    public JoinDotsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPaint.setColor(Color.WHITE);
        starPaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.CYAN);
        linePaint.setStrokeWidth(8f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guidePaint.setColor(Color.YELLOW);
        guidePaint.setStrokeWidth(5f);
        
        hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintPaint.setColor(Color.GRAY);
        hintPaint.setAlpha(50);
        hintPaint.setStrokeWidth(2f);

        magnifierPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void setListener(GameListener listener) {
        this.listener = listener;
    }

    public void setData(List<StarPoint> stars, List<Line> connections) {
        this.stars = stars;
        this.targetLines = connections;
        this.drawnLines.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Draw main scene
        renderScene(canvas, false);

        // Draw Magnifier if dragging
        if (currentTouchPoint != null) {
            drawMagnifier(canvas);
        }
    }

    private void renderScene(Canvas canvas, boolean isZoomed) {
        // Draw hint lines
        for (Line line : targetLines) {
            canvas.drawLine(line.start.x, line.start.y, line.end.x, line.end.y, hintPaint);
        }

        // Draw already drawn lines
        for (Line line : drawnLines) {
            canvas.drawLine(line.start.x, line.start.y, line.end.x, line.end.y, linePaint);
        }

        // Draw current dragging line
        if (currentStartStar != null && currentTouchPoint != null) {
            canvas.drawLine(currentStartStar.x, currentStartStar.y, currentTouchPoint.x, currentTouchPoint.y, guidePaint);
        }

        // Draw stars
        for (StarPoint star : stars) {
            float radius = star.magnitude * 12f;
            
            // Highlight if hovered or starting star
            if (star == hoveredStar || star == currentStartStar) {
                starPaint.setColor(Color.YELLOW);
                canvas.drawCircle(star.x, star.y, Math.max(12f, radius + 4), starPaint);
            } else {
                starPaint.setColor(Color.WHITE);
                canvas.drawCircle(star.x, star.y, Math.max(8f, radius), starPaint);
            }
        }
    }

    private void drawMagnifier(Canvas canvas) {
        float magRadius = 150f;
        float zoomScale = 2.5f;
        
        // Position magnifier above finger
        float magX = currentTouchPoint.x;
        float magY = currentTouchPoint.y - 250f;
        
        // Keep on screen
        if (magY < magRadius + 20) magY = currentTouchPoint.y + 250f;

        canvas.save();
        
        // Background & Border
        magnifierPaint.setStyle(Paint.Style.FILL);
        magnifierPaint.setColor(Color.BLACK);
        canvas.drawCircle(magX, magY, magRadius, magnifierPaint);
        
        magnifierPaint.setStyle(Paint.Style.STROKE);
        magnifierPaint.setColor(Color.WHITE);
        magnifierPaint.setStrokeWidth(6f);
        canvas.drawCircle(magX, magY, magRadius, magnifierPaint);

        // Clip to circle
        Path clipPath = new Path();
        clipPath.addCircle(magX, magY, magRadius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        // Draw zoomed scene
        canvas.translate(magX, magY);
        canvas.scale(zoomScale, zoomScale);
        canvas.translate(-currentTouchPoint.x, -currentTouchPoint.y);
        renderScene(canvas, true);

        canvas.restore();

        // Crosshair
        magnifierPaint.setStrokeWidth(2f);
        canvas.drawLine(magX - 20, magY, magX + 20, magY, magnifierPaint);
        canvas.drawLine(magX, magY - 20, magX, magY + 20, magnifierPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float tx = event.getX();
        float ty = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentStartStar = findNearbyStar(tx, ty);
                if (currentStartStar != null) {
                    currentTouchPoint = new PointF(tx, ty);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (currentStartStar != null) {
                    currentTouchPoint.set(tx, ty);
                    hoveredStar = findNearbyStar(tx, ty);
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
                if (currentStartStar != null) {
                    StarPoint endStar = findNearbyStar(tx, ty);
                    if (endStar != null && endStar != currentStartStar) {
                        checkAndAddLine(currentStartStar, endStar);
                    }
                }
                currentStartStar = null;
                hoveredStar = null;
                currentTouchPoint = null;
                invalidate();
                break;
        }
        return true;
    }

    private StarPoint findNearbyStar(float x, float y) {
        StarPoint closest = null;
        float minDistance = 80f; // Search radius
        
        for (StarPoint star : stars) {
            float dx = star.x - x;
            float dy = star.y - y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < minDistance) {
                minDistance = dist;
                closest = star;
            }
        }
        return closest;
    }

    private void checkAndAddLine(StarPoint a, StarPoint b) {
        for (Line target : targetLines) {
            if ((target.start == a && target.end == b) || (target.start == b && target.end == a)) {
                if (!isLineAlreadyDrawn(a, b)) {
                    drawnLines.add(new Line(a, b));
                    checkWinCondition();
                }
                return;
            }
        }
    }

    private boolean isLineAlreadyDrawn(StarPoint a, StarPoint b) {
        for (Line drawn : drawnLines) {
            if ((drawn.start == a && drawn.end == b) || (drawn.start == b && drawn.end == a)) {
                return true;
            }
        }
        return false;
    }

    private void checkWinCondition() {
        if (drawnLines.size() >= targetLines.size()) {
            // Validate all target lines are present in drawnLines
            int matchCount = 0;
            for (Line target : targetLines) {
                if (isLineAlreadyDrawn(target.start, target.end)) {
                    matchCount++;
                }
            }
            if (matchCount == targetLines.size() && listener != null) {
                listener.onConstellationCompleted();
            }
        }
    }

    public static class StarPoint {
        int id;
        float x, y;
        float magnitude;

        public StarPoint(int id, float x, float y, float magnitude) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.magnitude = magnitude;
        }
    }

    public static class Line {
        StarPoint start, end;
        public Line(StarPoint s, StarPoint e) {
            this.start = s;
            this.end = e;
        }
    }
}
