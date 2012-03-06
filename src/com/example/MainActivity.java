package com.example;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Here's how this works...
 *
 * MainActivity is specified in AndroidManifest.xml, so it's loaded on App start.
 *
 * We need a View for drawing and animating outside of the UI thread.  So we use SurfaceView.
 *
 * Using SurfaceView involves the following steps:
 * 1. Subclass SurfaceView (DrumSurfaceView) and have that class implement SurfaceHolder.Callback
 * 2. Give DrumSurfaceView a handle to the SurfaceHolder
 * 3. Create a thread (DrumThread) that will handle drawing to the surface's Canvas
 * 4. Make sure DrumThread only draws when the surface is available (between calls to surfaceCreated() and surfaceDestroyed()
 * 5. Canvas must be locked and unlocked before and after drawing
 *
 */
public class MainActivity extends Activity
{
    DrumSurfaceView drumView;
    private static String TAG= "MainActivity";
    
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        this.drumView = new DrumSurfaceView(this);
        this.setContentView(this.drumView);
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // TODO: save state
    }

    @Override
    public void onResume() {
        super.onResume();
        // TODO: retrieve state
    }
    
    
    public static class DrumSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

        public SurfaceHolder surfaceHolder;
        public DrumThread drumThread;

        public DrumSurfaceView(Context context) {
            super(context);
            // Get a handle to the holder and register for events
            this.surfaceHolder = this.getHolder();
            this.surfaceHolder.addCallback(this);
            this.drumThread = new DrumThread(this.surfaceHolder, context);

        }

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            this.drumThread.setCanDraw(true);         
            try {
                this.drumThread.start();
            } catch (IllegalThreadStateException itse) {
                Log.e(TAG, "DrumThread was already started.");
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
            
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            this.drumThread.setCanDraw(false); // Thread's run method should finish shortly
            Log.d(TAG, "Surface destroyed, stopping DrumThread");
            try {
                this.drumThread.join(); // wait for the thread to run out
            } catch (InterruptedException ie) {
                Log.e(TAG, "drumThread.join() interrupted");
            }
            Log.d(TAG, "DrumThread stopped");
        }

        public static class DrumThread extends Thread {
            private SurfaceHolder surfaceHolder;
            private Context context;
            private long startMillis;

            // Setter must be synchronized
            private Boolean canDraw = false;
            
            public synchronized void setCanDraw(Boolean canDraw) {
                this.canDraw = canDraw;
            }
            
            public DrumThread(SurfaceHolder surfaceHolder, Context context) {
                this.surfaceHolder = surfaceHolder;
                this.context = context;
            }
            
            private void doDraw(Canvas c) {
                Rect clipBounds = c.getClipBounds();

                int rectHeight = 30;
                int[] colors = {Color.RED, Color.WHITE, Color.BLACK};

                long scrollSpeedPxPerMillis = 1L/25L; // 1px per 25millis

                // Animate by one bar every 3 seconds
                long runningMillis = System.currentTimeMillis() - startMillis;
                Log.i(TAG,"running Millis: " + runningMillis);
                long hundredths = runningMillis;
                Log.i(TAG,"hundredths: " + hundredths);
                Long wrappedHundredths = hundredths / 300;
                Log.i(TAG,"Wrapped hundredths: " + wrappedHundredths);
                int base = wrappedHundredths.intValue() % rectHeight;
                Log.i(TAG, "Base: " + base);

                for (int i=0; i*rectHeight <= clipBounds.bottom; i++) {
                    int newY = base + i * rectHeight;
                    ShapeDrawable rect = new ShapeDrawable(new RectShape());
                    rect.getPaint().setColor(colors[ i % 3 ]);
                    rect.setBounds(0, newY, clipBounds.right, newY + rectHeight);
                    rect.draw(c);
                };
            }

            /**
             * Thread will run as long as canDraw is true.  It will
             * need to be restarted if is switched to false and then true again
             */
            @Override
            public void run() {
                this.startMillis = System.currentTimeMillis();
                while (canDraw) {
                    Canvas c = null;
                    try {
                        c = this.surfaceHolder.lockCanvas(null);
                        this.doDraw(c);
                    } finally {
                        this.surfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }
    }
}
