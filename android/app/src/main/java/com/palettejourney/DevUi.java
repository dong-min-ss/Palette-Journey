package com.palettejourney;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Explicit contrast and dp sizing, independent of automotive theme overlays. */
final class DevUi {
    static final int BG=Color.rgb(11,18,32), PANEL=Color.rgb(24,38,58), TEXT=Color.rgb(241,245,249), MUTED=Color.rgb(176,194,215), ACCENT=Color.rgb(47,109,246);
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable panel(Context c){GradientDrawable d=new GradientDrawable();d.setColor(PANEL);d.setCornerRadius(dp(c,14));d.setStroke(dp(c,1),Color.rgb(53,73,99));return d;}
    static TextView text(Context c,String value,int size){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(TEXT);t.setPadding(0,dp(c,5),0,dp(c,5));return t;}
    static LinearLayout section(LinearLayout parent,String title){
        Context c=parent.getContext();LinearLayout box=new LinearLayout(c);box.setOrientation(1);int pad=dp(c,18);box.setPadding(pad,pad,pad,pad);box.setBackground(panel(c));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(c,14);parent.addView(box,p);
        if(!title.isEmpty()){TextView label=text(c,title,24);label.setTypeface(null,1);box.addView(label);}return box;
    }
    static LinearLayout fold(LinearLayout parent,String title){
        LinearLayout box=section(parent,"");Button toggle=new Button(parent.getContext());toggle.setTag("fold-toggle");toggle.setText(title+" ＋");button(toggle);toggle.setBackgroundTintList(ColorStateList.valueOf(PANEL));box.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout body=new LinearLayout(parent.getContext());body.setOrientation(1);body.setVisibility(View.GONE);box.addView(body);
        toggle.setOnClickListener(v->{boolean open=body.getVisibility()!=View.VISIBLE;body.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(title+(open?" −":" ＋"));});return body;
    }
    static void moveTo(LinearLayout parent,View child){if(child.getParent() instanceof ViewGroup)((ViewGroup)child.getParent()).removeView(child);parent.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    static void button(Button b){b.setAllCaps(false);b.setTextSize(22);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,Color.WHITE}));b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{PANEL,ACCENT}));b.setMinHeight(dp(b.getContext(),60));b.setPadding(dp(b.getContext(),18),dp(b.getContext(),12),dp(b.getContext(),18),dp(b.getContext(),12));}
    static void styleTree(View v){
        if("palette-swatch".equals(v.getTag()))return;
        if(v instanceof Button&&((Button)v).getText().length()>0){button((Button)v);if("fold-toggle".equals(v.getTag()))((Button)v).setBackgroundTintList(ColorStateList.valueOf(PANEL));}
        else if(v instanceof EditText){EditText e=(EditText)v;e.setTextSize(22);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setPadding(dp(v.getContext(),16),dp(v.getContext(),14),dp(v.getContext(),16),dp(v.getContext(),14));e.setBackground(panel(v.getContext()));}
        else if(v instanceof TextView){TextView t=(TextView)v;t.setTextColor(TEXT);float sp=t.getTextSize()/v.getResources().getDisplayMetrics().scaledDensity;if(sp<20)t.setTextSize(20);}
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)styleTree(((ViewGroup)v).getChildAt(i));
        if(v instanceof LinearLayout&&((LinearLayout)v).getOrientation()==LinearLayout.HORIZONTAL){LinearLayout row=(LinearLayout)v;boolean buttons=row.getChildCount()>0;for(int i=0;i<row.getChildCount();i++)buttons=buttons&&row.getChildAt(i) instanceof Button;if(buttons)for(int i=0;i<row.getChildCount();i++){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(v.getContext(),3),0,dp(v.getContext(),3),0);row.getChildAt(i).setLayoutParams(p);}}
    }
    static ArrayAdapter<String> spinner(Context c,String[] names){return new ArrayAdapter<String>(c,android.R.layout.simple_spinner_dropdown_item,names){
        @Override public View getView(int p,View v,ViewGroup parent){View row=super.getView(p,v,parent);styleTree(row);row.setBackgroundColor(PANEL);return row;}
        @Override public View getDropDownView(int p,View v,ViewGroup parent){View row=super.getDropDownView(p,v,parent);styleTree(row);row.setBackgroundColor(PANEL);return row;}
    };}
}
