/**
 * Anything that can play Connect Four on its own: our Minimax agents, or an outside AI via OpponentHarness.
 * .
 * To add a new agent:
 *  1. Write a class that implements this interface, with a constructor that takes the search depth.
 *  2. Add one line for it in AgentRegistry. It then appears in the Game Settings dropdowns.
 */
public interface Agent
{
    /**
     * @return the name shown in the game log, results.csv, and the game over banner
     */
    String getName();

    /**
     * Chooses the column to drop a disc into.
     * .
     * Runs on a background thread, so a slow search won't freeze the window.
     * The board is a COPY of the game board, so it is safe to modify while searching
     * (e.g. place a disc, recurse, then remove it).
     * .
     * Board layout: board[row][col], where row 0 is the TOP row and gravity pulls toward the last row.
     * Each slot is null (empty), Disc.RED, or Disc.YELLOW.
     * Use board.length and board[0].length for the size.
     * .
     * Helpers:
     *  - me.opponent(): the other side's color
     *  - InputHandler.isWinningMove(board, row, col): did the disc at (row, col) make four in a row?
     *  - InputHandler.isBoardFull(board): is the game a draw?
     *  - InputHandler.copyGrid(board): deep copy of a board
     *
     * @param board a copy of the current board state
     * @param me the color this agent is playing this game (the maximizing player)
     * @return the column (0 to cols - 1) to place the disc in; must not be full, or the agent forfeits
     */
    int chooseColumn(InputHandler.Disc[][] board, InputHandler.Disc me);
}
