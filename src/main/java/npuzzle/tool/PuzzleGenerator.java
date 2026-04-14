package npuzzle.tool;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Utility script that generates solvable sliding puzzles and appends them to a
 * text file under the {@code resources} directory.
 *
 * <p>Usage examples:</p>
 * <pre>
 *   java stud.g09.tool.PuzzleGenerator
 *   java stud.g09.tool.PuzzleGenerator 3 5
 *   java stud.g09.tool.PuzzleGenerator 4 2 50 42 custom.txt
 * </pre>
 *
 * <p>Arguments (optional, in order):</p>
 * <ul>
 *   <li>size   - board dimension, default 4</li>
 *   <li>count  - number of boards to create, default 1</li>
 *   <li>steps  - scramble steps from the goal state, default {@code size * size}</li>
 *   <li>seed   - random seed (long), default random</li>
 *   <li>output - file name under {@code resources}, default generated_puzzles.txt</li>
 * </ul>
 */
public final class PuzzleGenerator {

    private static final String DEFAULT_OUTPUT = "generated_puzzles.txt";

    private PuzzleGenerator() {
        throw new AssertionError("Utility class");
    }

    public static void main(String[] args) {
        Config config = parseArgs(args);
        System.out.println("Configuration: size=" + config.size
                + ", count=" + config.count
                + ", steps=" + config.scrambleSteps
                + ", output=" + config.outputFile
                + (config.seed != null ? ", seed=" + config.seed : ""));

        try (BufferedWriter writer = openWriter(config.outputFile)) {
            int[] goal = buildGoalBoard(config.size);
            for (int i = 0; i < config.count; i++) {
                int[] board = generateBoard(config.size, config.scrambleSteps, config.random);
                writer.write(formatLine(config.size, board, goal));
                writer.newLine();

                System.out.println("Puzzle #" + (i + 1) + " (0 denotes blank):");
                printBoard(board, config.size);
                System.out.println("Linear form: " + Arrays.toString(board));
            }
            System.out.println("All puzzles appended to file: " + config.outputFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + config.outputFile, e);
        }
    }

    private static Config parseArgs(String[] args) {
        Config config = new Config();
        config.size = args.length > 0 ? parsePositiveInt(args[0], "size", 2) : 4;
        config.count = args.length > 1 ? parsePositiveInt(args[1], "count", 1) : 1;
        config.scrambleSteps = args.length > 2 ? parseNonNegativeInt(args[2], "steps") : config.size * config.size;

        if (args.length > 3 && !args[3].isBlank()) {
            try {
                config.seed = Long.parseLong(args[3]);
                config.random = new Random(config.seed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("seed must be a long integer", e);
            }
        } else {
            config.random = new Random();
        }

        String fileName = args.length > 4 && !args[4].isBlank() ? args[4] : DEFAULT_OUTPUT;
        config.outputFile = Paths.get("resources", fileName).toString();
        return config;
    }

    private static int parsePositiveInt(String raw, String label, int min) {
        try {
            int value = Integer.parseInt(raw);
            if (value < min) {
                throw new IllegalArgumentException(label + " must be >= " + min);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be an integer", e);
        }
    }

    private static int parseNonNegativeInt(String raw, String label) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                throw new IllegalArgumentException(label + " must be >= 0");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be an integer", e);
        }
    }

    private static BufferedWriter openWriter(String outputPath) throws IOException {
        Path path = Paths.get(outputPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        return Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private static int[] generateBoard(int size, int scrambleSteps, Random random) {
        int[] board = buildGoalBoard(size);
        if (scrambleSteps == 0) {
            return board.clone();
        }
        int zeroPos = board.length - 1;
        Move lastMove = null;

        for (int i = 0; i < scrambleSteps; i++) {
            List<Move> moves = validMoves(zeroPos, size, lastMove);
            if (moves.isEmpty()) {
                moves = validMoves(zeroPos, size, null);
            }
            Move move = moves.get(random.nextInt(moves.size()));
            zeroPos = applyMove(board, zeroPos, size, move);
            lastMove = move;
        }

        return board.clone();
    }

    private static List<Move> validMoves(int zeroPos, int size, Move lastMove) {
        int row = zeroPos / size;
        int col = zeroPos % size;
        List<Move> moves = new ArrayList<>(4);
        for (Move move : Move.values()) {
            if (lastMove != null && move == lastMove.opposite) {
                continue;
            }
            int nextRow = row + move.deltaRow;
            int nextCol = col + move.deltaCol;
            if (nextRow >= 0 && nextRow < size && nextCol >= 0 && nextCol < size) {
                moves.add(move);
            }
        }
        return moves;
    }

    private static int applyMove(int[] board, int zeroPos, int size, Move move) {
        int row = zeroPos / size;
        int col = zeroPos % size;
        int targetRow = row + move.deltaRow;
        int targetCol = col + move.deltaCol;
        int targetPos = targetRow * size + targetCol;
        board[zeroPos] = board[targetPos];
        board[targetPos] = 0;
        return targetPos;
    }

    private static void printBoard(int[] board, int size) {
        for (int row = 0; row < size; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < size; col++) {
                int value = board[row * size + col];
                if (value == 0) {
                    line.append("   ");
                } else {
                    line.append(String.format("%2d ", value));
                }
            }
            System.out.println(line.toString().stripTrailing());
        }
    }

    private static int[] buildGoalBoard(int size) {
        int total = size * size;
        int[] goal = new int[total];
        for (int i = 0; i < total - 1; i++) {
            goal[i] = i + 1;
        }
        goal[total - 1] = 0;
        return goal;
    }

    private static String formatLine(int size, int[] board, int[] goal) {
        StringBuilder builder = new StringBuilder();
        builder.append(size);
        builder.append(' ');
        appendArray(builder, board);
        builder.append(' ');
        appendArray(builder, goal);
        return builder.toString().trim();
    }

    private static void appendArray(StringBuilder builder, int[] array) {
        for (int i = 0; i < array.length; i++) {
            builder.append(array[i]);
            if (i < array.length - 1) {
                builder.append(' ');
            }
        }
    }

    private enum Move {
        UP(-1, 0),
        DOWN(1, 0),
        LEFT(0, -1),
        RIGHT(0, 1);

        final int deltaRow;
        final int deltaCol;
        Move opposite;

        static {
            UP.opposite = DOWN;
            DOWN.opposite = UP;
            LEFT.opposite = RIGHT;
            RIGHT.opposite = LEFT;
        }

        Move(int deltaRow, int deltaCol) {
            this.deltaRow = deltaRow;
            this.deltaCol = deltaCol;
        }
    }

    private static final class Config {
        int size;
        int count;
        int scrambleSteps;
        Long seed;
        Random random;
        String outputFile;
    }
}

