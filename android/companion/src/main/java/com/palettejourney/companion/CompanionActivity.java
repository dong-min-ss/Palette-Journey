package com.palettejourney.companion;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;

/** Emulator MVP: adb reverse maps this loopback origin to the existing PC server. */
public class CompanionActivity extends Activity {
    static final String ORIGIN = "http://localhost:3000";
    WebView web;
    private PermissionRequest microphone;
    private TextView connection;
    private boolean loadFailed;

    static boolean trusted(Uri uri) {
        return "http".equals(uri.getScheme()) && "localhost".equals(uri.getHost())
            && uri.getPort() == 3000 && uri.getUserInfo() == null;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248,245,237));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            return insets;
        });
        connection = new TextView(this);
        connection.setText("서버에 연결하고 있어요…");
        connection.setPadding(24,16,24,16);
        connection.setTextColor(Color.rgb(91,69,52));
        connection.setOnClickListener(v -> web.loadUrl(ORIGIN + "/"));
        root.addView(connection);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setUserAgentString(web.getSettings().getUserAgentString()+" PaletteCompanion/0.1");
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.setBackgroundColor(Color.rgb(248,245,237));
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) {
                loadFailed=false;
                finishMicrophone(false);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !trusted(request.getUrl());
            }
            @Override public void onPageFinished(WebView view,String url) {
                if(!loadFailed && trusted(Uri.parse(url))) connection.setVisibility(TextView.GONE);
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) {
                if(request.isForMainFrame()) {
                    loadFailed=true;
                    connection.setText("시연 서버에 연결하지 못했어요. PC에서 시작 스크립트를 실행한 뒤 여기를 눌러주세요.");
                    connection.setVisibility(TextView.VISIBLE);
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                if(!trusted(request.getOrigin()) || !trusted(Uri.parse(web.getUrl()))
                    || request.getResources().length != 1
                    || !Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    || microphone != null) { request.deny(); return; }
                microphone = request;
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) finishMicrophone(true);
                else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if(microphone == request) microphone = null;
            }
            @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result) {
                new AlertDialog.Builder(CompanionActivity.this).setMessage(message)
                    .setPositiveButton("확인",(d,w)->result.confirm())
                    .setNegativeButton("취소",(d,w)->result.cancel())
                    .setOnCancelListener(d->result.cancel()).show();
                return true;
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        int lightBars=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            |android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        getWindow().getInsetsController().setSystemBarsAppearance(lightBars,lightBars);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::confirmLeave);
        if(state == null || web.restoreState(state) == null) web.loadUrl(ORIGIN + "/");
    }

    private void finishMicrophone(boolean granted) {
        PermissionRequest pending = microphone; microphone = null;
        if(pending == null) return;
        if(granted && trusted(pending.getOrigin()) && trusted(Uri.parse(web.getUrl())))
            pending.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        else pending.deny();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(code == 10) finishMicrophone(results.length == 1 && results[0] == PackageManager.PERMISSION_GRANTED);
    }
    @Override protected void onSaveInstanceState(Bundle state) { web.saveState(state);super.onSaveInstanceState(state); }
    @Override protected void onPause() {
        web.evaluateJavascript("document.getElementById('voice-player')?.pause();document.dispatchEvent(new Event('palette-app-background'));",null);
        super.onPause();
    }
    @Override protected void onDestroy() { finishMicrophone(false);web.destroy();super.onDestroy(); }
    private void confirmLeave() {
        new AlertDialog.Builder(this).setMessage("그림과 목소리를 저장했나요? 앱을 닫을까요?")
            .setPositiveButton("닫기",(d,w)->finish()).setNegativeButton("계속 작성",null).show();
    }
}
