package com.vicky.personalai;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class FixedMainActivity extends MainActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        forceTeamPanelCollapsed();
    }

    private void forceTeamPanelCollapsed() {
        try {
            Field expanded = MainActivity.class.getDeclaredField("teamPanelExpanded");
            expanded.setAccessible(true);
            expanded.setBoolean(this, false);

            Field rowsField = MainActivity.class.getDeclaredField("teamRows");
            rowsField.setAccessible(true);
            TextView[] rows = (TextView[]) rowsField.get(this);
            if (rows != null) {
                for (TextView row : rows) if (row != null) row.setVisibility(View.GONE);
            }

            Method refresh = MainActivity.class.getDeclaredMethod("refreshTeamUi");
            refresh.setAccessible(true);
            refresh.invoke(this);
        } catch (Exception ignored) {
        }
    }
}
