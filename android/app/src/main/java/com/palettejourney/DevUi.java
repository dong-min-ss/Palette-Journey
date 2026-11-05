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
    static final int BG=Color.rgb(12,25,24), PANEL=Color.rgb(23,43,39), TEXT=Color.rgb(247,247,238), MUTED=Color.rgb(173,194,184), ACCENT=Color.rgb(34,110,91);
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable panel(Context c){GradientDrawable d=new GradientDrawable();d.setColor(PANEL);d.setCornerRadius(dp(c,20));d.setStroke(dp(c,1),Color.rgb(48,76,66));return d;}
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
    static void button(Button b){
        if(b instanceof CompoundButton)((CompoundButton)b).setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Color.rgb(141,221,184),MUTED}));
        b.setAllCaps(false);b.setTextSize(22);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        boolean primary="primary-action".equals(b.getTag());
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,TEXT}));
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        for(int i=0;i<3;i++){GradientDrawable shape=panel(b.getContext());shape.setColor(i==0?BG:i==1?Color.rgb(43,91,74):primary?ACCENT:PANEL);states.addState(i==0?new int[]{-android.R.attr.state_enabled}:i==1?new int[]{android.R.attr.state_pressed}:new int[]{},shape);}
        b.setBackgroundTintList(null);b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x227fdfba),states,null));
        b.setMinHeight(dp(b.getContext(),60));b.setPadding(dp(b.getContext(),20),dp(b.getContext(),14),dp(b.getContext(),20),dp(b.getContext(),14));
        if("destination-action".equals(b.getTag()))b.setMinHeight(dp(b.getContext(),94));
    }
    static void home(LinearLayout root,Button book,Button collection,Button family,Button recordings,Button developer){
        java.util.List<View> rest=new java.util.ArrayList<>();
        for(int i=2;i<root.getChildCount();i++){View v=root.getChildAt(i);if(v!=book&&v!=collection&&v!=family&&v!=recordings&&v!=developer)rest.add(v);}
        root.removeAllViews();int pad=dp(root.getContext(),24);root.setPadding(pad,pad,pad,pad);
        TextView brand=text(root.getContext(),"Palette Journey",30);
        brand.setTypeface(null,Typeface.BOLD);brand.setAccessibilityHeading(true);root.addView(brand);
        TextView subtitle=text(root.getContext(),"가족 그림일기 · 에뮬레이터 시연",20);subtitle.setTag("muted-copy");root.addView(subtitle);
        Button[] buttons={book,family,collection,recordings};
        String[] captions={"저장한 그림과 대화 보기","QR로 휴대폰 연결하기","여행에서 모은 색과 스티커 보기","차량에 저장한 녹음 듣기"};
        int columns=root.getResources().getConfiguration().screenWidthDp>=650?2:1;
        for(int i=0;i<buttons.length;i+=columns){
            LinearLayout row=new LinearLayout(root.getContext());
            root.addView(row,new LinearLayout.LayoutParams(-1,-2));
            for(int j=i;j<i+columns;j++){
                Button destination=buttons[j];
                String title=destination.getText().toString();
                android.text.SpannableString label=new android.text.SpannableString(title+"\n"+captions[j]);
                label.setSpan(new android.text.style.AbsoluteSizeSpan(18,true),title.length()+1,label.length(),0);
                label.setSpan(new android.text.style.StyleSpan(Typeface.NORMAL),title.length()+1,label.length(),0);
                destination.setText(label);destination.setTag("destination-action");
                destination.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
                row.addView(destination);
            }
        }
        LinearLayout conversation=section(root,"목소리로 이야기 남기기");for(View v:rest)conversation.addView(v);
        if(developer!=null){LinearLayout tools=fold(root,"개발 도구");tools.addView(developer);tools.addView(text(root.getContext(),"모의 주행 · 지역 해금 시연",18));}
    }
    static void styleTree(View v){
        if("palette-swatch".equals(v.getTag()))return;
        if(v instanceof Button&&((Button)v).getText().length()>0){button((Button)v);if("fold-toggle".equals(v.getTag()))((Button)v).setBackgroundTintList(ColorStateList.valueOf(PANEL));}
        else if(v instanceof EditText){EditText e=(EditText)v;e.setTextSize(22);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setPadding(dp(v.getContext(),16),dp(v.getContext(),14),dp(v.getContext(),16),dp(v.getContext(),14));e.setBackground(panel(v.getContext()));}
        else if(v instanceof TextView){TextView t=(TextView)v;t.setTextColor("brand-copy".equals(t.getTag())?Color.rgb(141,221,184):"muted-copy".equals(t.getTag())?MUTED:TEXT);float sp=t.getTextSize()/v.getResources().getDisplayMetrics().scaledDensity;if(sp<20)t.setTextSize(20);}
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)styleTree(((ViewGroup)v).getChildAt(i));
        if(v instanceof LinearLayout&&((LinearLayout)v).getOrientation()==LinearLayout.HORIZONTAL){LinearLayout row=(LinearLayout)v;boolean buttons=row.getChildCount()>0;for(int i=0;i<row.getChildCount();i++)buttons=buttons&&row.getChildAt(i) instanceof Button;if(buttons)for(int i=0;i<row.getChildCount();i++){View child=row.getChildAt(i);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(v.getContext(),3),"destination-action".equals(child.getTag())?dp(v.getContext(),8):0,dp(v.getContext(),3),0);child.setLayoutParams(p);}}
    }
    static ArrayAdapter<String> spinner(Context c,String[] names){return new ArrayAdapter<String>(c,android.R.layout.simple_spinner_dropdown_item,names){
        @Override public View getView(int p,View v,ViewGroup parent){View row=super.getView(p,v,parent);styleTree(row);row.setBackgroundColor(PANEL);return row;}
        @Override public View getDropDownView(int p,View v,ViewGroup parent){View row=super.getDropDownView(p,v,parent);styleTree(row);row.setBackgroundColor(PANEL);return row;}
    };}
}
