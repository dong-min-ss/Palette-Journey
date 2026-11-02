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
    private FusedLocation location; private Car car; private TextView view;
    private volatile boolean active=false;
    private boolean collect=false; private volatile String participant=null; private LocationData latest=null; private long lastSent=0; private String rewardState="GPS 보상 시연 중지됨";
    private String locationState="위치 수신 대기",motionState="차량 상태 수신 대기";
    private void render(){if(active)view.setText("차량 API 읽기 진단 · 에뮬레이터는 시뮬레이션\n\n"+locationState+"\n\n"+motionState+"\n\n"+rewardState+"\n시연 구역 2분 체류 · 실제 방문 인증 아님\n운전자 조작 허용에 사용하지 않아요.");}
    private final FusedLocationListener listener=new FusedLocationListener(){
        public void onChanged(LocationData data){handler.post(()->{latest=data;locationState=data==null?"위치 없음":String.format(java.util.Locale.ROOT,"위도 %.6f / 경도 %.6f\nSDK 시각 %d / confidence %.2f",data.getLatitude(),data.getLongitude(),data.getTimestampMillis(),data.getConfidence());render();});}
        public void onError(Exception e){handler.post(()->{latest=null;locationState="위치 오류: "+e.getClass().getSimpleName();render();});}
    };
    @Override public void onCreate(Bundle state){super.onCreate(state);view=new TextView(this);view.setPadding(32,32,32,32);view.setTextSize(22);android.widget.LinearLayout root=new android.widget.LinearLayout(this);root.setOrientation(android.widget.LinearLayout.VERTICAL);root.addView(view);
        android.widget.Button toggle=new android.widget.Button(this);toggle.setText("GPS 보상 시연 시작·중지");root.addView(toggle);setContentView(root);
        boolean emulator=BuildConfig.DEBUG&&(android.os.Build.HARDWARE.contains("ranchu")||android.os.Build.HARDWARE.contains("goldfish"));toggle.setEnabled(emulator);
        toggle.setOnClickListener(v->{collect=!collect;lastSent=0;rewardState=collect?"가족 연결 확인 후 GPS 보상 시연 중":"GPS 보상 시연 중지됨";if(!collect)worker.execute(()->sendReset());render();});
        java.util.ArrayList<String> needed=new java.util.ArrayList<>();
        for(String permission:new String[]{"pleos.car.permission.FUSED_LOCATION","android.car.permission.CAR_SPEED"})if(checkSelfPermission(permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED)needed.add(permission);
        if(!needed.isEmpty())requestPermissions(needed.toArray(new String[0]),71);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==71&&active){stopLocation();startLocation();}}
    @Override public void onStart(){super.onStart();active=true;render();startLocation();handler.post(poll);}
    private void startLocation(){try{location=new FusedLocation(this);if(location.isSupported()){location.initialize();location.registerFusedLocationCallback(listener);location.getLastKnownLocation(listener);}else locationState="FusedLocation 미지원";}catch(Exception e){locationState="위치 초기화 실패: "+e.getClass().getSimpleName();}render();}
    private void stopLocation(){if(location!=null){try{location.unregisterFusedLocationCallback(listener);location.release();}catch(Exception ignored){}location=null;}}
    private void sendReset(){if(participant!=null)try{VehicleApi.request("/api/session/visits/vehicle","POST",new org.json.JSONObject().put("source","pleos-emulator").put("valid",false),participant);}catch(Exception ignored){}}
    private final Runnable poll=new Runnable(){public void run(){if(!active)return;
        final boolean sending=collect&&android.os.SystemClock.elapsedRealtime()-lastSent>=5000;
        final LocationData sample=latest;
        if(sending)lastSent=android.os.SystemClock.elapsedRealtime();
        worker.execute(()->{String value;try{if(car==null)car=Car.createCar(VehicleSignalsActivity.this);CarPropertyManager properties=(CarPropertyManager)car.getCarManager(Car.PROPERTY_SERVICE);float speed=properties.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED,0);int gear=properties.getIntProperty(VehiclePropertyIds.GEAR_SELECTION,0);value="차량 속도 "+speed+" m/s / 기어 값 "+gear+"\n진단용 읽기 성공 (정차 허용 판정 미적용)";}catch(Exception e){value="속도·기어 조회 실패: "+e.getClass().getSimpleName()+" · 상태 미확인";}if(sending){try{if(participant==null)participant=VehicleApi.load(VehicleSignalsActivity.this);if(participant==null)throw new IllegalStateException();
            org.json.JSONObject body=new org.json.JSONObject().put("source","pleos-emulator").put("valid",sample!=null);
            if(sample!=null)body.put("latitude",sample.getLatitude()).put("longitude",sample.getLongitude()).put("measuredAt",sample.getTimestampMillis());
            org.json.JSONObject result=VehicleApi.request("/api/session/visits/vehicle","POST",body,participant);
            String message=result.getJSONArray("rewards").length()>0?"경주 모의 방문 보상 지급 완료 · 색3개/스티커2개":result.optBoolean("locationFresh")?"경주 시연 구역 체류 "+result.optLong("dwellMs")/1000+" / 120초":"위치 없음·오래된 위치: 체류 초기화";
            handler.post(()->{if(active){rewardState=message;render();}});
        }catch(Exception e){handler.post(()->{if(active){rewardState="전송 실패 · 가족 연결/서버/시연 설정을 확인해주세요.";render();}});}}
        String result=value;handler.post(()->{if(active){motionState=result;render();handler.postDelayed(this,1000);}});});}};
    @Override public void onStop(){active=false;handler.removeCallbacks(poll);stopLocation();latest=null;collect=false;worker.execute(()->{sendReset();if(car!=null){car.disconnect();car=null;}});super.onStop();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
}
