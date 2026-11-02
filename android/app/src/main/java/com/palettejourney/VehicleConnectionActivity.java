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
public class VehicleConnectionActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler();
    private TextView status,codeLabel,rewards;private EditText code,origin;private Spinner pages;private ImageView qr;
    private String token,codeInput,originInput;private int pagePosition;private JSONArray pageList=new JSONArray();
    private LinearLayout root;private boolean working=false,visible=false;private int qrGeneration=0;
    interface Work {void run()throws Exception;}
    private void run(Work work){if(working)return;codeInput=code==null?"":code.getText().toString();originInput=origin==null?"":origin.getText().toString();pagePosition=pages==null?-1:pages.getSelectedItemPosition();working=true;setButtons(false);executor.execute(()->{try{work.run();}catch(Exception e){handler.post(()->status.setText("연결하지 못했어요. 서버 실행·Wi-Fi·코드 만료를 확인해주세요."));}finally{handler.post(()->{working=false;setButtons(true);});}});}
    private void setButtons(boolean enabled){for(int i=0;i<root.getChildCount();i++)if(root.getChildAt(i) instanceof Button)root.getChildAt(i).setEnabled(enabled);}
    private void button(String text,Work work){Button button=new Button(this);button.setText(text);root.addView(button);button.setOnClickListener(v->run(work));}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,24,28,24);ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
        status=new TextView(this);status.setTextSize(22);root.addView(status);
        if(!BuildConfig.DEBUG||!(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"))){status.setText("차량 상태 미확인: 연결 조작이 잠겨 있어요.");return;}
        status.setText("에뮬레이터 연결 시연 · 녹음은 전송하지 않아요.\n차량 가족을 시작하거나 웹 메뉴의 연결 코드를 입력하세요. 차량 가족 재연결 시 이전 연결은 만료돼요.");
        code=new EditText(this);code.setHint("기존 웹의 24자리 연결 코드");code.setSingleLine(true);root.addView(code);
        button("기존 웹 가족에 연결",()->{String input=codeInput.trim().toLowerCase(java.util.Locale.ROOT);if(!input.matches("[a-f0-9]{24}"))throw new Exception();String candidate=VehicleApi.randomToken();VehicleApi.request("/api/sessions/join","POST",new JSONObject().put("code",input),candidate);VehicleApi.save(this,candidate);token=candidate;loadLibrary();});
        button("차량 가족 연결 시작·재연결",()->{String owner=VehicleApi.load(this,"owner");if(owner==null){owner=VehicleApi.randomToken();VehicleApi.save(this,"owner",owner);}VehicleApi.request("/api/account","PUT",new JSONObject(),owner);JSONObject session=VehicleApi.request("/api/sessions","POST",new JSONObject(),owner);String candidate=VehicleApi.randomToken();VehicleApi.request("/api/sessions/join","POST",new JSONObject().put("code",session.getString("code")),candidate);VehicleApi.save(this,candidate);token=candidate;loadLibrary();});
        rewards=new TextView(this);root.addView(rewards);
        pages=new Spinner(this);root.addView(pages);
        button("선택 페이지 함께 보기",()->{requireToken();int position=pagePosition;if(position<0)throw new Exception();JSONObject latest=VehicleApi.request("/api/session/connection","GET",null,token);VehicleApi.request("/api/session/connection","PUT",new JSONObject().put("pageId",pageList.getJSONObject(position).getString("id")).put("revision",latest.getInt("revision")),token);loadLibrary();});
        origin=new EditText(this);origin.setSingleLine(true);origin.setHint("휴대폰 접속 주소: http://PC의Wi-Fi주소:3000");origin.setText(getPreferences(0).getString("webOrigin","http://localhost:3000"));root.addView(origin);
        button("휴대폰 연결 QR 발급 · 5분",()->{requireToken();String address=originInput.trim();java.net.URI uri=new java.net.URI(address);if(!("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()||"/".equals(uri.getPath())))throw new Exception();address=address.replaceAll("/$","");JSONObject invite=VehicleApi.request("/api/session/invitations","POST",new JSONObject(),token);String link=address+"/#join="+invite.getString("code");BitMatrix matrix=new QRCodeWriter().encode(link,BarcodeFormat.QR_CODE,400,400);Bitmap bitmap=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);for(int y=0;y<400;y++)for(int x=0;x<400;x++)bitmap.setPixel(x,y,matrix.get(x,y)?0xff000000:0xffffffff);String value=address;int generation=++qrGeneration;long expiry=android.os.SystemClock.elapsedRealtime()+Math.max(0,Math.min(300000,invite.getLong("joinExpiresAt")-invite.getLong("serverNow")));handler.post(()->{if(!visible||generation!=qrGeneration){bitmap.recycle();return;}getPreferences(0).edit().putString("webOrigin",value).apply();qr.setImageBitmap(bitmap);codeLabel.setText("같은 Wi-Fi에서 스캔하세요. 연결 기기는 가족 일기에 접근해요.\n"+link+"\n5분 뒤 만료 · HTTP에서는 휴대폰 마이크 사용 불가");handler.postDelayed(()->{if(generation==qrGeneration&&android.os.SystemClock.elapsedRealtime()>=expiry){qr.setImageDrawable(null);codeLabel.setText("연결 코드가 만료됐어요. 다시 발급해주세요.");}},300000);});});
        codeLabel=new TextView(this);codeLabel.setTextIsSelectable(true);root.addView(codeLabel);qr=new ImageView(this);root.addView(qr,new LinearLayout.LayoutParams(400,400));
        button("연결 상태·페이지 새로고침",()->{requireToken();loadLibrary();});
        try{token=VehicleApi.load(this);}catch(Exception ignored){status.setText("저장한 연결을 열 수 없어요. 새 코드로 다시 연결해주세요.");}
        if(token!=null)run(this::loadLibrary);
    }
    private void requireToken()throws Exception{if(token==null)throw new Exception();}
    private void refreshRewards()throws Exception{
        JSONObject palette=VehicleApi.request("/api/session/palette","GET",null,token),catalog=VehicleApi.request("/api/session/catalog","GET",null,token);
        StringBuilder text=new StringBuilder("가족 공유 팔레트 · ");JSONArray colors=palette.getJSONArray("colors"),stickers=catalog.getJSONArray("stickers");text.append(colors.length()).append("색\n");
        for(int i=0;i<colors.length();i++){JSONObject c=colors.getJSONObject(i);if("region".equals(c.optString("source")))text.append(c.optString("name")).append(" ").append(c.optString("hex")).append("\n");}
        for(int i=0;i<stickers.length();i++){JSONObject item=stickers.getJSONObject(i);if("regional".equals(item.optString("kind"))&&item.optBoolean("unlocked"))text.append(item.getString("name")).append(item.optBoolean("developmentUnlocked")?" · 개발용":" · 모의 방문 보상").append("\n");}
        text.append("실제 방문 인증 아님 · Pleos 위치 미연동");String value=text.toString();handler.post(()->{if(!isDestroyed())rewards.setText(value);});
    }
    private void loadLibrary()throws Exception{
        refreshRewards();
        JSONObject library=VehicleApi.request("/api/session/library","GET",null,token),connection=VehicleApi.request("/api/session/connection","GET",null,token);
        String webOrigin=connection.optString("webOrigin","http://localhost:3000");
        JSONArray list=library.getJSONArray("pages"),diaries=library.getJSONArray("diaries"),children=library.getJSONArray("children"),trips=library.getJSONArray("trips");String[] names=new String[list.length()];
        for(int i=0;i<list.length();i++){JSONObject page=list.getJSONObject(i);String prefix="";for(int j=0;j<diaries.length();j++)if(diaries.getJSONObject(j).getString("id").equals(page.getString("diaryId"))){JSONObject diary=diaries.getJSONObject(j);prefix=diary.getString("title")+" / ";for(int k=0;k<children.length();k++)if(children.getJSONObject(k).getString("id").equals(diary.getString("childId")))prefix=children.getJSONObject(k).getString("name")+" / "+prefix;for(int k=0;k<trips.length();k++)if(trips.getJSONObject(k).getString("id").equals(diary.getString("tripId")))prefix=trips.getJSONObject(k).getString("title")+" / "+prefix;}names[i]=prefix+page.getString("title");}
        handler.post(()->{if(isDestroyed())return;pageList=list;if(!getPreferences(0).contains("webOrigin"))origin.setText(webOrigin);pages.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));JSONObject page=connection.optJSONObject("page");status.setText("웹 가족 연결됨 · 함께 보는 페이지: "+(page==null?"아직 선택하지 않음":page.optString("title"))+"\n연결은 최대 8시간 유지돼요. 녹음은 차량에만 보관돼요.");});
    }
    private final Runnable poll=new Runnable(){public void run(){if(!visible)return;if(token!=null&&!working)VehicleConnectionActivity.this.run(()->{refreshRewards();JSONObject connection=VehicleApi.request("/api/session/connection","GET",null,token);handler.post(()->{JSONObject page=connection.optJSONObject("page");status.setText("웹 가족 연결됨 · 함께 보는 페이지: "+(page==null?"아직 선택하지 않음":page.optString("title"))+"\n녹음은 차량에만 보관돼요.");});});handler.postDelayed(this,5000);}};
    @Override protected void onStart(){super.onStart();visible=true;handler.postDelayed(poll,5000);}
    @Override protected void onStop(){super.onStop();visible=false;handler.removeCallbacks(poll);qrGeneration++;if(qr!=null)qr.setImageDrawable(null);if(codeLabel!=null)codeLabel.setText("QR은 다시 발급해주세요.");}
    @Override protected void onDestroy(){super.onDestroy();handler.removeCallbacksAndMessages(null);executor.shutdownNow();}
}
