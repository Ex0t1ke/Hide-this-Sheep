package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

/**
 * Animated interactive demonstration view for the Game Guide dialog.
 * Shows "КАК НЕЛЬЗЯ" (open pen, wolf attacks sheep) vs "КАК НАДО" (complete 2-cell fence corral, wolf safely passes).
 */
public class TutorialDemoView extends View {

    private Paint paint;
    private Paint gradientPaint;
    private Paint dashPaint;
    private final RectF rectF = new RectF();
    private final Path path = new Path();

    private boolean isGoodMode = true; // true = КАК НАДО, false = КАК НЕЛЬЗЯ
    private long startTime;

    private static final int GRID_COLS = 5;
    private static final int GRID_ROWS = 5;

    public TutorialDemoView(Context context) {
        super(context);
        init();
    }

    public TutorialDemoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TutorialDemoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dashPaint.setStyle(Paint.Style.STROKE);
        startTime = System.currentTimeMillis();
    }

    public void setGoodMode(boolean good) {
        this.isGoodMode = good;
        this.startTime = System.currentTimeMillis();
        invalidate();
    }

    public boolean isGoodMode() {
        return isGoodMode;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cellSize = Math.min((float) w / GRID_COLS, (float) h / GRID_ROWS) * 0.95f;
        float offsetX = (w - cellSize * GRID_COLS) / 2f;
        float offsetY = (h - cellSize * GRID_ROWS) / 2f;

        // Draw Pasture Background with rounded wooden rim
        drawPastureBackground(canvas, offsetX, offsetY, cellSize * GRID_COLS, cellSize * GRID_ROWS);

        // Grid contents based on mode
        int sheepX = 2;
        int sheepY = 2;

        if (isGoodMode) {
            // "КАК НАДО": 4 double fences completely enclose (2, 2)
            // Top: (1, 1) and (2, 1) [H], Right: (3, 1) and (3, 2) [V]
            // Bottom: (2, 3) and (3, 3) [H], Left: (1, 2) and (1, 3) [V]
            drawConnectedDoubleFence(canvas, offsetX, offsetY, cellSize, 1, 1, 2, 1);
            drawConnectedDoubleFence(canvas, offsetX, offsetY, cellSize, 3, 1, 3, 2);
            drawConnectedDoubleFence(canvas, offsetX, offsetY, cellSize, 2, 3, 3, 3);
            drawConnectedDoubleFence(canvas, offsetX, offsetY, cellSize, 1, 2, 1, 3);

            // Sheep safe in the center
            drawSheep(canvas, offsetX + sheepX * cellSize, offsetY + sheepY * cellSize, cellSize);

            // Wolf safely patrols around and exits
            drawPatrollingWolf(canvas, offsetX, offsetY, cellSize);
        } else {
            // "КАК НЕЛЬЗЯ": only 2 fences placed, leaving gaps!
            // Top: (1, 1)-(2, 1), Left: (1, 2)-(1, 3) -> Right and corners open!
            drawConnectedDoubleFence(canvas, offsetX, offsetY, cellSize, 1, 1, 2, 1);
            drawConnectedDoubleFence(canvas, offsetX, offsetY, cellSize, 1, 2, 1, 3);

            // Sheep in danger
            drawSheep(canvas, offsetX + sheepX * cellSize, offsetY + sheepY * cellSize, cellSize);

            // Wolf runs through the gap into the sheep
            drawAttackingWolf(canvas, offsetX, offsetY, cellSize, sheepX, sheepY);
        }

        // Keep animated
        postInvalidateOnAnimation();
    }

    private void drawPastureBackground(Canvas canvas, float x, float y, float w, float h) {
        rectF.set(x, y, x + w, y + h);

        // Dark grass background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#264D1D"));
        canvas.drawRoundRect(rectF, 16f, 16f, paint);

        // Inner pasture gradient
        gradientPaint.setShader(new RadialGradient(
                x + w / 2f, y + h / 2f, Math.max(w, h) * 0.7f,
                new int[]{Color.parseColor("#448A2C"), Color.parseColor("#2F6620")},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP));
        rectF.inset(4f, 4f);
        canvas.drawRoundRect(rectF, 12f, 12f, gradientPaint);
        gradientPaint.setShader(null);

        // Soft outer wooden border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.5f);
        paint.setColor(Color.parseColor("#5C3413"));
        rectF.set(x, y, x + w, y + h);
        canvas.drawRoundRect(rectF, 16f, 16f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawConnectedDoubleFence(Canvas canvas, float ox, float oy, float cs, int x1, int y1, int x2, int y2) {
        float cx1 = ox + x1 * cs + cs / 2f;
        float cy1 = oy + y1 * cs + cs / 2f;
        float cx2 = ox + x2 * cs + cs / 2f;
        float cy2 = oy + y2 * cs + cs / 2f;
        float postR = cs * 0.17f;
        float railThickness = cs * 0.085f;
        float railOffset = cs * 0.125f;

        paint.setColor(Color.parseColor("#E09E53"));
        if (y1 == y2) {
            float left = Math.min(cx1, cx2);
            float right = Math.max(cx1, cx2);
            rectF.set(left, cy1 - railOffset - railThickness / 2f, right, cy1 - railOffset + railThickness / 2f);
            canvas.drawRoundRect(rectF, 3f, 3f, paint);
            rectF.set(left, cy1 + railOffset - railThickness / 2f, right, cy1 + railOffset + railThickness / 2f);
            canvas.drawRoundRect(rectF, 3f, 3f, paint);
        } else {
            float top = Math.min(cy1, cy2);
            float bottom = Math.max(cy1, cy2);
            rectF.set(cx1 - railOffset - railThickness / 2f, top, cx1 - railOffset + railThickness / 2f, bottom);
            canvas.drawRoundRect(rectF, 3f, 3f, paint);
            rectF.set(cx1 + railOffset - railThickness / 2f, top, cx1 + railOffset + railThickness / 2f, bottom);
            canvas.drawRoundRect(rectF, 3f, 3f, paint);
        }

        // Posts
        paint.setColor(Color.parseColor("#C27A32"));
        canvas.drawCircle(cx1, cy1, postR, paint);
        canvas.drawCircle(cx2, cy2, postR, paint);
        paint.setColor(Color.parseColor("#7A4112"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        canvas.drawCircle(cx1, cy1, postR, paint);
        canvas.drawCircle(cx2, cy2, postR, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSheep(Canvas canvas, float x, float y, float cs) {
        float cx = x + cs * 0.5f;
        float cy = y + cs * 0.5f;

        // Shadow
        paint.setColor(Color.argb(70, 0, 0, 0));
        canvas.drawOval(cx - cs * 0.3f, cy + cs * 0.18f, cx + cs * 0.3f, cy + cs * 0.34f, paint);

        // Wool cloud
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx - cs * 0.12f, cy, cs * 0.18f, paint);
        canvas.drawCircle(cx + cs * 0.12f, cy, cs * 0.18f, paint);
        canvas.drawCircle(cx, cy - cs * 0.1f, cs * 0.18f, paint);
        canvas.drawCircle(cx, cy + cs * 0.08f, cs * 0.18f, paint);

        // Face
        paint.setColor(Color.parseColor("#2A201C"));
        canvas.drawCircle(cx + cs * 0.22f, cy - cs * 0.04f, cs * 0.10f, paint);

        // Eyes
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx + cs * 0.24f, cy - cs * 0.06f, cs * 0.025f, paint);
    }

    private void drawAttackingWolf(Canvas canvas, float ox, float oy, float cs, int sheepX, int sheepY) {
        long elapsed = (System.currentTimeMillis() - startTime) % 2800;
        float t = Math.min(elapsed / 2000f, 1f);

        // Wolf starts at (4, 0), runs through gap at (3, 2), reaches sheep at (2, 2)
        float startX = ox + 4 * cs;
        float startY = oy + 0 * cs;
        float midX = ox + 3.2f * cs;
        float midY = oy + 1.8f * cs;
        float targetX = ox + sheepX * cs + cs * 0.2f;
        float targetY = oy + sheepY * cs;

        float currX, currY;
        if (t < 0.6f) {
            float sub = t / 0.6f;
            currX = startX + (midX - startX) * sub;
            currY = startY + (midY - startY) * sub;
        } else {
            float sub = (t - 0.6f) / 0.4f;
            currX = midX + (targetX - midX) * sub;
            currY = midY + (targetY - midY) * sub;
        }

        // Red dashed threat trajectory
        dashPaint.setColor(Color.parseColor("#E53935"));
        dashPaint.setStrokeWidth(cs * 0.07f);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{cs * 0.2f, cs * 0.15f}, 0));
        path.reset();
        path.moveTo(startX + cs / 2f, startY + cs / 2f);
        path.lineTo(midX + cs / 2f, midY + cs / 2f);
        path.lineTo(targetX + cs / 2f, targetY + cs / 2f);
        canvas.drawPath(path, dashPaint);

        drawMiniWolf(canvas, currX, currY, cs);

        // If wolf reached sheep, show flashing ✗ badge
        if (t >= 0.95f) {
            paint.setColor(Color.argb(220, 211, 47, 47));
            rectF.set(ox + 0.8f * cs, oy + 0.3f * cs, ox + 4.2f * cs, oy + 1.1f * cs);
            canvas.drawRoundRect(rectF, 10f, 10f, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(cs * 0.32f);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            canvas.drawText("✗ ЩЕЛЬ В ЗАБОРЕ!", ox + 2.5f * cs, oy + 0.85f * cs, paint);
        }
    }

    private void drawPatrollingWolf(Canvas canvas, float ox, float oy, float cs) {
        long elapsed = (System.currentTimeMillis() - startTime) % 3600;
        float t = elapsed / 3600f;

        // Wolf runs from (4, 0) down the right corridor and exits at (0, 4)
        float startX = ox + 4f * cs;
        float startY = oy + 0.2f * cs;
        float p1X = ox + 4f * cs;
        float p1Y = oy + 4f * cs;
        float exitX = ox + 0.2f * cs;
        float exitY = oy + 4f * cs;

        float currX, currY;
        if (t < 0.5f) {
            float sub = t / 0.5f;
            currX = startX;
            currY = startY + (p1Y - startY) * sub;
        } else {
            float sub = (t - 0.5f) / 0.5f;
            currX = p1X + (exitX - p1X) * sub;
            currY = p1Y;
        }

        // Blue dashed bypass path
        dashPaint.setColor(Color.parseColor("#42A5F5"));
        dashPaint.setStrokeWidth(cs * 0.07f);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{cs * 0.2f, cs * 0.15f}, 0));
        path.reset();
        path.moveTo(startX + cs / 2f, startY + cs / 2f);
        path.lineTo(p1X + cs / 2f, p1Y + cs / 2f);
        path.lineTo(exitX + cs / 2f, exitY + cs / 2f);
        canvas.drawPath(path, dashPaint);

        drawMiniWolf(canvas, currX, currY, cs);

        // Safe status badge
        paint.setColor(Color.argb(220, 56, 142, 60));
        rectF.set(ox + 0.8f * cs, oy + 0.3f * cs, ox + 4.2f * cs, oy + 1.1f * cs);
        canvas.drawRoundRect(rectF, 10f, 10f, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(cs * 0.32f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        canvas.drawText("✓ ЗАЩИЩЕНО: 3 ЗВЕЗДЫ! ★★★", ox + 2.5f * cs, oy + 0.85f * cs, paint);
    }

    private void drawMiniWolf(Canvas canvas, float x, float y, float cs) {
        float cx = x + cs * 0.5f;
        float cy = y + cs * 0.5f;

        paint.setColor(Color.argb(70, 0, 0, 0));
        canvas.drawOval(cx - cs * 0.28f, cy + cs * 0.16f, cx + cs * 0.28f, cy + cs * 0.30f, paint);

        paint.setColor(Color.parseColor("#4A4744"));
        rectF.set(cx - cs * 0.26f, cy - cs * 0.12f, cx + cs * 0.26f, cy + cs * 0.16f);
        canvas.drawRoundRect(rectF, 10f, 10f, paint);

        // Head & snout
        paint.setColor(Color.parseColor("#363330"));
        canvas.drawCircle(cx + cs * 0.24f, cy, cs * 0.14f, paint);
        canvas.drawCircle(cx + cs * 0.34f, cy, cs * 0.08f, paint);

        // Amber eyes
        paint.setColor(Color.parseColor("#FFA000"));
        canvas.drawCircle(cx + cs * 0.26f, cy - cs * 0.05f, cs * 0.025f, paint);
    }
}
