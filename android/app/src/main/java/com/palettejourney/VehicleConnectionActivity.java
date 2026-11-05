package com.palettejourney;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.widget.*;
import android.graphics.Bitmap;
import org.json.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;

/** Emulator-only pairing; microphone archives remain on the device. */
public class VehicleConnectionActivity extends VehicleActivity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler();
    private TextView status,codeLabel,rewards;private EditText code,origin;private Spinner pages;private ImageView qr;
    private String token,codeInput,originInput;private int pagePosition;private JSONArray pageList=new JSONArray();
    private LinearLayout root;private Button startPairing,issueQr,sharePage;private boolean working=false,visible=false,connected=false;private int qrGeneration=0;
    interface Work {void run()throws Exception;}
    private void run(Work work){if(working)return;codeInput=code==null?"":code.getText().toString();originInput=origin==null?"":origin.getText().toString();pagePosition=pages==null?-1:pages.getSelectedItemPosition();working=true;setButtons(false);executor.execute(()->{try{work.run();}catch(Exception e){handler.post(()->{if(token==null||"HTTP 401".equals(e.getMessage()))connected=false;status.setText("연결을 확인해주세요. 세션이 만료됐다면 다시 연결할 수 있어요.");});}finally{handler.post(()->{working=false;setButtons(true);});}});}
    private void setButtons(boolean enabled){
        setButtons(root,enabled);
        if(startPairing!=null)startPairing.setVisibility(connected?android.view.View.GONE:android.view.View.VISIBLE);
        if(issueQr!=null)issueQr.setEnabled(enabled&&connected);
        if(sharePage!=null)sharePage.setEnabled(enabled&&connected&&pageList.length()>0);
    }
    private void setButtons(android.view.View view,boolean enabled){if(view instanceof Button)view.setEnabled(enabled);if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)setButtons(group.getChildAt(i),enabled);}}
    private Button button(String text,Work work){Button button=new Button(this);button.setText(text);DevUi.button(button);root.addView(button);button.setOnClickListener(v->run(work));return button;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,24,28,24);ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);root.setBackgroundColor(DevUi.BG);root.post(()->DevUi.styleTree(root));
        TextView title=DevUi.text(this,"가족 연결",32);title.setAccessibilityHeading(true);root.addView(title);status=new TextView(this);status.setTextSize(22);status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        if(!BuildConfig.DEBUG||!(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"))){status.setText("차량 상태 미확인: 연결 조작이 잠겨 있어요.");return;}
        status.setText("가족을 연결하고 휴대폰으로 이어 그려요.");
        code=new EditText(this);code.setHint("기존 웹의 24자리 연결 코드");code.setSingleLine(true);root.addView(code);
        button("기존 웹 가족에 연결",()->{String input=codeInput.trim().toLowerCase(java.util.Locale.ROOT);if(!input.matches("[a-f0-9]{24}"))throw new Exception();String candidate=VehicleApi.randomToken();VehicleApi.request("/api/sessions/join","POST",new JSONObject().put("code",input),candidate);VehicleApi.save(this,candidate);token=candidate;loadLibrary();});
        button("차량 가족 연결 시작·재연결",()->{String owner=VehicleApi.load(this,"owner");if(owner==null){owner=VehicleApi.randomToken();VehicleApi.save(this,"owner",owner);}VehicleApi.request("/api/account","PUT",new JSONObject(),owner);JSONObject session=VehicleApi.request("/api/sessions","POST",new JSONObject(),owner);String candidate=VehicleApi.randomToken();VehicleApi.request("/api/sessions/join","POST",new JSONObject().put("code",session.getString("code")),candidate);VehicleApi.save(this,candidate);token=candidate;loadLibrary();});
        button("새 시연 가족 · 지역 잠금 상태",()->{String owner=VehicleApi.randomToken();VehicleApi.request("/api/account/demo","PUT",new JSONObject(),owner);JSONObject session=VehicleApi.request("/api/sessions","POST",new JSONObject(),owner);String member=VehicleApi.randomToken();VehicleApi.request("/api/sessions/join","POST",new JSONObject().put("code",session.getString("code")),member);VehicleApi.saveDemo(this,member);token=member;clearPairing();loadLibrary();});
        button("기존 가족으로 돌아가기",()->{VehicleApi.useDemo(this,false);clearPairing();token=VehicleApi.load(this);if(token==null){handler.post(()->{connected=false;status.setText("기존 연결이 없어요. 가족 연결 코드를 입력해주세요.");rewards.setText("");pageList=new JSONArray();pages.setAdapter(DevUi.spinner(this,new String[]{}));});return;}loadLibrary();});
        rewards=new TextView(this);root.addView(rewards);
        button(getString(R.string.vehicle_signals),()->handler.post(()->startActivity(new android.content.Intent(this,VehicleSignalsActivity.class))));
        pages=new Spinner(this);root.addView(pages);
        button("선택 페이지 함께 보기",()->{requireToken();int position=pagePosition;if(position<0)throw new Exception();JSONObject latest=VehicleApi.request("/api/session/connection","GET",null,token);VehicleApi.request("/api/session/connection","PUT",new JSONObject().put("pageId",pageList.getJSONObject(position).getString("id")).put("revision",latest.getInt("revision")),token);loadLibrary();});
        origin=new EditText(this);origin.setSingleLine(true);origin.setHint("휴대폰 접속 주소: http://PC의Wi-Fi주소:3000");origin.setText(getPreferences(0).getString("webOrigin","http://localhost:3000"));root.addView(origin);
        button("휴대폰 연결 QR 발급 · 5분",()->{requireToken();String address=originInput.trim();java.net.URI uri=new java.net.URI(address);if(!("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()||"/".equals(uri.getPath())))throw new Exception();address=address.replaceAll("/$","");JSONObject invite=VehicleApi.request("/api/session/invitations","POST",new JSONObject(),token);String link=address+"/#join="+invite.getString("code");BitMatrix matrix=new QRCodeWriter().encode(link,BarcodeFormat.QR_CODE,400,400);Bitmap bitmap=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);for(int y=0;y<400;y++)for(int x=0;x<400;x++)bitmap.setPixel(x,y,matrix.get(x,y)?0xff000000:0xffffffff);String value=address;int generation=++qrGeneration;long expiry=android.os.SystemClock.elapsedRealtime()+Math.max(0,Math.min(300000,invite.getLong("joinExpiresAt")-invite.getLong("serverNow")));handler.post(()->{if(!visible||generation!=qrGeneration){bitmap.recycle();return;}getPreferences(0).edit().putString("webOrigin",value).apply();qr.setImageBitmap(bitmap);qr.setVisibility(android.view.View.VISIBLE);codeLabel.setText("가족 비공개 · 5분 유효\n"+link+"");handler.postDelayed(()->{if(generation==qrGeneration&&android.os.SystemClock.elapsedRealtime()>=expiry){hideQr();codeLabel.setText("연결 코드가 만료됐어요. 다시 발급해주세요.");}},300000);});});
        codeLabel=new TextView(this);codeLabel.setTextIsSelectable(true);root.addView(codeLabel);qr=new ImageView(this);root.addView(qr,new LinearLayout.LayoutParams(400,400));
        button("연결 상태·페이지 새로고침",()->{requireToken();loadLibrary();});
        button("여행책 감상",()->handler.post(()->startActivity(new android.content.Intent(this,JourneyBookActivity.class))));
        button("색·스티커 사전",()->handler.post(()->startActivity(new android.content.Intent(this,CollectionActivity.class))));
        button("개발자 주행 시연",()->handler.post(()->startActivity(new android.content.Intent(this,DevDriveActivity.class))));
        // Group existing controls without changing pairing actions or credentials.
        java.util.List<android.view.View> controls=new java.util.ArrayList<>();for(int i=0;i<root.getChildCount();i++)controls.add(root.getChildAt(i));
        java.util.function.Function<String,Button> find=label->{for(android.view.View v:controls)if(v instanceof Button&&label.equals(((Button)v).getText().toString()))return (Button)v;return null;};
        LinearLayout pairing=DevUi.section(root,"휴대폰으로 이어 그리기");
        pairing.addView(DevUi.text(this,"처음이라면 가족 연결을 시작한 뒤 휴대폰 카메라로 QR을 찍으세요.",20));
        Button reconnect=find.apply("차량 가족 연결 시작·재연결");
        startPairing=new Button(this);startPairing.setText("가족 연결 시작");startPairing.setTag("primary-action");
        startPairing.setOnClickListener(v->reconnect.performClick());pairing.addView(startPairing);
        issueQr=find.apply("휴대폰 연결 QR 발급 · 5분");
        DevUi.moveTo(pairing,issueQr);DevUi.moveTo(pairing,qr);qr.setAdjustViewBounds(true);qr.setMaxHeight(DevUi.dp(this,240));
        qr.setContentDescription("휴대폰 가족 연결 QR 코드");qr.setVisibility(android.view.View.GONE);
        LinearLayout address=DevUi.fold(pairing,"접속 주소·연결 링크");DevUi.moveTo(address,origin);DevUi.moveTo(address,codeLabel);address.addView(DevUi.text(this,"같은 Wi-Fi · 5분 유효 · 가족 비공개",20));
        LinearLayout shared=DevUi.section(root,"함께 보는 그림");DevUi.moveTo(shared,pages);sharePage=find.apply("선택 페이지 함께 보기");DevUi.moveTo(shared,sharePage);DevUi.moveTo(shared,rewards);
        DevUi.moveTo(shared,find.apply("여행책 감상"));
        DevUi.moveTo(shared,find.apply("색·스티커 사전"));
        LinearLayout manage=DevUi.fold(root,"가족 관리");DevUi.moveTo(manage,find.apply("차량 가족 연결 시작·재연결"));DevUi.moveTo(manage,code);DevUi.moveTo(manage,find.apply("기존 웹 가족에 연결"));DevUi.moveTo(manage,find.apply("기존 가족으로 돌아가기"));DevUi.moveTo(manage,find.apply("연결 상태·페이지 새로고침"));
        LinearLayout demo=DevUi.fold(root,"개발용 시연");DevUi.moveTo(demo,find.apply("개발자 주행 시연"));DevUi.moveTo(demo,find.apply("새 시연 가족 · 지역 잠금 상태"));DevUi.moveTo(demo,find.apply(getString(R.string.vehicle_signals)));
        try{token=VehicleApi.load(this);}catch(Exception ignored){status.setText("저장한 연결을 열 수 없어요. 새 코드로 다시 연결해주세요.");}
        setButtons(true);
        if(token!=null)run(this::loadLibrary);
    }
    private void hideQr(){qr.setImageDrawable(null);qr.setVisibility(android.view.View.GONE);}
    private void clearPairing(){handler.post(()->{qrGeneration++;hideQr();codeLabel.setText("가족이 변경됐어요. QR을 다시 발급해주세요.");});}
    private void requireToken()throws Exception{if(token==null)throw new Exception();}
    private void refreshRewards()throws Exception{
        JSONObject palette=VehicleApi.request("/api/session/palette","GET",null,token),catalog=VehicleApi.request("/api/session/catalog","GET",null,token);
        JSONArray colors=palette.getJSONArray("colors"),stickers=catalog.getJSONArray("stickers");int owned=0;for(int i=0;i<stickers.length();i++)if(stickers.getJSONObject(i).optBoolean("acquired"))owned++;
        String value=colors.length()+"색 · 스티커 "+owned+"개"+(VehicleApi.isDemo(this)?" · 시연 가족":"");handler.post(()->{if(!isDestroyed())rewards.setText(value);});
    }
    private void loadLibrary()throws Exception{
        refreshRewards();
        JSONObject library=VehicleApi.request("/api/session/library","GET",null,token),connection=VehicleApi.request("/api/session/connection","GET",null,token);
        String webOrigin=connection.optString("webOrigin","http://localhost:3000");
        JSONArray list=library.getJSONArray("pages"),diaries=library.getJSONArray("diaries"),children=library.getJSONArray("children"),trips=library.getJSONArray("trips");String[] names=new String[list.length()];
        for(int i=0;i<list.length();i++){JSONObject page=list.getJSONObject(i);String prefix="";for(int j=0;j<diaries.length();j++)if(diaries.getJSONObject(j).getString("id").equals(page.getString("diaryId"))){JSONObject diary=diaries.getJSONObject(j);prefix=diary.getString("title")+" / ";for(int k=0;k<children.length();k++)if(children.getJSONObject(k).getString("id").equals(diary.getString("childId")))prefix=children.getJSONObject(k).getString("name")+" / "+prefix;for(int k=0;k<trips.length();k++)if(trips.getJSONObject(k).getString("id").equals(diary.getString("tripId")))prefix=trips.getJSONObject(k).getString("title")+" / "+prefix;}names[i]=prefix+page.getString("title");}
        handler.post(()->{if(isDestroyed())return;connected=true;pageList=list;if(!getPreferences(0).contains("webOrigin"))origin.setText(webOrigin);pages.setAdapter(DevUi.spinner(this,names));JSONObject page=connection.optJSONObject("page");status.setText("연결됨 · "+(page==null?"그림 선택 대기":page.optString("title"))+"");});
    }
    private final Runnable poll=new Runnable(){public void run(){if(!visible)return;if(token!=null&&!working)VehicleConnectionActivity.this.run(()->{refreshRewards();JSONObject connection=VehicleApi.request("/api/session/connection","GET",null,token);handler.post(()->{JSONObject page=connection.optJSONObject("page");status.setText("연결됨 · "+(page==null?"그림 선택 대기":page.optString("title"))+"");});});handler.postDelayed(this,5000);}};
    @Override protected void onStart(){super.onStart();visible=true;handler.postDelayed(poll,5000);}
    @Override protected void onStop(){super.onStop();visible=false;handler.removeCallbacks(poll);qrGeneration++;if(qr!=null)hideQr();if(codeLabel!=null)codeLabel.setText("QR은 다시 발급해주세요.");}
    @Override protected void onDestroy(){super.onDestroy();handler.removeCallbacksAndMessages(null);executor.shutdownNow();}
}
