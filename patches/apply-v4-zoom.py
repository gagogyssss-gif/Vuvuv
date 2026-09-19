from pathlib import Path
p=Path("NFTRecorderProject/nft-recorder/app/src/main/java/com/vuvuv/nftrecorder/MainActivity.java")
s=p.read_text()

# Force pinch zoom even when the site disables it in viewport metadata.
s=s.replace(
'''        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                status.setText("Страница открыта. Если есть проверка Fragment — пройди её. Потом увеличь карточку и нажми REC.");
            }
        });''',
'''        web.setWebChromeClient(new WebChromeClient());
        web.setInitialScale(100);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                String js = "(function(){var m=document.querySelector('meta[name=viewport]');" +
                        "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                        "m.setAttribute('content','width=device-width,initial-scale=1.0,minimum-scale=0.25,maximum-scale=8.0,user-scalable=yes');})();";
                view.evaluateJavascript(js, null);
                status.setText("Страница открыта. Масштабируй двумя пальцами или кнопками − / +. Потом нажми REC.");
            }
        });''')

needle='''        root.addView(web, webLp);

        webRec = new Button(this);'''
insert='''        root.addView(web, webLp);

        LinearLayout zoomRow = new LinearLayout(this);
        zoomRow.setOrientation(LinearLayout.HORIZONTAL);

        Button zoomOut = new Button(this);
        zoomOut.setText("−");
        zoomOut.setOnClickListener(v -> web.zoomOut());

        Button zoomReset = new Button(this);
        zoomReset.setText("RESET");
        zoomReset.setOnClickListener(v -> {
            web.setInitialScale(100);
            web.reload();
        });

        Button zoomIn = new Button(this);
        zoomIn.setText("+");
        zoomIn.setOnClickListener(v -> web.zoomIn());

        zoomRow.addView(zoomOut, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        zoomRow.addView(zoomReset, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        zoomRow.addView(zoomIn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(zoomRow, margin(matchWrap(), 4));

        webRec = new Button(this);'''
if needle not in s:
    raise SystemExit("zoom insertion point not found")
s=s.replace(needle, insert)

p.write_text(s)
