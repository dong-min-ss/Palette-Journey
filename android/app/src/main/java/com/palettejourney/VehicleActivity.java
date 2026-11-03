package com.palettejourney;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

/** Keeps navigation visible outside each screen's scrolling/working content. */
public abstract class VehicleActivity extends Activity {
    @Override public void setContentView(View content) {
        LinearLayout frame=new LinearLayout(this);frame.setOrientation(LinearLayout.VERTICAL);frame.setBackgroundColor(DevUi.BG);
        LinearLayout navigation=new LinearLayout(this);int pad=DevUi.dp(this,16);navigation.setPadding(pad,DevUi.dp(this,8),pad,0);
        Button back=new Button(this);back.setText("‹ 뒤로가기");back.setContentDescription("뒤로가기");back.setTag("vehicle-back");DevUi.button(back);
        back.setOnClickListener(v->onBackPressed());navigation.addView(back,new LinearLayout.LayoutParams(-2,-2));
        frame.addView(navigation,new LinearLayout.LayoutParams(-1,-2));frame.addView(content,new LinearLayout.LayoutParams(-1,0,1));super.setContentView(frame);
    }
}
