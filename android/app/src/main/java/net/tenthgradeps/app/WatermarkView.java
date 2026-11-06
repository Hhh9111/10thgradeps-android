package net.tenthgradeps.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Faint repeated account name drawn over Premium pages (the website's viewer does the same). Does not intercept touches. */
final class WatermarkView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String text;

    WatermarkView(Context c, String text) {
        super(c);
        this.text = text;
        paint.setColor(0x2E000000);
        paint.setTextSize(Ui.dp(c, 15));
        setClickable(false);
        setFocusable(false);
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float stepY = Ui.dp(getContext(), 120), stepX = Ui.dp(getContext(), 230);
        canvas.save();
        canvas.rotate(-25f, w / 2f, h / 2f);
        int row = 0;
        for (float y = -h; y < h * 2; y += stepY, row++) {
            float off = (row % 2) * (stepX / 2f);
            for (float x = -w + off; x < w * 2; x += stepX) canvas.drawText(text, x, y, paint);
        }
        canvas.restore();
    }
}
