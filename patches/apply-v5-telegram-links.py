from pathlib import Path
p=Path("NFTRecorderProject/nft-recorder/app/src/main/java/com/vuvuv/nftrecorder/MainActivity.java")
s=p.read_text()

# Add Uri import if missing.
if "import android.net.Uri;" not in s:
    s=s.replace("import android.app.Activity;\n", "import android.app.Activity;\nimport android.net.Uri;\n")

# Replace WebViewClient block added by v4 with one that handles Telegram custom scheme.
old='''        web.setWebChromeClient(new WebChromeClient());
        web.setInitialScale(100);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                String js = "(function(){var m=document.querySelector('meta[name=viewport]');" +
                        "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                        "m.setAttribute('content','width=device-width,initial-scale=1.0,minimum-scale=0.25,maximum-scale=8.0,user-scalable=yes');})();";
                view.evaluateJavascript(js, null);
                status.setText("Страница открыта. Масштабируй двумя пальцами или кнопками − / +. Потом нажми REC.");
            }
        });'''

new='''        web.setWebChromeClient(new WebChromeClient());
        web.setInitialScale(100);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return handleWebNavigation(view, request.getUrl().toString());
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleWebNavigation(view, url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                String js = "(function(){var m=document.querySelector('meta[name=viewport]');" +
                        "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                        "m.setAttribute('content','width=device-width,initial-scale=1.0,minimum-scale=0.25,maximum-scale=8.0,user-scalable=yes');})();";
                view.evaluateJavascript(js, null);
                status.setText("Страница открыта. Масштабируй двумя пальцами или кнопками − / +. Потом нажми REC.");
            }
        });'''

if old not in s:
    raise SystemExit("WebViewClient block not found")
s=s.replace(old,new)

# Replace normalizeUrl with Telegram->Fragment conversion for direct entry.
start=s.index("    private static String normalizeUrl(")
end=s.index("    private static String safeNameFromUrl", start)
new_norm=r'''    private boolean handleWebNavigation(WebView view, String url) {
        if (url == null) return false;
        if (url.startsWith("tg:nft")) {
            try {
                Uri uri = Uri.parse(url);
                String slug = uri.getQueryParameter("slug");
                if (slug != null && slug.matches("[A-Za-z0-9_-]+")) {
                    String fallback = "https://fragment.com/gift/" + slug.toLowerCase(Locale.ROOT) + "?collection=all";
                    view.loadUrl(fallback);
                    status.setText("Telegram-ссылка перенаправлена во встроенный Fragment: " + slug);
                    return true;
                }
            } catch (Throwable ignored) {}
            status.setText("Не удалось разобрать Telegram NFT ссылку.");
            return true;
        }
        return false;
    }

    private static String normalizeUrl(String s) {
        if (s == null || s.isEmpty()) return null;
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://" + s;

        Matcher tm = Pattern.compile("https?://t\\.me/nft/([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE).matcher(s);
        if (tm.find()) {
            return "https://fragment.com/gift/" + tm.group(1).toLowerCase(Locale.ROOT) + "?collection=all";
        }

        return s.matches("https?://.+") ? s : null;
    }

'''
s=s[:start]+new_norm+s[end:]
p.write_text(s)
