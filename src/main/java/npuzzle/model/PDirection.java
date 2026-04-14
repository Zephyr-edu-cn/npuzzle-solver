package npuzzle.model;

public enum PDirection {
    UP(-1, 0, '↑'),
    DOWN(1, 0, '↓'),
    LEFT(0, -1, '←'),
    RIGHT(0, 1, '→');

    private final int deltaRow;
    private final int deltaCol;
    private final char symbol;

    PDirection(int deltaRow, int deltaCol, char symbol) {
        this.deltaRow = deltaRow;
        this.deltaCol = deltaCol;
        this.symbol = symbol;
    }

    public int getDeltaRow() {
        return deltaRow;
    }

    public int getDeltaCol() {
        return deltaCol;
    }

    public char getSymbol() {
        return symbol;
    }

    @Override
    public String toString() {
        return name() + "(" + symbol + ")";
    }
}