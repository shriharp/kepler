package com.prj.keplerv0;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
    private PointF currentTouchPoint = null;
    
    private Paint starPaint;
    private Paint linePaint;
    private Paint guidePaint;
    private Paint hintPaint;

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
        linePaint.setStrokeCap(Paint.Style.Cap.ROUND.ordinal() == 0 ? Paint.Cap.ROUND : Paint.Cap.ROUND);

        guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guidePaint.setColor(Color.YELLOW);
        guidePaint.setStrokeWidth(5f);
        
        hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintPaint.setColor(Color.GRAY);
        hintPaint.setAlpha(50);
        hintPaint.setStrokeWidth(2f);
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

        // Draw hint lines (optional, very faint)
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
            float radius = star.magnitude * 10f;
            canvas.drawCircle(star.x, star.y, Math.max(8f, radius), starPaint);
        }
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
                currentTouchPoint = null;
                invalidate();
                break;
        }
        return true;
    }

    private StarPoint findNearbyStar(float x, float y) {
        for (StarPoint star : stars) {
            float dx = star.x - x;
            float dy = star.y - y;
            if (Math.sqrt(dx * dx + dy * dy) < 60) { // 60px tolerance
                return star;
            }
        }
        return null;
    }

    private void checkAndAddLine(StarPoint a, StarPoint b) {
        for (Line target : targetLines) {
            if ((target.start == a && target.end == b) || (target.start == b && target.end == a)) {
                // Correct line!
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
        if (drawnLines.size() == targetLines.size()) {
            if (listener != null) {
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
