package com.prj.keplerv0.quiz;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FloatingOrbsView extends View {

    private static class Orb {
        float x, y;
        float radius;
        float speedY;
        float speedX;
        float baseAlpha;
        float currentAlpha;
        float pulseSpeed;
        float pulseOffset;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Orb> orbs = new ArrayList<>();
    private final Random random = new Random();
    private ValueAnimator animator;
    private long lastTime = 0;

    public FloatingOrbsView(Context context) {
        super(context);
        init();
    }

    public FloatingOrbsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && orbs.isEmpty()) {
            generateOrbs(w, h);
            startAnimation();
        }
    }

    private void generateOrbs(int w, int h) {
        int numOrbs = 30; // Clean, not overwhelming
        for (int i = 0; i < numOrbs; i++) {
            Orb o = new Orb();
            o.x = random.nextFloat() * w;
            o.y = random.nextFloat() * h;
            o.radius = 2f + random.nextFloat() * 6f; // sizes 2 to 8
            
            // Slow drift upwards and slightly sideways
            o.speedY = -(0.2f + random.nextFloat() * 0.8f);
            o.speedX = -0.3f + random.nextFloat() * 0.6f;
            
            o.baseAlpha = 0.2f + random.nextFloat() * 0.6f;
            o.pulseSpeed = 0.02f + random.nextFloat() * 0.03f;
            o.pulseOffset = random.nextFloat() * (float) Math.PI * 2;
            orbs.add(o);
        }
    }

    private void startAnimation() {
        if (animator != null) animator.cancel();
        
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(10000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            long now = System.currentTimeMillis();
            if (lastTime == 0) lastTime = now;
            float dt = (now - lastTime) / 16f; // rough 60fps multiplier
            lastTime = now;
            
            int w = getWidth();
            int h = getHeight();
            
            for (Orb o : orbs) {
                // Move orb
                o.y += o.speedY * dt;
                o.x += o.speedX * dt;
                
                // Wrap around edges cleanly
                if (o.y < -o.radius) o.y = h + o.radius;
                if (o.x < -o.radius) o.x = w + o.radius;
                else if (o.x > w + o.radius) o.x = -o.radius;
                
                // Pulse alpha
                float pulse = (float) Math.sin((now * o.pulseSpeed / 10f) + o.pulseOffset);
                o.currentAlpha = o.baseAlpha + (pulse * 0.2f);
                if (o.currentAlpha > 1f) o.currentAlpha = 1f;
                if (o.currentAlpha < 0.1f) o.currentAlpha = 0.1f;
            }
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Orb o : orbs) {
            paint.setAlpha((int) (o.currentAlpha * 255));
            // Adding a slight glow effect (optional, could be intense on performance, skip for now. Alpha is enough)
            canvas.drawCircle(o.x, o.y, o.radius, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }
}
