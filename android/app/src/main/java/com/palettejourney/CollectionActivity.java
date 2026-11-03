package com.palettejourney;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class CollectionActivity extends Activity {
    private final Handler main=new Handler();private final ExecutorService network=Executors.newSingleThreadExecutor();
    private WebView catalog;private TextView status;private Button refresh;private boolean active,busy;private int epoch;private String signature,credential;private JSONObject latest;
    private boolean allowed(){return BuildConfig.DEBUG&&("ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE));}
    @Override public void onCreate(Bundle state){super.onCreate(state);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(DevUi.dp(this,24),DevUi.dp(this,12),DevUi.dp(this,24),DevUi.dp(this,12));root.setBackgroundColor(DevUi.BG);
        LinearLayout bar=new LinearLayout(this);bar.addView(DevUi.text(this,"색·스티커 사전",28),new LinearLayout.LayoutParams(0,-2,1));refresh=new Button(this);refresh.setText("새로고침");bar.addView(refresh);Button back=new Button(this);back.setText("돌아가기");bar.addView(back);root.addView(bar);status=DevUi.text(this,"가족 보유 목록 확인 중",20);status.setTag("collection-status");root.addView(status);
        catalog=new WebView(this);catalog.setTag("collection-browser");catalog.setBackgroundColor(DevUi.BG);catalog.getSettings().setJavaScriptEnabled(true);catalog.getSettings().setAllowFileAccess(false);catalog.getSettings().setAllowContentAccess(false);catalog.getSettings().setBlockNetworkLoads(true);catalog.getSettings().setDomStorageEnabled(false);catalog.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);catalog.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){if(active&&latest!=null){view.evaluateJavascript("window.collection?.update("+CollectionRenderer.decode(CollectionRenderer.encoded(latest))+")",null);status.setText("가족 공유 · 모의 방문 컬렉션");}}@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}});root.addView(catalog,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);DevUi.styleTree(root);back.setOnClickListener(v->finish());refresh.setOnClickListener(v->load());}
    private void load(){if(!active||!allowed()||busy)return;busy=true;refresh.setEnabled(false);int expected=epoch;
        network.execute(()->{try{String token=VehicleApi.load(this);if(token==null)throw new java.io.IOException("HTTP 401");JSONObject data=VehicleApi.request("/api/session/catalog","GET",null,token);String next=data.toString();boolean replace=signature==null||!token.equals(credential);String html=replace?CollectionRenderer.html(this,data):null;
            main.post(()->{if(!active||epoch!=expected)return;if(html!=null)catalog.loadDataWithBaseURL("https://collection.palette.invalid/",html,"text/html","UTF-8",null);else if(!next.equals(signature))catalog.evaluateJavascript("window.collection?.update("+CollectionRenderer.decode(CollectionRenderer.encoded(data))+")",null);latest=data;signature=next;credential=token;status.setText(html!=null?"사전 준비 중":"가족 공유 · 모의 방문 컬렉션");busy=false;refresh.setEnabled(true);});
        }catch(Exception e){main.post(()->{if(!active||epoch!=expected)return;busy=false;refresh.setEnabled(true);boolean expired="HTTP 401".equals(e.getMessage());if(expired){catalog.loadData("","text/html","UTF-8");signature=null;credential=null;latest=null;}status.setText(expired?"가족 연결 후 보유 항목을 확인해주세요.":"연결 실패 · 새로고침으로 다시 확인해주세요.");});}});
    }
    private final Runnable poll=new Runnable(){public void run(){if(!active)return;load();main.postDelayed(this,5000);}};
    @Override protected void onStart(){super.onStart();active=true;epoch++;busy=false;if(!allowed()){status.setText("개발용 에뮬레이터에서 확인할 수 있어요.");refresh.setEnabled(false);return;}load();main.postDelayed(poll,5000);}
    @Override protected void onStop(){active=false;epoch++;busy=false;main.removeCallbacks(poll);catalog.loadData("","text/html","UTF-8");signature=null;credential=null;super.onStop();}
    @Override protected void onDestroy(){catalog.destroy();network.shutdownNow();super.onDestroy();}
}
