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

/** Read-only API diagnostic. Never grants rewards or unlocks driver controls. */
public class VehicleSignalsActivity extends Activity {
    private final Handler handler=new Handler();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private FusedLocation location; private Car car; private TextView view;
    private volatile boolean active=false;
    private String locationState="위치 수신 대기",motionState="차량 상태 수신 대기";
    private void render(){if(active)view.setText("차량 API 읽기 진단 · 에뮬레이터는 시뮬레이션\n\n"+locationState+"\n\n"+motionState+"\n\n방문 보상 미연결 · 운전자 조작 허용에 사용하지 않아요.");}
    private final FusedLocationListener listener=new FusedLocationListener(){
        public void onChanged(LocationData data){handler.post(()->{locationState=data==null?"위치 없음":String.format(java.util.Locale.ROOT,"위도 %.6f / 경도 %.6f\nSDK 시각 %d / confidence %.2f",data.getLatitude(),data.getLongitude(),data.getTimestampMillis(),data.getConfidence());render();});}
        public void onError(Exception e){handler.post(()->{locationState="위치 오류: "+e.getClass().getSimpleName();render();});}
    };
    @Override public void onCreate(Bundle state){super.onCreate(state);view=new TextView(this);view.setPadding(32,32,32,32);view.setTextSize(22);setContentView(view);
        java.util.ArrayList<String> needed=new java.util.ArrayList<>();
        for(String permission:new String[]{"pleos.car.permission.FUSED_LOCATION","android.car.permission.CAR_SPEED"})if(checkSelfPermission(permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED)needed.add(permission);
        if(!needed.isEmpty())requestPermissions(needed.toArray(new String[0]),71);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==71&&active){stopLocation();startLocation();}}
    @Override public void onStart(){super.onStart();active=true;render();startLocation();handler.post(poll);}
    private void startLocation(){try{location=new FusedLocation(this);if(location.isSupported()){location.initialize();location.registerFusedLocationCallback(listener);location.getLastKnownLocation(listener);}else locationState="FusedLocation 미지원";}catch(Exception e){locationState="위치 초기화 실패: "+e.getClass().getSimpleName();}render();}
    private void stopLocation(){if(location!=null){try{location.unregisterFusedLocationCallback(listener);location.release();}catch(Exception ignored){}location=null;}}
    private final Runnable poll=new Runnable(){public void run(){if(!active)return;worker.execute(()->{String value;try{if(car==null)car=Car.createCar(VehicleSignalsActivity.this);CarPropertyManager properties=(CarPropertyManager)car.getCarManager(Car.PROPERTY_SERVICE);float speed=properties.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED,0);int gear=properties.getIntProperty(VehiclePropertyIds.GEAR_SELECTION,0);value="차량 속도 "+speed+" m/s / 기어 값 "+gear+"\n진단용 읽기 성공 (정차 허용 판정 미적용)";}catch(Exception e){value="속도·기어 조회 실패: "+e.getClass().getSimpleName()+" · 상태 미확인";}String result=value;handler.post(()->{if(active){motionState=result;render();handler.postDelayed(this,1000);}});});}};
    @Override public void onStop(){active=false;handler.removeCallbacks(poll);stopLocation();worker.execute(()->{if(car!=null){car.disconnect();car=null;}});super.onStop();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
}
