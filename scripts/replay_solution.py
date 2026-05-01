import argparse
from pathlib import Path


DIRECTIONS = {
    "UP": (-1, 0),
    "DOWN": (1, 0),
    "LEFT": (0, -1),
    "RIGHT": (0, 1),
}


def read_text_fallback(path: str) -> str:
    data = Path(path).read_bytes()
    for encoding in ("utf-8", "gbk", "gb2312"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    raise UnicodeDecodeError("unknown", data, 0, len(data), "Cannot decode file.")


def parse_problem(path: str):
    text = read_text_fallback(path)
    nums = [int(x) for x in text.replace(",", " ").split()]

    if not nums:
        raise ValueError("Empty problem file.")

    size = nums[0]
    board_len = size * size
    expected_len = 1 + board_len * 2

    if len(nums) != expected_len:
        raise ValueError(
            f"Invalid problem format: expected {expected_len} integers "
            f"(size + initial + goal), got {len(nums)}."
        )

    initial = nums[1:1 + board_len]
    goal = nums[1 + board_len:1 + board_len * 2]

    validate_board(initial, size, "initial")
    validate_board(goal, size, "goal")

    return size, initial, goal


def parse_actions(path: str):
    text = read_text_fallback(path)
    tokens = text.replace(",", " ").split()
    actions = [token.strip().upper() for token in tokens if token.strip()]

    for i, action in enumerate(actions):
        if action not in DIRECTIONS:
            raise ValueError(f"Unsupported action at index {i}: {action}")

    return actions


def validate_board(board, size, name):
    expected = list(range(size * size))
    if sorted(board) != expected:
        raise ValueError(
            f"Invalid {name} board: expected tile set {expected}, got {sorted(board)}."
        )


def apply_action(board, size, action):
    zero = board.index(0)
    row, col = divmod(zero, size)
    dr, dc = DIRECTIONS[action]
    nr, nc = row + dr, col + dc

    if not (0 <= nr < size and 0 <= nc < size):
        raise ValueError(
            f"Illegal action {action}: blank at ({row}, {col}) cannot move to ({nr}, {nc})."
        )

    target = nr * size + nc
    next_board = board[:]
    next_board[zero], next_board[target] = next_board[target], next_board[zero]
    return next_board


def format_board(board, size):
    lines = []
    for r in range(size):
        row = board[r * size:(r + 1) * size]
        lines.append(" ".join(f"{x:2d}" for x in row))
    return "\n".join(lines)


def write_trace(path: str, states, size):
    output_path = Path(path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with output_path.open("w", encoding="utf-8") as f:
        for step, board in enumerate(states):
            f.write(f"step={step}\n")
            f.write(format_board(board, size))
            f.write("\n\n")


def main():
    parser = argparse.ArgumentParser(
        description="Replay and validate N-Puzzle solutionAnimation.txt."
    )
    parser.add_argument(
        "--problem",
        default="bin/problem.txt",
        help="Path to problem.txt exported by the Java solver."
    )
    parser.add_argument(
        "--actions",
        default="bin/solutionAnimation.txt",
        help="Path to solutionAnimation.txt exported by the Java solver."
    )
    parser.add_argument(
        "--write-trace",
        default=None,
        help="Optional path to write replayed board states."
    )

    args = parser.parse_args()

    size, board, goal = parse_problem(args.problem)
    actions = parse_actions(args.actions)

    states = [board[:]]
    current = board[:]

    for step, action in enumerate(actions, start=1):
        current = apply_action(current, size, action)
        states.append(current)

    if current != goal:
        raise AssertionError(
            "Replay finished, but final board does not equal the goal state.\n\n"
            f"Final board:\n{format_board(current, size)}\n\n"
            f"Goal board:\n{format_board(goal, size)}"
        )

    if args.write_trace:
        write_trace(args.write_trace, states, size)

    print(
        f"PASS: {len(actions)} actions replayed; "
        f"final board matches the goal state."
    )


if __name__ == "__main__":
    main()