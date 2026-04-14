package npuzzle.model;

import core.problem.Action;

public class PuzzleAction extends Action {
    private final PDirection direction;

    public static final PuzzleAction UP = new PuzzleAction(PDirection.UP);
    public static final PuzzleAction DOWN = new PuzzleAction(PDirection.DOWN);
    public static final PuzzleAction LEFT = new PuzzleAction(PDirection.LEFT);
    public static final PuzzleAction RIGHT = new PuzzleAction(PDirection.RIGHT);

    public PuzzleAction(PDirection direction) {
        this.direction = direction;
    }

    public PDirection getDirection() {
        return direction;
    }

    public int getDeltaRow() {
        return direction.getDeltaRow();
    }

    public int getDeltaCol() {
        return direction.getDeltaCol();
    }

    @Override
    public void draw() {
        // 在控制台输出动作信息
        System.out.println("移动方向: " + direction.getSymbol());
    }

    @Override
    public int stepCost() {
        // 在拼图问题中，每次移动的成本都是1
        return 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PuzzleAction that = (PuzzleAction) obj;
        return direction == that.direction;
    }

    @Override
    public int hashCode() {
        return direction.hashCode();
    }

    @Override
    public String toString() {
        return direction.toString();
    }
}