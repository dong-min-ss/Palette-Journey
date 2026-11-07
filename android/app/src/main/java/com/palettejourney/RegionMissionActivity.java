package com.palettejourney;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class RegionMissionActivity extends VehicleActivity {
    private int region = -1;
    private int ageGroup;
    private boolean generated;
    private Button[] regionButtons;
    private Button[] ageButtons;
    private Button generate;
    private TextView guidance;
    private LinearLayout results;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!BuildConfig.DEBUG || !("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE))) {
            finish();
            return;
        }
        if (state != null) {
            region = state.getInt("region", -1);
            ageGroup = state.getInt("ageGroup", 0);
            generated = state.getBoolean("generated");
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = DevUi.dp(this, 20);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(DevUi.BG);
        scroll.addView(root);
        root.addView(DevUi.text(this, "지역 미션 생성", 30));
        root.addView(DevUi.text(this, "지역과 연령대를 골라 가족이 함께 할 그림일기 미션을 받아보세요.", 22));
        root.addView(DevUi.text(this, "에뮬레이터 시연 · 준비된 지역 미션", 20));

        regionButtons = addChoices(DevUi.section(root, "여행 지역"), RegionMissions.REGIONS, this::selectRegion);
        ageButtons = addChoices(DevUi.section(root, "연령대"), RegionMissions.AGE_GROUPS, this::selectAgeGroup);

        guidance = DevUi.text(this, "", 20);
        guidance.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(guidance);
        generate = new Button(this);
        generate.setText("미션 생성");
        generate.setTag("primary-action");
        DevUi.button(generate);
        generate.setOnClickListener(v -> {
            generated = true;
            render();
            scroll.post(() -> scroll.smoothScrollTo(0, results.getTop()));
        });
        root.addView(generate, new LinearLayout.LayoutParams(-1, -2));
        results = DevUi.section(root, "");
        results.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        setContentView(scroll);
        render();
    }

    private Button[] addChoices(LinearLayout parent, String[] labels, java.util.function.IntConsumer select) {
        Button[] buttons = new Button[labels.length];
        LinearLayout row = null;
        for (int i = 0; i < labels.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            int index = i;
            Button button = new Button(this);
            button.setText(labels[i]);
            DevUi.button(button);
            button.setOnClickListener(v -> select.accept(index));
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, -2, 1);
            layout.setMargins(DevUi.dp(this, 4), DevUi.dp(this, 4), DevUi.dp(this, 4), DevUi.dp(this, 4));
            row.addView(button, layout);
            buttons[i] = button;
        }
        return buttons;
    }

    private void selectRegion(int index) {
        if (region == index) return;
        region = index;
        generated = false;
        render();
    }

    private void selectAgeGroup(int index) {
        if (ageGroup == index) return;
        ageGroup = index;
        generated = false;
        render();
    }

    private void render() {
        updateSelection(regionButtons, RegionMissions.REGIONS, region);
        updateSelection(ageButtons, RegionMissions.AGE_GROUPS, ageGroup);
        generate.setEnabled(region >= 0);
        guidance.setText(region < 0 ? "여행 지역을 먼저 선택해 주세요."
                : RegionMissions.REGIONS[region] + " · " + RegionMissions.AGE_GROUPS[ageGroup] + " 미션을 만들 수 있어요.");
        results.removeAllViews();
        results.setVisibility(generated && region >= 0 ? View.VISIBLE : View.GONE);
        if (!generated || region < 0) return;

        TextView heading = DevUi.text(this, RegionMissions.REGIONS[region] + " 그림일기 미션", 24);
        heading.setAccessibilityHeading(true);
        results.addView(heading);
        String[] missions = RegionMissions.forSelection(region, ageGroup);
        for (int i = 0; i < missions.length; i++) {
            results.addView(DevUi.text(this, RegionMissions.CATEGORIES[i], 22));
            results.addView(DevUi.text(this, missions[i], 20));
        }
    }

    private void updateSelection(Button[] buttons, String[] labels, int selected) {
        for (int i = 0; i < buttons.length; i++) {
            boolean isSelected = i == selected;
            buttons[i].setActivated(isSelected);
            buttons[i].setContentDescription(labels[i] + (isSelected ? ", 선택됨" : ""));
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("region", region);
        state.putInt("ageGroup", ageGroup);
        state.putBoolean("generated", generated);
    }
}
