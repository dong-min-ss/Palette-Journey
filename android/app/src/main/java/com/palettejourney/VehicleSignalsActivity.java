package com.palettejourney;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.property.CarPropertyManager;
import ai.pleos.playground.location.fused.FusedLocation;
import ai.pleos.playground.location.fused.listener.FusedLocationListener;
import ai.pleos.playground.location.fused.model.LocationData;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Platform read diagnostic with explicit emulator-only GPS reward collection. Driver controls stay locked. */
public class VehicleSignalsActivity extends Activity {
    private final Handler handler=new Handler();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private volatile boolean networkBusy=false;
    private FusedLocation location; private Car car; private CarPropertyManager properties;
    private volatile Float vehicleSpeed=null; private volatile Integer vehicleGear=null; private volatile long signalAt=0; private volatile int signalCount=0; private TextView view;
    private TextView speedValue,gearValue,gpsValue,rewardValue,detailValue,assetValue;
    private volatile boolean active=false;
    private boolean collect=false; private volatile String participant=null; private LocationData latest=null; private long lastSent=0,lastCatalog=0; private String rewardState="GPS 보상 시연 중지됨";
    private String catalogRegion="gyeongju";
    private String locationState="위치 수신 대기",motionState="차량 상태 수신 대기";
    private void render(){if(!active||speedValue==null)return;
        Float speed=vehicleSpeed;Integer gear=vehicleGear;
        speedValue.setText(speed==null?"—":String.format(java.util.Locale.ROOT,"%.1f",Math.abs(speed)*3.6f));
        String name=gear==null?"—":gear==4?"P":gear==8?"D":gear==2?"R":gear==1?"N":"?";
        gearValue.setText(name+(gear==null?"":" · "+gear));
        gpsValue.setText(latest==null?locationState:String.format(java.util.Locale.ROOT,"위도 %.6f    경도 %.6f",latest.getLatitude(),latest.getLongitude()));
        rewardValue.setText(rewardState);
        detailValue.setText(motionState+"\n\n"+locationState+"\n좌표·속도·기어는 에뮬레이터 입력입니다.");
    }
    private android.widget.LinearLayout card(android.widget.LinearLayout parent,String title){
        android.widget.LinearLayout c=new android.widget.LinearLayout(this);c.setOrientation(1);int pad=DevUi.dp(this,20);c.setPadding(pad,pad,pad,pad);c.setBackground(DevUi.panel(this));
        android.widget.LinearLayout.LayoutParams params=new android.widget.LinearLayout.LayoutParams(-1,-2);params.bottomMargin=DevUi.dp(this,16);parent.addView(c,params);
        TextView label=DevUi.text(this,title,20);label.setTextColor(DevUi.MUTED);c.addView(label);return c;
    }
    private void buildDashboard(){
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(DevUi.BG);
        android.widget.LinearLayout root=new android.widget.LinearLayout(this);root.setOrientation(1);int pad=DevUi.dp(this,24);root.setPadding(pad,pad,pad,pad);scroll.addView(root);setContentView(scroll);
        TextView title=DevUi.text(this,"차량 상태 · LIVE",32);title.setTypeface(null,1);root.addView(title);
        TextView note=DevUi.text(this,"개발용 에뮬레이터 · 시뮬레이션",20);note.setTextColor(DevUi.MUTED);root.addView(note);
        android.widget.LinearLayout metrics=new android.widget.LinearLayout(this);metrics.setOrientation(0);root.addView(metrics);
        android.widget.LinearLayout speedCard=card(metrics,"속도 · km/h");android.widget.LinearLayout.LayoutParams a=new android.widget.LinearLayout.LayoutParams(0,-2,1);a.setMargins(0,DevUi.dp(this,12),DevUi.dp(this,8),DevUi.dp(this,16));speedCard.setLayoutParams(a);
        speedValue=DevUi.text(this,"—",56);speedValue.setTag("speed-value");speedValue.setTypeface(null,1);speedCard.addView(speedValue);
        android.widget.LinearLayout gearCard=card(metrics,"기어");android.widget.LinearLayout.LayoutParams b=new android.widget.LinearLayout.LayoutParams(0,-2,1);b.setMargins(DevUi.dp(this,8),DevUi.dp(this,12),0,DevUi.dp(this,16));gearCard.setLayoutParams(b);
        gearValue=DevUi.text(this,"—",56);gearValue.setTag("gear-value");gearValue.setTypeface(null,1);gearCard.addView(gearValue);
        android.widget.LinearLayout gps=card(root,"GPS 좌표");gpsValue=DevUi.text(this,"위치 수신 대기",26);gps.addView(gpsValue);
        android.widget.LinearLayout reward=card(root,"지역 방문 보상");assetValue=DevUi.text(this,"보유 콘텐츠 확인 중",22);assetValue.setTag("reward-assets");reward.addView(assetValue);rewardValue=DevUi.text(this,rewardState,24);reward.addView(rewardValue);reward.addView(DevUi.text(this,"시연 구역 120초 체류 · 실제 방문 인증 아님",20));
        android.widget.Button toggle=new android.widget.Button(this);toggle.setText(R.string.gps_reward_simulation);DevUi.button(toggle);reward.addView(toggle,new android.widget.LinearLayout.LayoutParams(-1,-2));
        toggle.setOnClickListener(v->{collect=!collect;lastSent=0;rewardState=collect?"GPS 보상 시연 진행 중":"GPS 보상 시연 중지됨";if(!collect)network.execute(()->sendReset());render();});
        android.widget.Button details=new android.widget.Button(this);details.setText("SDK·수신 상세 펼치기");DevUi.button(details);root.addView(details);
        detailValue=DevUi.text(this,"수신 대기",20);detailValue.setTypeface(android.graphics.Typeface.MONOSPACE);detailValue.setVisibility(android.view.View.GONE);root.addView(detailValue);
        details.setOnClickListener(v->{boolean open=detailValue.getVisibility()!=android.view.View.VISIBLE;detailValue.setVisibility(open?android.view.View.VISIBLE:android.view.View.GONE);details.setText(open?"SDK·수신 상세 접기":"SDK·수신 상세 펼치기");});
    }
    private final FusedLocationListener listener=new FusedLocationListener(){
        public void onChanged(LocationData data){handler.post(()->{latest=data;locationState=data==null?"위치 없음":String.format(java.util.Locale.ROOT,"위도 %.6f / 경도 %.6f\nSDK 시각 %d / confidence %.2f",data.getLatitude(),data.getLongitude(),data.getTimestampMillis(),data.getConfidence());render();});}
        public void onError(Exception e){handler.post(()->{latest=null;locationState="위치 오류: "+e.getClass().getSimpleName();render();});}
    };
    @Override public void onCreate(Bundle state){super.onCreate(state);view=new TextView(this);view.setPadding(32,32,32,32);view.setTextSize(22);
        if(!BuildConfig.DEBUG||!(android.os.Build.HARDWARE.contains("ranchu")||android.os.Build.HARDWARE.contains("goldfish"))){view.setText("이 진단 화면은 개발용 에뮬레이터에서만 사용할 수 있어요.");setContentView(view);return;}
        buildDashboard();
        java.util.ArrayList<String> needed=new java.util.ArrayList<>();
        for(String permission:new String[]{"pleos.car.permission.FUSED_LOCATION","android.car.permission.CAR_SPEED"})if(checkSelfPermission(permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED)needed.add(permission);
        if(!needed.isEmpty())requestPermissions(needed.toArray(new String[0]),71);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==71&&active){stopLocation();startLocation();}}
    @Override public void onStart(){super.onStart();if(!BuildConfig.DEBUG||!(android.os.Build.HARDWARE.contains("ranchu")||android.os.Build.HARDWARE.contains("goldfish")))return;active=true;render();startLocation();handler.post(poll);}
    private void startLocation(){try{location=new FusedLocation(this);if(location.isSupported()){location.initialize();location.registerFusedLocationCallback(listener);location.getLastKnownLocation(listener);}else locationState="FusedLocation 미지원";}catch(Exception e){locationState="위치 초기화 실패: "+e.getClass().getSimpleName();}render();}
    private void stopLocation(){if(location!=null){try{location.unregisterFusedLocationCallback(listener);location.release();}catch(Exception ignored){}location=null;}}
    private String connectionMessage(Exception e){
        if("HTTP 401".equals(e.getMessage()))return "가족 연결 만료 · 가족 연결에서 새 시연 가족을 만들어주세요.";
        if("HTTP 403".equals(e.getMessage()))return "서버의 모의 위치 시연이 꺼져 있어요.";
        if(e instanceof IllegalStateException)return "가족 연결이 없어요 · 먼저 새 시연 가족을 만들어주세요.";
        return "서버 전송 실패 · 서버 실행과 연결을 확인해주세요.";
    }
    private void refreshAssets(){try{
        if(participant==null)participant=VehicleApi.load(this);if(participant==null)throw new IllegalStateException();
        org.json.JSONObject catalog=VehicleApi.request("/api/session/catalog","GET",null,participant);
        StringBuilder list=new StringBuilder();int colors=0,stickers=0;
        for(String kind:new String[]{"colors","stickers"}){org.json.JSONArray values=catalog.getJSONArray(kind);for(int i=0;i<values.length();i++){org.json.JSONObject item=values.getJSONObject(i);if(!catalogRegion.equals(item.optString("regionId")))continue;boolean owned=item.getBoolean("unlocked");if(item.optBoolean("acquired")){if(kind.equals("colors"))colors++;else stickers++;}list.append(item.getString("name")).append(!owned?" · 잠김":item.optBoolean("developmentUnlocked")?" · 개발용":"simulation".equals(item.optString("rewardSource"))?" · 모의 보상":" · 해금됨").append("\n");}}
        String regionName="경주";org.json.JSONArray cities=catalog.getJSONArray("cities");for(int i=0;i<cities.length();i++)if(catalogRegion.equals(cities.getJSONObject(i).getString("id")))regionName=cities.getJSONObject(i).getString("name");
        String value=regionName+" 색 "+colors+"/3 · 스티커 "+stickers+"/2\n"+list;
        handler.post(()->{if(active)assetValue.setText(value);});
    }catch(Exception e){handler.post(()->{if(active){assetValue.setText(connectionMessage(e));if("HTTP 401".equals(e.getMessage())){collect=false;rewardState="시연 중단 · 가족 연결 만료";render();}}});}}
    private void sendReset(){if(participant!=null)try{VehicleApi.request("/api/session/visits/vehicle","POST",new org.json.JSONObject().put("source","pleos-emulator").put("valid",false),participant);}catch(Exception ignored){}}
    private final CarPropertyManager.CarPropertyEventCallback signalListener=new CarPropertyManager.CarPropertyEventCallback(){
        public void onChangeEvent(android.car.hardware.CarPropertyValue value){
            if(!active)return;
            if(value.getPropertyId()==VehiclePropertyIds.PERF_VEHICLE_SPEED)vehicleSpeed=value.getStatus()==0?(Float)value.getValue():null;
            if(value.getPropertyId()==VehiclePropertyIds.GEAR_SELECTION)vehicleGear=value.getStatus()==0?(Integer)value.getValue():null;
            signalAt=android.os.SystemClock.elapsedRealtime();signalCount++;
        }
        public void onErrorEvent(int propertyId,int areaId){if(propertyId==VehiclePropertyIds.PERF_VEHICLE_SPEED)vehicleSpeed=null;if(propertyId==VehiclePropertyIds.GEAR_SELECTION)vehicleGear=null;}
    };
    private final Runnable poll=new Runnable(){public void run(){if(!active)return;
        final boolean refresh=android.os.SystemClock.elapsedRealtime()-lastCatalog>=5000;if(refresh)lastCatalog=android.os.SystemClock.elapsedRealtime();
        final boolean sending=collect&&android.os.SystemClock.elapsedRealtime()-lastSent>=5000;
        final LocationData sample=latest;
        if(sending)lastSent=android.os.SystemClock.elapsedRealtime();
        if((sending||refresh)&&!networkBusy){networkBusy=true;network.execute(()->{try{if(sending){try{if(participant==null)participant=VehicleApi.load(VehicleSignalsActivity.this);if(participant==null)throw new IllegalStateException();
            org.json.JSONObject body=new org.json.JSONObject().put("source","pleos-emulator").put("valid",sample!=null);
            if(sample!=null)body.put("latitude",sample.getLatitude()).put("longitude",sample.getLongitude()).put("measuredAt",sample.getTimestampMillis());
            org.json.JSONObject result=VehicleApi.request("/api/session/visits/vehicle","POST",body,participant);
            if(!result.isNull("regionId"))catalogRegion=result.getString("regionId");String regionName=result.optString("regionName","시연 구역");boolean collected=false;org.json.JSONArray receipts=result.getJSONArray("rewards");for(int i=0;i<receipts.length();i++)if(catalogRegion.equals(receipts.getJSONObject(i).getString("regionId")))collected=true;
            String message=!result.optBoolean("locationFresh")?"위치 없음·오래된 위치: 체류 초기화":!result.optBoolean("inRegion")?"GPS 수신 정상 · 시연 구역 밖에서 대기 중":collected?regionName+" 모의 방문 보상 지급 완료 · 색3개/스티커2개":"GPS 수신 정상 · "+regionName+" 체류 "+result.optLong("dwellMs")/1000+" / 120초";
            handler.post(()->{if(active){rewardState=message;render();}});
        }catch(Exception e){handler.post(()->{if(active){rewardState=connectionMessage(e);if("HTTP 401".equals(e.getMessage()))collect=false;render();}});}}
        if(refresh||sending)refreshAssets();}finally{networkBusy=false;}});}
        worker.execute(()->{String value;try{if(car==null){car=Car.createCar(VehicleSignalsActivity.this);properties=(CarPropertyManager)car.getCarManager(Car.PROPERTY_SERVICE);
            if(!properties.registerCallback(signalListener,VehiclePropertyIds.PERF_VEHICLE_SPEED,CarPropertyManager.SENSOR_RATE_ONCHANGE)||!properties.registerCallback(signalListener,VehiclePropertyIds.GEAR_SELECTION,CarPropertyManager.SENSOR_RATE_ONCHANGE))throw new IllegalStateException("SIGNAL_SUBSCRIPTION_FAILED");}
            value="차량 속도 "+(vehicleSpeed==null?"수신 대기":vehicleSpeed)+" m/s / 기어 값 "+(vehicleGear==null?"수신 대기":vehicleGear)+"\n변경 이벤트 "+signalCount+"회 · 마지막 수신 "+(signalAt==0?"없음":(android.os.SystemClock.elapsedRealtime()-signalAt)/1000+"초 전")+"\n정차 허용 판정 미적용";}catch(Exception e){value="속도·기어 조회 실패: "+e.getClass().getSimpleName()+" · 상태 미확인";}String result=value;handler.post(()->{if(active){motionState=result;render();handler.postDelayed(this,1000);}});});}};
    @Override public void onStop(){active=false;handler.removeCallbacks(poll);stopLocation();latest=null;collect=false;network.execute(()->sendReset());worker.execute(()->{if(properties!=null){properties.unregisterCallback(signalListener);properties=null;}if(car!=null){car.disconnect();car=null;}vehicleSpeed=null;vehicleGear=null;});super.onStop();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);worker.shutdown();network.shutdown();super.onDestroy();}
}
