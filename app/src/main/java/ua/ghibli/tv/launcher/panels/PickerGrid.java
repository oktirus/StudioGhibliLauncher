package ua.ghibli.tv.launcher.panels;

import android.widget.GridLayout;

final class PickerGrid {
    private PickerGrid() {}

    static void reflowColumns(GridLayout grid, int columns) {
        if (grid.getColumnCount() == columns) return;
        for (int i = 0; i < grid.getChildCount(); i++) {
            GridLayout.LayoutParams lp = (GridLayout.LayoutParams) grid.getChildAt(i).getLayoutParams();
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED);
            lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED);
            grid.getChildAt(i).setLayoutParams(lp);
        }
        grid.setColumnCount(columns);
    }
}
