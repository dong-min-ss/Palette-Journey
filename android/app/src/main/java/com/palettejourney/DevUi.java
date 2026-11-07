package com.palettejourney;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Explicit contrast and dp sizing, independent of automotive theme overlays. */
final class DevUi {
    static final int BG=0xfff6f5ef, PANEL=0xfffffdf6, TEXT=0xff332d25, MUTED=0xff6b6257, ACCENT=0xff765034;
    private static final int LINE=0xffe2d9c9, SAND=0xfff1e5d3, PRESSED=0xffe7d4bb;
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable panel(Context c){return surface(c,PANEL,LINE);}
    private static GradientDrawable surface(Context c,int fill,int stroke){
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(fill);shape.setCornerRadius(dp(c,20));shape.setStroke(dp(c,1),stroke);
        return shape;
    }
    static TextView text(Context c,String value,int size){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(TEXT);t.setPadding(0,dp(c,5),0,dp(c,5));return t;}
    static LinearLayout section(LinearLayout parent,String title){
        Context c=parent.getContext();LinearLayout box=new LinearLayout(c);box.setOrientation(1);int pad=dp(c,18);box.setPadding(pad,pad,pad,pad);box.setBackground(panel(c));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(c,14);parent.addView(box,p);
        if(!title.isEmpty()){TextView label=text(c,title,24);label.setTypeface(null,1);box.addView(label);}return box;
    }
    static LinearLayout fold(LinearLayout parent,String title){
        Context c=parent.getContext();
        LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams frame=new LinearLayout.LayoutParams(-1,-2);frame.topMargin=dp(c,8);parent.addView(box,frame);
        Button toggle=new Button(c);toggle.setText(title+" ＋");button(toggle);
        toggle.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
        box.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout body=new LinearLayout(c);body.setOrientation(1);body.setVisibility(View.GONE);
        body.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8));box.addView(body,new LinearLayout.LayoutParams(-1,-2));
        toggle.setOnClickListener(v->{boolean open=body.getVisibility()!=View.VISIBLE;body.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(title+(open?" −":" ＋"));});return body;
    }
    static void moveTo(LinearLayout parent,View child){if(child.getParent() instanceof ViewGroup)((ViewGroup)child.getParent()).removeView(child);parent.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    static void button(Button b){
        if(b instanceof CompoundButton)((CompoundButton)b).setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,MUTED}));
        b.setAllCaps(false);b.setTextSize(22);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        boolean primary="primary-action".equals(b.getTag());
        Context c=b.getContext();
        int[] disabled={-android.R.attr.state_enabled};
        int[] activated={android.R.attr.state_activated};
        b.setTextColor(new ColorStateList(new int[][]{disabled,activated,new int[]{}},new int[]{MUTED,PANEL,primary?PANEL:TEXT}));
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(disabled,surface(c,BG,LINE));
        states.addState(new int[]{android.R.attr.state_focused,android.R.attr.state_activated},surface(c,0xff634129,ACCENT));
        states.addState(new int[]{android.R.attr.state_focused},surface(c,primary?0xff634129:SAND,ACCENT));
        states.addState(activated,surface(c,ACCENT,ACCENT));
        states.addState(new int[]{android.R.attr.state_pressed},surface(c,primary?0xff634129:PRESSED,ACCENT));
        states.addState(new int[]{},surface(c,primary?ACCENT:PANEL,primary?ACCENT:LINE));
        b.setStateListAnimator(null);b.setBackgroundTintList(null);b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x26765034),states,null));
        b.setMinHeight(dp(b.getContext(),60));b.setPadding(dp(b.getContext(),20),dp(b.getContext(),14),dp(b.getContext(),20),dp(b.getContext(),14));
        if("destination-action".equals(b.getTag()))b.setMinHeight(dp(b.getContext(),94));
    }
    static void home(LinearLayout root,Button book,Button collection,Button family,Button recordings,Button developer,Button missions){
        java.util.List<View> rest=new java.util.ArrayList<>();
        for(int i=2;i<root.getChildCount();i++){View v=root.getChildAt(i);if(v!=book&&v!=collection&&v!=family&&v!=recordings&&v!=developer)rest.add(v);}
        root.removeAllViews();int pad=dp(root.getContext(),24);root.setPadding(pad,pad,pad,pad);
        Context c=root.getContext();
        LinearLayout masthead=new LinearLayout(c);masthead.setGravity(android.view.Gravity.CENTER_VERTICAL);
        masthead.setPadding(dp(c,12),dp(c,8),dp(c,20),dp(c,8));masthead.setBackground(surface(c,SAND,LINE));
        ImageView mark=new ImageView(c);mark.setImageResource(R.drawable.ic_launcher_foreground);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        masthead.addView(mark,new LinearLayout.LayoutParams(dp(c,64),dp(c,64)));
        LinearLayout heading=new LinearLayout(c);heading.setOrientation(LinearLayout.VERTICAL);
        TextView brand=text(c,"Palette Journey",30);
        brand.setTypeface(null,Typeface.BOLD);brand.setAccessibilityHeading(true);heading.addView(brand);
        TextView subtitle=text(c,"우리 가족의 여행 그림일기 · 에뮬레이터 시연",20);
        subtitle.setTag("muted-copy");heading.addView(subtitle);
        masthead.addView(heading,new LinearLayout.LayoutParams(0,-2,1));root.addView(masthead);
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
                label.setSpan(new android.text.style.TypefaceSpan(Typeface.DEFAULT),title.length()+1,label.length(),0);
                destination.setText(label);destination.setTag("destination-action");
                destination.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
                row.addView(destination);
            }
        }
        LinearLayout conversation=section(root,"목소리로 이야기 남기기");for(View v:rest)conversation.addView(v);
        if (developer != null) {
            LinearLayout tools = fold(root, "개발 도구");
            tools.addView(developer);
            tools.addView(missions);
        }
    }
    static void styleTree(View v){
        if("palette-swatch".equals(v.getTag()))return;
        if(v instanceof Button&&((Button)v).getText().length()>0)button((Button)v);
        else if(v instanceof EditText){EditText e=(EditText)v;e.setTextSize(22);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setPadding(dp(v.getContext(),16),dp(v.getContext(),14),dp(v.getContext(),16),dp(v.getContext(),14));e.setBackground(panel(v.getContext()));}
        else if(v instanceof TextView){TextView t=(TextView)v;t.setTextColor("muted-copy".equals(t.getTag())?MUTED:TEXT);float sp=t.getTextSize()/v.getResources().getDisplayMetrics().scaledDensity;if(sp<20)t.setTextSize(20);}
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)styleTree(((ViewGroup)v).getChildAt(i));
        if(v instanceof LinearLayout&&((LinearLayout)v).getOrientation()==LinearLayout.HORIZONTAL){LinearLayout row=(LinearLayout)v;boolean buttons=row.getChildCount()>0;for(int i=0;i<row.getChildCount();i++)buttons=buttons&&row.getChildAt(i) instanceof Button;if(buttons)for(int i=0;i<row.getChildCount();i++){View child=row.getChildAt(i);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(v.getContext(),3),"destination-action".equals(child.getTag())?dp(v.getContext(),8):0,dp(v.getContext(),3),0);child.setLayoutParams(p);}}
    }
    static ArrayAdapter<String> spinner(Context c,String[] names){return new ArrayAdapter<String>(c,android.R.layout.simple_spinner_dropdown_item,names){
        @Override public View getView(int p,View v,ViewGroup parent){View row=super.getView(p,v,parent);styleTree(row);row.setBackgroundColor(PANEL);return row;}
        @Override public View getDropDownView(int p,View v,ViewGroup parent){View row=super.getDropDownView(p,v,parent);styleTree(row);row.setBackgroundColor(PANEL);return row;}
    };}
}
