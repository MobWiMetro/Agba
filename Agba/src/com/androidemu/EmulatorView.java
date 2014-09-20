package com.androidemu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.androidemu.gba.input.VirtualKeypad;

public class EmulatorView extends SurfaceView
		implements SurfaceHolder.Callback {

	public static final int SCALING_ORIGINAL = 0;
	public static final int SCALING_PROPORTIONAL = 1;
	public static final int SCALING_STRETCH = 2;

	private static final String LOG_TAG = "EmulatorView";

	private Emulator emulator;
	private int scalingMode = SCALING_STRETCH;
	private int actualWidth;
	private int actualHeight;
	private float aspectRatio;	
	
	private VirtualKeypad keypad;
	
	public EmulatorView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		Log.v("EmulatorView", "Constructor being called");
		final SurfaceHolder holder = getHolder();
		holder.setFormat(PixelFormat.RGB_565);
		holder.setFixedSize(Emulator.VIDEO_W, Emulator.VIDEO_H);
		holder.setKeepScreenOn(true);
		holder.addCallback(this);

		setFocusableInTouchMode(true);
		requestFocus();
	}

	public void setEmulator(Emulator e) {
		Log.v("EmulatorView", "setEmulator");
		emulator = e;
	}
	
	public void setActualSize(int w, int h)
	{
		if (actualWidth != w || actualHeight != h)
		{
			actualWidth = w;
			actualHeight = h;
			updateSurfaceSize();
		}
	}	

	public void setScalingMode(int mode) {
		if (scalingMode != mode) {
			scalingMode = mode;
			requestLayout();
		}
	}

	public void setAspectRatio(float ratio)
	{
		if (aspectRatio != ratio)
		{
			aspectRatio = ratio;
			updateSurfaceSize();
		}
	}
	
	private void updateSurfaceSize()
	{
		int viewWidth = getWidth();
		int viewHeight = getHeight();
		if (viewWidth == 0 || viewHeight == 0 || actualWidth == 0 || actualHeight == 0)
			return;

		int w = 0;
		int h = 0;

		Log.v("EmulatorView - SM & AR", Integer.toString(scalingMode) + Float.toString(aspectRatio));
		
		if (scalingMode != SCALING_STRETCH && aspectRatio != 0)
		{
			float ratio = aspectRatio * actualHeight / actualWidth;
			viewWidth = (int) (viewWidth / ratio);
		}

		switch (scalingMode)
		{
			case SCALING_ORIGINAL:
				w = viewWidth;
				h = viewHeight;
				break;

			/*case SCALING_2X:
				w = viewWidth / 2;
				h = viewHeight / 2;
				break;
			*/
			case SCALING_STRETCH:
				if (viewWidth * actualHeight >= viewHeight * actualWidth)
				{
					w = actualWidth;
					h = actualHeight;
				}
				break;
		}

		if (w < actualWidth || h < actualHeight)
		{
			h = actualHeight;
			w = h * viewWidth / viewHeight;
			if (w < actualWidth)
			{
				w = actualWidth;
				h = w * viewHeight / viewWidth;
			}
		}

		w = (w + 3) & ~3;
		h = (h + 3) & ~3;
		getHolder().setFixedSize(w, h);
		//emulator.resume();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		Log.v("EmulatorView", "onSizeChanged!!");
		//emulator.pause();
		updateSurfaceSize();
	}	
	
	public void onImageUpdate(int[] data) {
		SurfaceHolder holder = getHolder();
		Canvas canvas = holder.lockCanvas();
		canvas.drawBitmap(data, 0, Emulator.VIDEO_W, 0, 0,
				Emulator.VIDEO_W, Emulator.VIDEO_H, false, null);
		holder.unlockCanvasAndPost(canvas);
		//keypad.draw(canvas);
		
	}

	public void surfaceCreated(SurfaceHolder holder) {
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		emulator.setRenderSurface(null, 0, 0);
	}

	public void surfaceChanged(SurfaceHolder holder,
			int format, int width, int height) {
		emulator.setRenderSurface(this, width, height);
		Log.v("EmulatorView", "surfaceChanged!!");
	}

	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		Log.v("EmulatorView", "onMeasureChanged!!");
		int specWidth = MeasureSpec.getSize(widthMeasureSpec);
		int specHeight = MeasureSpec.getSize(heightMeasureSpec);
		int w, h;

		switch (scalingMode) {
		case SCALING_ORIGINAL:
			w = Emulator.VIDEO_W;
			h = Emulator.VIDEO_H;
			break;
		case SCALING_STRETCH:
			if (specWidth >= specHeight) {
				w = specWidth;
				h = specHeight;
				break;
			}
			// fall through
		case SCALING_PROPORTIONAL:
			h = specHeight;
			w = h * Emulator.VIDEO_W / Emulator.VIDEO_H;
			if (w > specWidth) {
				w = specWidth;
				h = w * Emulator.VIDEO_H / Emulator.VIDEO_W;
			}
			break;
		default:
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			return;
		}

		setMeasuredDimension(w, h);
	}
}
