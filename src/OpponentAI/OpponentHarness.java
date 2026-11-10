package OpponentAI;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Plug-in point for an outside AI (e.g. a classmate's) to play against the human or our agents.
 * THIS IS THE ONLY FILE THAT SHOULD NEED EDITING to hook up a different AI.
 * .
 * To plug in another AI:
 *  1. Copy its source files into src/OpponentAI/ and add "package OpponentAI;" at the top of each
 *     (or keep its own package and put it in the matching folder under src/, then import it here).
 *  2. Create it in the constructor below.
 *  3. In chooseColumn(), convert the board to whatever format it expects, call it, and return its column.
 *  4. Run the game and pick "Opponent Harness" for Red or Orange in the Game Settings panel.
 * .
 * The game handles everything else: turns, timing, win detection, and logging results.
 * If the opponent throws an exception or returns an invalid column, it forfeits the game.
 */
public class OpponentHarness
{
    // board values passed to chooseColumn()
    public static final int EMPTY    = 0;
    public static final int ME       = 1; // this AI's discs (whichever color it is playing)
    public static final int OTHER    = 2; // the other side's discs

    public static final int CONNECT  = 4; // discs in a row needed to win, on every board size

    private final int _depth;

    // PLACEHOLDER opponent: random legal column. Remove once a real AI is plugged in.
    private final Random _random = new Random();

    /**
     * Creates the opponent. Called once per game.
     * @param depth the search depth set for this side in the Game Settings panel;
     *              pass it on to the plugged-in AI, or ignore it if that AI doesn't take one
     */
    public OpponentHarness(int depth)
    {
        this._depth = depth;

        // TODO (plug-in): create the outside AI here, e.g.
        //   _theirAI = new TheirConnectFourAI(depth);
    }

    /**
     * @return the name shown in the game log, results.csv, and the game over banner
     */
    public String getName()
    {
        // TODO (plug-in): e.g. return "Alex's AI";
        return "Opponent AI";
    }

    /**
     * Chooses the column the opponent drops its disc into.
     * Runs on a background thread, so a slow search won't freeze the window.
     * .
     * board[row][col]: row 0 is the TOP row, gravity pulls toward the last row.
     * Each slot is EMPTY (0), ME (1), or OTHER (2). The array is a copy, so it is safe to modify.
     * Board size can be any M x N set in the Game Settings panel; use board.length and board[0].length.
     *
     * @param board a copy of the current board, from this opponent's point of view
     * @return the column (0 to cols - 1) to place the disc in; must not be full
     */
    public int chooseColumn(int[][] board)
    {
        // TODO (plug-in): convert the board, ask the outside AI, and return its column, e.g.
        //   char[][] theirBoard = ...convert board...;
        //   return _theirAI.getBestMove(theirBoard);

        // PLACEHOLDER: random legal column
        List<Integer> legal = new ArrayList<>();

        for (int col = 0; col < board[0].length; col++)
        {
            if (board[0][col] == EMPTY)
            {
                legal.add(col);
            }
        }

        return legal.get(_random.nextInt(legal.size()));
    }
}
