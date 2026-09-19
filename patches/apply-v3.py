from pathlib import Path

p = Path("NFTRecorderProject/nft-recorder/app/src/main/java/com/vuvuv/nftrecorder/H264LottieRenderer.java")
s = p.read_text()

repls = [
    ("import android.content.ContentResolver;\n", "import android.app.Activity;\nimport android.content.ContentResolver;\n"),
    ("import android.content.Context;\n", "import android.content.Context;\nimport android.content.ContextWrapper;\n"),
    ("import android.os.Build;\n", "import android.os.Build;\nimport android.os.Handler;\nimport android.os.Looper;\n"),
    ("import android.view.Surface;\n", "import android.view.PixelCopy;\nimport android.view.Surface;\n"),
]
for a, b in repls:
    if b not in s:
        s = s.replace(a, b)

start = s.index("    public String renderWebCard(")
end = s.index("    private static String sanitizeDisplayName", start)

method = r'''    public String renderWebCard(WebView webView, int seconds, String displayName, ProgressListener listener) throws Exception {
        if (webView == null) throw new Exception("WebView is null");
        Activity activity = findActivity(webView.getContext());
        if (activity == null) throw new Exception("Activity not found for PixelCopy");

        final Rect[] srcRectHolder = new Rect[1];
        final int[] sideHolder = new int[1];
        CountDownLatch geometryLatch = new CountDownLatch(1);
        webView.post(() -> {
            try {
                int vw = webView.getWidth();
                int vh = webView.getHeight();
                if (vw <= 0 || vh <= 0) throw new IllegalStateException("WebView has no size");
                int side = Math.min(vw, vh);
                int[] loc = new int[2];
                webView.getLocationInWindow(loc);
                int left = loc[0] + Math.max(0, (vw - side) / 2);
                int top = loc[1] + Math.max(0, (vh - side) / 2);
                srcRectHolder[0] = new Rect(left, top, left + side, top + side);
                sideHolder[0] = side;
            } finally {
                geometryLatch.countDown();
            }
        });
        if (!geometryLatch.await(5, TimeUnit.SECONDS) || srcRectHolder[0] == null) {
            throw new Exception("Could not calculate WebView capture area");
        }

        final Rect srcRect = srcRectHolder[0];
        final int srcSide = sideHolder[0];
        final int frames = Math.max(FPS, seconds * FPS);
        File temp = new File(context.getCacheDir(), "web-render-" + System.nanoTime() + ".mp4");
        MediaCodec codec = null;
        Surface codecSurface = null;
        EglEncoderSurface egl = null;
        MediaMuxer muxer = null;
        Bitmap bitmap = null;
        Bitmap captureBitmap = null;
        Handler mainHandler = new Handler(Looper.getMainLooper());

        try {
            codec = createConfiguredEncoder();
            codecSurface = codec.createInputSurface();
            codec.start();
            egl = new EglEncoderSurface(codecSurface);
            muxer = new MediaMuxer(temp.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            EncoderDrain drain = new EncoderDrain(codec, muxer);
            bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            captureBitmap = Bitmap.createBitmap(srcSide, srcSide, Bitmap.Config.ARGB_8888);

            long startMs = SystemClock.uptimeMillis();
            int outSide = HEIGHT;
            int outLeft = (WIDTH - outSide) / 2;
            Rect outRect = new Rect(outLeft, 0, outLeft + outSide, outSide);

            for (int i = 0; i < frames; i++) {
                long due = startMs + Math.round(i * 1000.0 / FPS);
                long wait = due - SystemClock.uptimeMillis();
                if (wait > 0) SystemClock.sleep(wait);

                CountDownLatch copyLatch = new CountDownLatch(1);
                final int[] copyResult = new int[]{PixelCopy.ERROR_UNKNOWN};
                final Bitmap dst = captureBitmap;
                PixelCopy.request(activity.getWindow(), srcRect, dst, result -> {
                    copyResult[0] = result;
                    copyLatch.countDown();
                }, mainHandler);

                if (!copyLatch.await(5, TimeUnit.SECONDS)) throw new Exception("PixelCopy timeout");
                if (copyResult[0] != PixelCopy.SUCCESS) {
                    throw new Exception("PixelCopy failed: " + copyResult[0]);
                }

                Canvas c = new Canvas(bitmap);
                c.drawColor(Color.BLACK);
                c.drawBitmap(captureBitmap, null, outRect, null);

                long ptsNs = i * 1_000_000_000L / FPS;
                egl.draw(bitmap, ptsNs);
                drain.drain(false);
                if (listener != null) listener.onProgress((i + 1) * 100 / frames);
            }

            codec.signalEndOfInputStream();
            drain.drain(true);
            drain.finish();
            saveToGallery(temp, sanitizeDisplayName(displayName));
            return sanitizeDisplayName(displayName);
        } finally {
            if (captureBitmap != null) captureBitmap.recycle();
            if (bitmap != null) bitmap.recycle();
            if (egl != null) egl.release();
            if (codecSurface != null) codecSurface.release();
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) {}
                codec.release();
            }
            if (muxer != null) {
                try { muxer.release(); } catch (Exception ignored) {}
            }
            if (temp.exists()) temp.delete();
        }
    }

    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return context instanceof Activity ? (Activity) context : null;
    }

'''
s = s[:start] + method + s[end:]
p.write_text(s)
