package com.palettejourney;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.property.CarPropertyManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.LinearLayout;
import android.widget.TextView;
import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.Light;
import ai.pleos.playground.vehicle.api.model.area.SeatArea;
import ai.pleos.playground.vehicle.api.model.capability.AccessType;
import kotlin.Unit;
import org.json.JSONObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Foreground, opt-in emulator demonstration. No service or background lighting writes. */
public class AmbientActivity extends VehicleActivity {
    private final Handler main=new Handler();
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private volatile boolean active;
    private volatile int generation;
    private boolean live(int epoch){return active&&epoch==generation;}
    private boolean busy;
    private Vehicle vehicle;
    private Car car;
    private CarPropertyManager properties;
    private TextView status,color;
    private android.view.View swatch;
    private void show(String text){main.post(()->{if(active)status.setText(text);});}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,24,24,24);root.setBackgroundColor(DevUi.BG);
        root.addView(DevUi.text(this,"그림 색과 차량 조명",30));
        root.addView(DevUi.text(this,"같은 가족의 폰 앱 → 차량 연결 → 색 조명 연동 켜기\n이 화면을 열어둔 동안 정차 상태에서만 선택 색을 적용해요.",22));
        color=DevUi.text(this,"선택한 색 대기",28);root.addView(color);
        swatch=new android.view.View(this);root.addView(swatch,new LinearLayout.LayoutParams(-1,DevUi.dp(this,100)));
        status=DevUi.text(this,"차량 조명 연결 준비 중",22);status.setTag("ambient-status");root.addView(status);
        root.addView(DevUi.text(this,"에뮬레이터 API 적용·재조회 시연입니다. 실제 LED 발광은 검증하지 않았어요. 화면을 나가면 적용을 멈추며 마지막 색은 유지돼요.",20));
        setContentView(root);
    }
    private boolean parked(){
        if(properties==null)return false;
        try{
            var speed=properties.getProperty(Float.class,VehiclePropertyIds.PERF_VEHICLE_SPEED,0);
            var gear=properties.getProperty(Integer.class,VehiclePropertyIds.GEAR_SELECTION,0);
            return speed!=null&&gear!=null&&speed.getStatus()==0&&gear.getStatus()==0
                &&Float.isFinite(speed.getValue())&&Math.abs(speed.getValue())<0.2f&&gear.getValue()==4;
        }catch(Exception error){return false;}
    }
    private <T> T await(CompletableFuture<T> value)throws Exception{return value.get(4,TimeUnit.SECONDS);}
    private kotlin.jvm.functions.Function1<Exception,Unit> fail(CompletableFuture<?> value){return e->{value.completeExceptionally(e);return Unit.INSTANCE;};}
    private int read(Light light,SeatArea area)throws Exception{
        CompletableFuture<Integer> value=new CompletableFuture<>();light.getAmbientLightBrightnessColor(area,n->{value.complete(n);return Unit.INSTANCE;},fail(value));return await(value);
    }
    private void process(int epoch)throws Exception{
        String token=VehicleApi.load(this);if(token==null){show("가족 연결을 먼저 해주세요.");return;}
        JSONObject command=VehicleApi.request("/api/session/ambient","GET",null,token);
        if(!live(epoch))return;
        if(!command.getBoolean("enabled")){show("폰에서 색 조명 연동을 켜고 색을 골라주세요.");return;}
        JSONObject chosen=command.getJSONObject("color");String hex=chosen.getString("hex");int revision=command.getInt("revision");
        main.post(()->{if(live(epoch)){color.setText("선택 색 "+hex);android.graphics.drawable.GradientDrawable chip=new android.graphics.drawable.GradientDrawable();chip.setColor(android.graphics.Color.parseColor(hex));chip.setCornerRadius(DevUi.dp(this,16));chip.setStroke(DevUi.dp(this,1),android.graphics.Color.rgb(217,212,200));swatch.setBackground(chip);}});
        String result="blocked";String actual=null;
        if(parked()){
            try{
                Light light=vehicle.getLight();CompletableFuture<SeatArea> supported=new CompletableFuture<>();
                light.checkAmbientLightBrightnessColorCapabilities((config,areas)->{
                    if(config.getAccessType()==AccessType.READ_WRITE&&!areas.isEmpty())supported.complete(areas.get(0));
                    else supported.completeExceptionally(new UnsupportedOperationException());return Unit.INSTANCE;
                },fail(supported));
                SeatArea area=await(supported);int desired=Integer.parseInt(hex.substring(1),16),before=read(light,area);
                // Revalidate session, latest command and P immediately before touching the API.
                JSONObject latest=VehicleApi.request("/api/session/ambient","GET",null,token);
                if(!live(epoch)||!token.equals(VehicleApi.load(this))||!latest.getBoolean("enabled")||latest.getInt("revision")!=revision)return;
                if(!parked())result="blocked";
                else {
                    if(!live(epoch))return;
                    if(before!=desired){
                        CompletableFuture<Boolean> written=new CompletableFuture<>();
                        light.setAmbientLightBrightnessColor(area,desired,(changed,value)->{written.complete(true);return Unit.INSTANCE;},fail(written));await(written);
                        Thread.sleep(350);
                    }
                    int after=read(light,area);actual=String.format(java.util.Locale.ROOT,"#%06X",after&0xFFFFFF);
                    result=after==desired?"applied":"error";
                    android.util.Log.i("PaletteAmbient","requested="+hex+" readback="+actual+" matched="+result.equals("applied"));
                }
            }catch(Exception error){result=error instanceof UnsupportedOperationException||error.getCause() instanceof UnsupportedOperationException?"unsupported":"error";}
        }
        if(!live(epoch)||!token.equals(VehicleApi.load(this)))return;
        JSONObject ack=new JSONObject().put("revision",revision).put("status",result);if(result.equals("applied"))ack.put("actualHex",actual);
        VehicleApi.request("/api/session/ambient/ack","POST",ack,token);
        show(result.equals("applied")?"같은 색 적용·재조회 확인 · "+actual:result.equals("blocked")?"P·정차 신호 대기 · 조명 적용을 멈췄어요.":result.equals("unsupported")?"이 차량은 조명 색 제어를 지원하지 않아요.":"조명 적용·재조회를 확인하지 못했어요. 콘솔 연결을 확인해주세요.");
    }
    private final Runnable tick=new Runnable(){public void run(){if(!active)return;if(!busy){busy=true;int epoch=generation;io.execute(()->{try{process(epoch);}catch(Exception error){show("서버·가족 연결을 확인해주세요. 조명 적용을 멈췄어요.");}finally{main.post(()->busy=false);}});}main.postDelayed(this,1000);}};
    @Override protected void onStart(){super.onStart();
        if(!BuildConfig.EMULATOR_DEMO||!("ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE))){status.setText("실차 조명 제어는 잠겨 있어요. 에뮬레이터 전용 시연입니다.");return;}
        active=true;generation++;
        if(checkSelfPermission("android.car.permission.CAR_SPEED")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.car.permission.CAR_SPEED"},915);
        try{vehicle=new Vehicle(this);vehicle.initialize();car=Car.createCar(this);properties=(CarPropertyManager)car.getCarManager(Car.PROPERTY_SERVICE);main.postDelayed(tick,1500);}catch(Exception error){show("조명 SDK에 연결하지 못했어요.");}
    }
    @Override protected void onStop(){active=false;generation++;main.removeCallbacks(tick);Vehicle oldVehicle=vehicle;Car oldCar=car;properties=null;
        io.execute(()->{if(oldVehicle!=null)oldVehicle.release();if(oldCar!=null)try{oldCar.disconnect();}catch(IllegalArgumentException ignored){}});super.onStop();}
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
}
