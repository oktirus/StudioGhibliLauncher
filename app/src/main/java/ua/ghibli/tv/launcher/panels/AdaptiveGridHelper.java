package ua.ghibli.tv.launcher.panels;

public class AdaptiveGridHelper {

    public static class LayoutResult {
        public final int columnCount;
        public final int cardWidthPx;

        public LayoutResult(int columnCount, int cardWidthPx) {
            this.columnCount = columnCount;
            this.cardWidthPx = cardWidthPx;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LayoutResult)) return false;
            LayoutResult that = (LayoutResult) o;
            return columnCount == that.columnCount && cardWidthPx == that.cardWidthPx;
        }

        @Override
        public int hashCode() {
            return 31 * columnCount + cardWidthPx;
        }

        @Override
        public String toString() {
            return "LayoutResult{columnCount=" + columnCount + ", cardWidthPx=" + cardWidthPx + "}";
        }
    }

    public static LayoutResult compute(
            int availableWidthPx,
            int minCardWidthPx,
            int maxCardWidthPx,
            int marginPx,
            int minCols,
            int maxCols
    ) {
        if (minCols < 1) minCols = 1;
        if (maxCols < minCols) maxCols = minCols;
        if (minCardWidthPx < 1) minCardWidthPx = 1;
        if (maxCardWidthPx < minCardWidthPx) maxCardWidthPx = minCardWidthPx;
        if (marginPx < 0) marginPx = 0;

        if (availableWidthPx <= 0) {
            return new LayoutResult(minCols, minCardWidthPx);
        }

        int itemSlotMin = minCardWidthPx + (marginPx * 2);
        int cols = availableWidthPx / Math.max(1, itemSlotMin);
        if (cols < minCols) cols = minCols;
        if (cols > maxCols) cols = maxCols;
        cols = Math.min(cols, Math.max(1, availableWidthPx / Math.max(1, itemSlotMin)));

        int totalMargins = cols * (marginPx * 2);
        int cardWidth = (availableWidthPx - totalMargins) / cols;
        if (cardWidth < minCardWidthPx) {
            cardWidth = Math.max(1, cardWidth);
        } else if (cardWidth > maxCardWidthPx) {
            cardWidth = maxCardWidthPx;
        }

        return new LayoutResult(cols, cardWidth);
    }
}
