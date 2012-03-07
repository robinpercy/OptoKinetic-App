package com.example;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

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
    
    
    public static class DrumSurfaceView extends SurfaceView implements SurfaceHolder.Callback, View.OnTouchListener {

        public SurfaceHolder surfaceHolder;
        public DrumThread drumThread;
        private boolean fingerIsDown = false;
        private double currentScrollSpeed = 0d;
        private float downY = 0;
        private long downMillis = 0L;
        private float lastMoveY = 0;
        private long lastMoveMillis = 0L;


        public DrumSurfaceView(Context context) {
            super(context);
            // Get a handle to the holder and register for events
            this.surfaceHolder = this.getHolder();
            this.surfaceHolder.addCallback(this);
            this.setOnTouchListener(this);
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

        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            /**
             * See http://developer.android.com/reference/android/view/MotionEvent.html -> Device Types
             *
             * On pointing devices with source class SOURCE_CLASS_POINTER such as touch screens, the pointer coordinates
             * specify absolute positions such as view X/Y coordinates. Each complete gesture is represented by a
             * sequence of motion events with actions that describe pointer state transitions and movements.
             * A gesture starts with a motion event with ACTION_DOWN that provides the location of the first pointer
             * down. As each additional pointer that goes down or up, the framework will generate a motion event with
             * ACTION_POINTER_DOWN or ACTION_POINTER_UP accordingly. Pointer movements are described by motion events
             * with ACTION_MOVE. Finally, a gesture end either when the final pointer goes up as represented by a
             * motion event with ACTION_UP or when gesture is canceled with ACTION_CANCEL.
             */
            boolean handled = false;
            switch (motionEvent.getAction()){
                case MotionEvent.ACTION_DOWN:
                    Log.i(TAG,"ACTION DOWN");
                    handled = true;
                    this.currentScrollSpeed = this.drumThread.getScrollSpeedPxPerMillis();
                    this.drumThread.setScrollSpeedPxPerMillis(0d);
                    this.downY = motionEvent.getY();
                    this.downMillis = System.currentTimeMillis();
                    break;
                case MotionEvent.ACTION_MOVE:
                    Log.i(TAG,"ACTION MOVE");
                    handled = true;
                    this.lastMoveY = motionEvent.getY();
                    this.lastMoveMillis = System.currentTimeMillis();
                    break;
                case MotionEvent.ACTION_UP:
                    Log.i(TAG,"ACTION UP");
                    handled = true;
                    this.currentScrollSpeed = calculateScrollSpeed(motionEvent);
                    this.drumThread.setScrollSpeedPxPerMillis(this.currentScrollSpeed);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    Log.i(TAG,"ACTION CANCEL");
                    handled = true;
                    break;
            }

            return handled;
        }

        private double calculateScrollSpeed(MotionEvent motionEvent) {
            float curY = motionEvent.getY();
            long curMillis = System.currentTimeMillis();
            float yDiff = curY - this.downY;
            long milliDiff = curMillis - this.downMillis;
            return yDiff/milliDiff;
        }

        public static class DrumThread extends Thread {
            private SurfaceHolder surfaceHolder;
            private Context context;
            private long startMillis;
            private double scrollSpeedPxPerMillis = -1d/25d;
            int stripeHeight = 30;
            int[] stripeColors = {Color.RED, Color.WHITE, Color.BLACK};

            // Setter must be synchronized
            private Boolean canDraw = false;
            
            public synchronized void setCanDraw(Boolean canDraw) {
                this.canDraw = canDraw;
            }

            public synchronized void setScrollSpeedPxPerMillis(double scrollSpeedPxPerMillis) {
                this.scrollSpeedPxPerMillis = scrollSpeedPxPerMillis;
            }

            public synchronized double  getScrollSpeedPxPerMillis() {
                return this.scrollSpeedPxPerMillis;
            }
            
            public DrumThread(SurfaceHolder surfaceHolder, Context context) {
                this.surfaceHolder = surfaceHolder;
                this.context = context;
            }
            
            private void doDraw(Canvas c) {
                /**
                 * To give the illusion of perpetual scrolling, we slowly slide the striped image downwards until
                 * all colors have scrolled into view exactly once, then we restart.  This means that the animation
                 * needs to start (colors.length * stripeHeight) above(or below, depending on direction) the clipBounds.
                 */

                // Coordinates for our drawing surface
                Rect clipBounds = c.getClipBounds();

                // Timing for the animation
                Long runningMillis = System.currentTimeMillis() - startMillis;
                Log.d(TAG,"running Millis: " + runningMillis);

                // Absolute distance that stripes should move
                Double pxToMove = Math.floor(runningMillis.doubleValue() * scrollSpeedPxPerMillis);
                Log.d(TAG, "px to move = " + pxToMove);

                // Start painting above the clipBounds, so the shifting image looks like a constant scroll
                int offset = stripeHeight * stripeColors.length;
                int base = -offset;

                // 'Scroll' by sliding the entire image down, then restart after all colors processed
                base += pxToMove.intValue() % offset;
                Log.d(TAG, "Base: " + base);

                // Loop for enough stripes to cover the visible surface plus the offsets at
                // each end of it.
                for (int i=0; i * stripeHeight <= clipBounds.bottom + (2 * offset); i++) {
                    int newY = base + (i * stripeHeight);
                    ShapeDrawable rect = new ShapeDrawable(new RectShape());
                    rect.getPaint().setColor(stripeColors[ i % 3 ]);
                    rect.setBounds(0, newY, clipBounds.right, newY + stripeHeight);
                    rect.draw(c);
                };
            }

            /**
             * This is our event loop.  It will run as long as canDraw is true.  If canDraw becomes false for any
             * reason, the thread will need to be restarted.
             *
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
