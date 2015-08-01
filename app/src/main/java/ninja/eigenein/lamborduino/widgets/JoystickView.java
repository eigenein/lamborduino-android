package ninja.eigenein.lamborduino.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;

import ninja.eigenein.lamborduino.R;

public class JoystickView extends View {

    private final Paint backgroundPaint = new Paint();

    public JoystickView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setAntiAlias(true);
        backgroundPaint.setStrokeWidth(2.0f);
        backgroundPaint.setColor(context.getResources().getColor(R.color.blue_500));
    }

    @Override
    protected void onDraw(final @NonNull Canvas canvas) {
        final int width = getWidth();
        final int height = getHeight();

        final float centerX = width / 2.0f;
        final float centerY = height / 2.0f;
        final float radius = Math.min(width, height) / 2.0f;

        canvas.drawCircle(centerX, centerY, radius, backgroundPaint);
    }
}
