from pathlib import Path
import re

MAIN = Path("NFTRecorderProject/nft-recorder/app/src/main/java/com/vuvuv/nftrecorder/MainActivity.java")
RENDERER = Path("NFTRecorderProject/nft-recorder/app/src/main/java/com/vuvuv/nftrecorder/H264LottieRenderer.java")

main = r'''package com.vuvuv.nftrecorder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieResult;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT = 7001;
    private static final int MAX_BATCH = 5000;

    private static final int[] BG_PALETTE = new int[] {
            Color.rgb(12, 14, 18),
            Color.rgb(168, 38, 48),
            Color.rgb(226, 190, 54),
            Color.rgb(199, 149, 44),
            Color.rgb(151, 77, 214),
            Color.rgb(54, 126, 210),
            Color.rgb(42, 143, 101),
            Color.rgb(199, 74, 132)
    };

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final ArrayList<ImportItem> importedItems = new ArrayList<>();

    private EditText input, fromInput, toInput, secondsInput, bgVariantsInput, patternVariantsInput;
    private Button loadWeb, autoCrop, webRec, batch, importFile, runImport, stop;
    private TextView status;
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = dp(12);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("NFT Recorder v6 — Batch / Import / Web Crop");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("t.me/nft/PlushPepe-4 | fragment.com/gift/... | getgems.io/...");
        root.addView(input, margin(matchWrap(), 8));

        LinearLayout range = new LinearLayout(this);
        range.setOrientation(LinearLayout.HORIZONTAL);
        fromInput = numberField("From", "");
        toInput = numberField("To", "100");
        secondsInput = numberField("Sec", "4");
        range.addView(fromInput, weightWrap());
        range.addView(toInput, weightWrap());
        range.addView(secondsInput, weightWrap());
        root.addView(range, margin(matchWrap(), 6));

        LinearLayout variants = new LinearLayout(this);
        variants.setOrientation(LinearLayout.HORIZONTAL);
        bgVariantsInput = numberField("BG variants", "5");
        patternVariantsInput = numberField("Pattern variants", "4");
        variants.addView(bgVariantsInput, weightWrap());
        variants.addView(patternVariantsInput, weightWrap());
        root.addView(variants, margin(matchWrap(), 4));

        loadWeb = new Button(this);
        loadWeb.setText("OPEN WEB");
        root.addView(loadWeb, margin(matchWrap(), 6));

        web = new WebView(this);
        configureWebView();
        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(430));
        webLp.topMargin = dp(6);
        root.addView(web, webLp);

        LinearLayout webTools = new LinearLayout(this);
        webTools.setOrientation(LinearLayout.HORIZONTAL);
        autoCrop = new Button(this);
        autoCrop.setText("AUTO CROP");
        webRec = new Button(this);
        webRec.setText("REC WEB 1080P60");
        webTools.addView(autoCrop, weightWrap());
        webTools.addView(webRec, weightWrap());
        root.addView(webTools, margin(matchWrap(), 4));

        batch = new Button(this);
        batch.setText("BATCH FROM → TO (SKIP MISSING)");
        root.addView(batch, margin(matchWrap(), 6));

        LinearLayout importRow = new LinearLayout(this);
        importRow.setOrientation(LinearLayout.HORIZONTAL);
        importFile = new Button(this);
        importFile.setText("IMPORT TXT");
        runImport = new Button(this);
        runImport.setText("RUN IMPORT");
        importRow.addView(importFile, weightWrap());
        importRow.addView(runImport, weightWrap());
        root.addView(importRow, margin(matchWrap(), 4));

        stop = new Button(this);
        stop.setText("STOP");
        stop.setEnabled(false);
        root.addView(stop, margin(matchWrap(), 4));

        status = new TextView(this);
        status.setText("Batch: вставь t.me/nft/Name-4, оставь From пустым и поставь To=100. " +
                "Отсутствующие номера пропускаются.\nFragment/GetGems: OPEN WEB → AUTO CROP → REC WEB. " +
                "IMPORT TXT понимает строки URL и формат url|key=value.");
        status.setGravity(Gravity.CENTER);
        root.addView(status, margin(matchWrap(), 8));

        setContentView(scroll);

        loadWeb.setOnClickListener(v -> openWeb());
        autoCrop.setOnClickListener(v -> autoCropWebCard());
        webRec.setOnClickListener(v -> recordWebCard());
        batch.setOnClickListener(v -> startBatch());
        importFile.setOnClickListener(v -> chooseImportFile());
        runImport.setOnClickListener(v -> startImportedQueue());
        stop.setOnClickListener(v -> {
            stopRequested.set(true);
            status.setText("Останавливаю после текущего рендера…");
        });
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(web, true);
        }

        web.setWebChromeClient(new WebChromeClient());
        web.setInitialScale(100);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleWebNavigation(view, request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleWebNavigation(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                String js = "(function(){var m=document.querySelector('meta[name=viewport]');" +
                        "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                        "m.setAttribute('content','width=device-width,initial-scale=1.0,minimum-scale=0.25,maximum-scale=8.0,user-scalable=yes');})();";
                view.evaluateJavascript(js, null);
                status.setText("Страница открыта. Для Fragment/GetGems нажми AUTO CROP. " +
                        "Если Fragment показал проверку — пройди её один раз, cookies сохранятся.");
            }
        });
    }

    private boolean handleWebNavigation(WebView view, String url) {
        if (url == null) return false;
        if (url.startsWith("tg:nft")) {
            try {
                Uri uri = Uri.parse(url);
                String slug = uri.getQueryParameter("slug");
                if (slug != null && slug.matches("[A-Za-z0-9_-]+")) {
                    String fallback = "https://fragment.com/gift/" + slug.toLowerCase(Locale.ROOT) + "?collection=all";
                    view.loadUrl(fallback);
                    return true;
                }
            } catch (Throwable ignored) {}
            return true;
        }
        return false;
    }

    private void openWeb() {
        String url = normalizeUrl(input.getText().toString().trim());
        if (url == null) {
            status.setText("Нужна ссылка t.me/nft, fragment.com или getgems.io.");
            return;
        }
        status.setText("Открываю…");
        web.loadUrl(url);
    }

    private void autoCropWebCard() {
        if (web.getUrl() == null) {
            openWeb();
            return;
        }
        String js = "(function(){" +
                "try{" +
                "var els=[].slice.call(document.querySelectorAll('canvas,video,img,svg,lottie-player,dotlottie-player,[class*=gift],[class*=nft]'));" +
                "var best=null,score=0;" +
                "els.forEach(function(e){var r=e.getBoundingClientRect();" +
                "if(r.width<90||r.height<90)return;" +
                "var vis=Math.max(0,Math.min(r.right,innerWidth)-Math.max(r.left,0))*Math.max(0,Math.min(r.bottom,innerHeight)-Math.max(r.top,0));" +
                "var sq=Math.min(r.width,r.height)/Math.max(r.width,r.height);" +
                "var s=vis*sq;if(s>score){score=s;best=e;}});" +
                "if(!best)return 'NO_CARD';" +
                "best.scrollIntoView({block:'center',inline:'center'});" +
                "var r=best.getBoundingClientRect();" +
                "var target=Math.min(innerWidth*0.90,innerHeight*0.78);" +
                "var scale=Math.min(3.0,Math.max(1.0,Math.min(target/r.width,target/r.height)));" +
                "best.style.transformOrigin='center center';best.style.transform='scale('+scale+')';" +
                "best.style.position='relative';best.style.zIndex='2147483646';" +
                "document.documentElement.style.scrollBehavior='auto';" +
                "setTimeout(function(){best.scrollIntoView({block:'center',inline:'center'});},80);" +
                "return 'OK '+best.tagName+' '+Math.round(r.width)+'x'+Math.round(r.height)+' x'+scale.toFixed(2);" +
                "}catch(e){return 'ERR '+e;}" +
                "})();";
        web.evaluateJavascript(js, value -> status.setText("AUTO CROP: " + value + "\nЕсли выбрало не ту карточку — подстрой масштаб руками."));
    }

    private void recordWebCard() {
        if (web.getUrl() == null) {
            openWeb();
            return;
        }
        int sec = clamp(parseInt(secondsInput, 4), 1, 15);
        final int duration = sec;
        String name = safeNameFromUrl(web.getUrl()) + "_web_1080p60.mp4";
        setBusy(true);
        stopRequested.set(false);
        status.setText("WEB REC: " + duration + " сек.");
        worker.submit(() -> {
            try {
                H264LottieRenderer renderer = new H264LottieRenderer(this);
                String saved = renderer.renderWebCard(web, duration, name, p -> {
                    if (p % 5 == 0 || p == 100) {
                        runOnUiThread(() -> status.setText("WEB REC: " + p + "%"));
                    }
                });
                runOnUiThread(() -> status.setText("Готово: " + saved + "\nMovies/NFTRecorder"));
            } catch (Throwable e) {
                runOnUiThread(() -> status.setText("WEB ошибка: " + msg(e)));
            } finally {
                setBusy(false);
            }
        });
    }

    private void startBatch() {
        GiftRef ref = parseGiftRef(input.getText().toString().trim());
        if (ref == null) {
            status.setText("Для batch нужна ссылка t.me/nft/Name-123 или fragment.com/gift/name-123.");
            return;
        }

        int from = parseOptionalInt(fromInput, ref.number);
        int to = parseOptionalInt(toInput, from);
        if (to < from) { int t = from; from = to; to = t; }
        if (to - from + 1 > MAX_BATCH) {
            status.setText("За один запуск максимум " + MAX_BATCH + " номеров.");
            return;
        }

        int bgVariants = clamp(parseInt(bgVariantsInput, 5), 0, BG_PALETTE.length);
        int patternVariants = clamp(parseInt(patternVariantsInput, 4), 0, 4);
        final int start = from, end = to, bgs = bgVariants, pats = patternVariants;
        stopRequested.set(false);
        setBusy(true);
        worker.submit(() -> runBatch(ref.base, start, end, bgs, pats));
    }

    private void runBatch(String base, int from, int to, int bgVariants, int patternVariants) {
        int ok = 0, skipped = 0;
        H264LottieRenderer renderer = new H264LottieRenderer(this);
        int total = to - from + 1;

        for (int n = from; n <= to; n++) {
            if (stopRequested.get() || Thread.currentThread().isInterrupted()) break;
            String slug = base + "-" + n;
            int index = n - from;
            int displayIndex = index + 1;
            int okNow = ok, skipNow = skipped;
            runOnUiThread(() -> status.setText("Проверяю " + slug + " [" + displayIndex + "/" + total + "]\n" +
                    "готово " + okNow + " | пропущено " + skipNow));

            try {
                LottieComposition composition = loadComposition(slug);
                int bgIndex = variantIndex(index, bgVariants);
                int patternIndex = variantIndex(index, patternVariants);
                renderer.setStyle(colorForVariant(bgIndex, bgVariants), patternIndex);
                String fileName = slug + "_bg" + bgIndex + "_pat" + patternIndex + "_1080p60.mp4";
                renderer.render(composition, fileName, p -> {
                    if (p == 100 || p % 20 == 0) {
                        runOnUiThread(() -> status.setText("Рендер " + slug + ": " + p + "% [" + displayIndex + "/" + total + "]"));
                    }
                });
                ok++;
            } catch (Throwable e) {
                skipped++;
            }
        }

        int finalOk = ok, finalSkipped = skipped;
        boolean stopped = stopRequested.get();
        runOnUiThread(() -> status.setText((stopped ? "Batch остановлен. " : "Batch готов. ") +
                "Видео: " + finalOk + " | пропущено: " + finalSkipped + "\nMovies/NFTRecorder"));
        setBusy(false);
    }

    private void chooseImportFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            List<ImportItem> parsed = parseImportFile(uri);
            importedItems.clear();
            importedItems.addAll(parsed);
            status.setText("Импортировано задач: " + importedItems.size() + ". Нажми RUN IMPORT.");
        } catch (Throwable e) {
            status.setText("Ошибка импорта: " + msg(e));
        }
    }

    private List<ImportItem> parseImportFile(Uri uri) throws Exception {
        ArrayList<ImportItem> out = new ArrayList<>();
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("Не удалось открыть файл");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("REF|")) continue;
                String[] parts = line.split("\\|");
                String url = parts[0].trim();
                GiftRef ref = parseGiftRef(url);
                if (ref == null) continue;
                Map<String, String> opts = new HashMap<>();
                for (int i = 1; i < parts.length; i++) {
                    int eq = parts[i].indexOf('=');
                    if (eq > 0) opts.put(parts[i].substring(0, eq).trim(), parts[i].substring(eq + 1).trim());
                }
                boolean saveVideo = !"0".equals(opts.get("save_video"));
                if (!saveVideo) continue;
                int bg = parseMapInt(opts, "bg_variants", parseInt(bgVariantsInput, 5));
                int pat = parseMapInt(opts, "pattern_variants", parseInt(patternVariantsInput, 4));
                boolean keepOriginal = "1".equals(opts.get("keep_original_bg"));
                out.add(new ImportItem(ref.base + "-" + ref.number,
                        clamp(bg, 0, BG_PALETTE.length),
                        clamp(pat, 0, 4), keepOriginal));
            }
        }
        return out;
    }

    private void startImportedQueue() {
        if (importedItems.isEmpty()) {
            status.setText("Сначала IMPORT TXT. Поддерживаются обычные URL и v6 строки url|key=value.");
            return;
        }
        ArrayList<ImportItem> jobs = new ArrayList<>(importedItems);
        stopRequested.set(false);
        setBusy(true);
        worker.submit(() -> runImportedQueue(jobs));
    }

    private void runImportedQueue(List<ImportItem> jobs) {
        int ok = 0, skipped = 0;
        H264LottieRenderer renderer = new H264LottieRenderer(this);
        for (int i = 0; i < jobs.size(); i++) {
            if (stopRequested.get() || Thread.currentThread().isInterrupted()) break;
            ImportItem job = jobs.get(i);
            int pos = i + 1;
            int okNow = ok, skipNow = skipped;
            runOnUiThread(() -> status.setText("IMPORT " + pos + "/" + jobs.size() + ": " + job.slug +
                    "\nготово " + okNow + " | пропущено " + skipNow));
            try {
                LottieComposition composition = loadComposition(job.slug);
                int bgIndex = job.keepOriginal ? 0 : variantIndex(i, job.bgVariants);
                int patIndex = job.keepOriginal ? 0 : variantIndex(i, job.patternVariants);
                renderer.setStyle(colorForVariant(bgIndex, job.keepOriginal ? 0 : job.bgVariants), patIndex);
                String name = job.slug + "_bg" + bgIndex + "_pat" + patIndex + "_1080p60.mp4";
                renderer.render(composition, name, p -> {
                    if (p == 100 || p % 25 == 0) {
                        runOnUiThread(() -> status.setText("IMPORT " + pos + "/" + jobs.size() + " — " + p + "%: " + job.slug));
                    }
                });
                ok++;
            } catch (Throwable e) {
                skipped++;
            }
        }
        int finalOk = ok, finalSkipped = skipped;
        boolean stopped = stopRequested.get();
        runOnUiThread(() -> status.setText((stopped ? "Import остановлен. " : "Import готов. ") +
                "Видео: " + finalOk + " | пропущено: " + finalSkipped + "\nMovies/NFTRecorder"));
        setBusy(false);
    }

    private LottieComposition loadComposition(String slug) throws Exception {
        String[] candidates = new String[] {
                "https://nft.fragment.com/gift/" + slug + ".lottie.json",
                "https://nft.fragment.com/gift/" + slug.toLowerCase(Locale.ROOT) + ".lottie.json"
        };
        Throwable last = null;
        for (String url : candidates) {
            LottieResult<LottieComposition> result = LottieCompositionFactory.fromUrlSync(this, url, null);
            if (result.getValue() != null) return result.getValue();
            if (result.getException() != null) last = result.getException();
        }
        throw new Exception("not found", last);
    }

    private static GiftRef parseGiftRef(String value) {
        if (value == null) return null;
        String s = value.trim();
        Matcher m = Pattern.compile("(?:t\\.me/nft/|fragment\\.com/gift/)([A-Za-z0-9_-]+?)-(\\d+)(?:[/?#]|$)", Pattern.CASE_INSENSITIVE).matcher(s + "/");
        if (!m.find()) return null;
        try {
            return new GiftRef(m.group(1), Integer.parseInt(m.group(2)));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalizeUrl(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        s = s.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://" + s;
        Matcher tm = Pattern.compile("https?://t\\.me/nft/([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE).matcher(s);
        if (tm.find()) {
            return "https://fragment.com/gift/" + tm.group(1).toLowerCase(Locale.ROOT) + "?collection=all";
        }
        return s.matches("https?://.+") ? s : null;
    }

    private static String safeNameFromUrl(String url) {
        if (url == null) return "WebGift";
        String s = url.replaceFirst("^https?://", "").replaceAll("[?&#].*$", "");
        s = s.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (s.length() > 80) s = s.substring(s.length() - 80);
        return s.isEmpty() ? "WebGift" : s;
    }

    private static int variantIndex(int itemIndex, int variants) {
        if (variants <= 0) return 0;
        return Math.floorMod(itemIndex, variants);
    }

    private static int colorForVariant(int index, int variants) {
        if (variants <= 0) return Color.BLACK;
        return BG_PALETTE[Math.floorMod(index, Math.min(variants, BG_PALETTE.length))];
    }

    private EditText numberField(String hint, String value) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    private int parseInt(EditText e, int fallback) {
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch (Throwable ignored) { return fallback; }
    }

    private int parseOptionalInt(EditText e, int fallback) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) return fallback;
        try { return Integer.parseInt(s); }
        catch (Throwable ignored) { return fallback; }
    }

    private static int parseMapInt(Map<String, String> map, String key, int fallback) {
        try {
            String v = map.get(key);
            return v == null || v.isEmpty() ? fallback : Integer.parseInt(v);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void setBusy(boolean busy) {
        runOnUiThread(() -> {
            batch.setEnabled(!busy);
            webRec.setEnabled(!busy);
            loadWeb.setEnabled(!busy);
            autoCrop.setEnabled(!busy);
            importFile.setEnabled(!busy);
            runImport.setEnabled(!busy);
            stop.setEnabled(busy);
        });
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String msg(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightWrap() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout.LayoutParams margin(LinearLayout.LayoutParams lp, int topDp) {
        lp.topMargin = dp(topDp);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        stopRequested.set(true);
        worker.shutdownNow();
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private static final class GiftRef {
        final String base;
        final int number;
        GiftRef(String base, int number) {
            this.base = base;
            this.number = number;
        }
    }

    private static final class ImportItem {
        final String slug;
        final int bgVariants;
        final int patternVariants;
        final boolean keepOriginal;
        ImportItem(String slug, int bgVariants, int patternVariants, boolean keepOriginal) {
            this.slug = slug;
            this.bgVariants = bgVariants;
            this.patternVariants = patternVariants;
            this.keepOriginal = keepOriginal;
        }
    }
}
'''

MAIN.write_text(main, encoding="utf-8")

r = RENDERER.read_text(encoding="utf-8")

if "import android.graphics.Paint;" not in r:
    r = r.replace("import android.graphics.Color;\n", "import android.graphics.Color;\nimport android.graphics.Paint;\n")

marker = "public final class H264LottieRenderer {"
if marker not in r:
    raise SystemExit("H264 renderer class marker not found")

if "styleBgColor" not in r:
    style_code = r'''
    private volatile int styleBgColor = Color.BLACK;
    private volatile int stylePattern = 0;

    public void setStyle(int bgColor, int pattern) {
        styleBgColor = bgColor;
        stylePattern = Math.max(0, pattern);
    }

    private void drawStyledBackground(Canvas canvas) {
        canvas.drawColor(styleBgColor);
        int kind = stylePattern % 4;
        if (kind == 0) return;

        int w = canvas.getWidth();
        int h = canvas.getHeight();
        int step = Math.max(42, Math.min(w, h) / 14);
        int luminance = (Color.red(styleBgColor) * 299 + Color.green(styleBgColor) * 587 + Color.blue(styleBgColor) * 114) / 1000;
        int pc = luminance < 130 ? Color.argb(48, 255, 255, 255) : Color.argb(42, 0, 0, 0);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(pc);

        if (kind == 1) {
            p.setStyle(Paint.Style.FILL);
            float rad = Math.max(3f, step * 0.07f);
            for (int y = step / 2; y < h; y += step) {
                int off = ((y / step) % 2) * (step / 2);
                for (int x = step / 2 - off; x < w; x += step) canvas.drawCircle(x, y, rad, p);
            }
        } else if (kind == 2) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, step * 0.025f));
            for (int x = -h; x < w; x += step) canvas.drawLine(x, 0, x + h, h, p);
            for (int x = 0; x < w + h; x += step) canvas.drawLine(x, 0, x - h, h, p);
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, step * 0.024f));
            float arm = step * 0.14f;
            for (int y = step / 2; y < h; y += step) {
                for (int x = step / 2; x < w; x += step) {
                    canvas.drawLine(x - arm, y, x + arm, y, p);
                    canvas.drawLine(x, y - arm, x, y + arm, p);
                }
            }
        }
    }
'''
    r = r.replace(marker, marker + style_code, 1)

web_method = r.find("public String renderWebCard(")
if web_method < 0:
    raise SystemExit("renderWebCard method not found")
head, tail = r[:web_method], r[web_method:]
head2, count = re.subn(r"([A-Za-z_][A-Za-z0-9_]*)\.drawColor\(Color\.BLACK\);", r"drawStyledBackground(\1);", head, count=1)
if count != 1 and "drawStyledBackground(" not in head:
    raise SystemExit("Lottie black background clear not found")
r = head2 + tail

RENDERER.write_text(r, encoding="utf-8")
print("v6 batch/import/autocrop patch applied")
