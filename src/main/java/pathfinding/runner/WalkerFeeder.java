package pathfinding.runner;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import pathfinding.problem.pathfinding.GridType;
import pathfinding.problem.pathfinding.PathFinding;
import pathfinding.problem.pathfinding.Position;
import pathfinding.queue.ListFrontier;

import java.util.ArrayList;

/**
 * Ѱ·�����EngineFeeder������ΪWalkerFeeder
 * ͬѧ�ǿ��Բο���д�Լ���PuzzleFeeder
 */
public class WalkerFeeder extends EngineFeeder {
    @Override
    public ArrayList<Problem> getProblems(ArrayList<String> problemLines) {
        //�����ģ, ����ĵ�һ��
        int size = Integer.parseInt(problemLines.get(0));
        //�����ͼ��size * size��Grid���ӵ�2�п�ʼ��size�����ݣ�ÿ����size������
        GridType[][] map = getMap(problemLines, size);

        /* ����������� */
        ArrayList<Problem> problems = new ArrayList<>();
        int lineNo = size + 1;
        while (lineNo < problemLines.size()){
            //��������ʵ��
            PathFinding problem = getPathFinding(problemLines.get(lineNo), size);
            //Ϊÿ������ʵ�����õ�ͼ
            problem.setGrids(map);
            //��ӵ������б�
            problems.add(problem);
            lineNo++;
        } //�����������

        return problems;
    }

    /**
     * ����Ѱ·�����һ��ʵ��
     * @param problemLine
     * @param size
     * @return
     */
    private PathFinding getPathFinding(String problemLine, int size) {
        String[] cells = problemLine.split(" ");
        //�����ʼ״̬
        int row = Integer.parseInt(cells[0]);
        int col = Integer.parseInt(cells[1]);
        Position initialState = new Position(row, col);
        //����Ŀ��״̬
        //�����ʼ״̬
        row = Integer.parseInt(cells[2]);
        col = Integer.parseInt(cells[3]);
        Position goal = new Position(row, col);

        //����Ѱ·�����ʵ��
        return new PathFinding(initialState, goal, size);
    }

    /**
     *
     * @param problemLines
     * @param size
     * @return
     */
    private GridType[][] getMap(ArrayList<String> problemLines, int size) {
        GridType[][] map = new GridType[size][];
        for (int i = 0; i < size; i++){
            map[i] = new GridType[size];
            String[] cells = problemLines.get(i + 1).split(" ");
            for (int j = 0; j < size; j++){
                int cellType = Integer.parseInt(cells[j]);
                map[i][j] = GridType.values()[cellType];
            }
        }
        return map;
    }


    @Override
    public Frontier getFrontier(EvaluationType type) {
        return new ListFrontier(Node.evaluator(type));
    }

    /**
     * ��ö�״̬���й�ֵ��Predictor
     *
     * @param type ��ֵ����������
     * @return  ��ֵ����
     */
    @Override
    public Predictor getPredictor(HeuristicType type) {
        return Position.predictor(type);
    }

}
