package com.palettejourney;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Initial display only. Vehicle state and account data are not connected yet. */
public class MainActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 36, 48, 36);
        root.setBackgroundColor(Color.rgb(247, 246, 242));
        addText(root, "Palette Journey", 36);
        addText(root, "여행의 색을 모으는 팔레트", 24);
        addText(root, "개발 미리보기 · 계정 및 차량 연결 전", 18);
        addText(root, "기본 11색", 24);
        LinearLayout palette = new LinearLayout(this);
        String[] colors = {"#F44336", "#FF9800", "#FFEB3B", "#4CAF50", "#2196F3", "#9C27B0", "#E91E63", "#795548", "#FFFFFF", "#000000", "#9E9E9E"};
        String[] names = {"빨강", "주황", "노랑", "초록", "파랑", "보라", "핑크", "브라운", "화이트", "블랙", "그레이"};
        for (int i = 0; i < colors.length; i++) {
            TextView swatch = new TextView(this);
            swatch.setText(names[i]);
            swatch.setContentDescription(names[i]);
            swatch.setTextColor(i == 9 ? Color.WHITE : Color.BLACK);
            swatch.setBackgroundColor(Color.parseColor(colors[i]));
            swatch.setGravity(android.view.Gravity.CENTER);
            palette.addView(swatch, new LinearLayout.LayoutParams(0, 100, 1));
        }
        root.addView(palette);
        addText(root, "첫 가입 혜택: 컬러티켓 1장 (실제 지급은 서버 연결 후)", 20);
        addText(root, "차량 상태 미확인 · 연결 및 창작 조작 잠김", 20);
        setContentView(root);
    }
    private void addText(LinearLayout root, String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(35, 44, 51));
        view.setPadding(0, 8, 0, 16);
        root.addView(view);
    }
}
