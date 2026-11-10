/**
 * The AI opponent. Chooses moves using Minimax with Alpha-Beta Pruning.
 * .
 * InputHandler calls chooseColumn() whenever it is the AI's turn.
 */
public class MinimaxAgent
{
    private final int _maxDepth;

    /**
     * Creates the agent
     * @param maxDepth how many plies (single moves) ahead the search is allowed to look
     */
    public MinimaxAgent(int maxDepth)
    {
        if (maxDepth < 1)
        {
            throw new IllegalArgumentException("MinimaxAgent Constructor: maxDepth must be at least 1");
        }

        this._maxDepth = maxDepth;
    }

    /**
     * Chooses the column the AI drops its disc into.
     * .
     * Runs on a background thread so a slow search won't freeze the window.
     * The board passed in is a COPY of the game board, so it is safe to modify while searching
     * (e.g. place a disc, recurse, then remove it).
     * .
     * Board layout: board[row][col], where row 0 is the TOP row and gravity pulls toward the last row.
     * Each slot is null (empty), Disc.PLAYER (the human), or Disc.AI (this agent).
     * .
     * Helpers available in InputHandler:
     *  - InputHandler.isWinningMove(board, row, col): did the disc at (row, col) make four in a row?
     *  - InputHandler.isBoardFull(board): is the game a draw?
     *
     * @param board a copy of the current board state
     * @return the column (0 to cols - 1) to place the disc in; must not be full
     */
    public int chooseColumn(InputHandler.Disc[][] board)
    {
        // TODO: replace with Minimax + Alpha-Beta Pruning, limited to _maxDepth.
        // PLACEHOLDER so the game is playable end-to-end: picks the leftmost column that isn't full.
        for (int col = 0; col < board[0].length; col++)
        {
            if (board[0][col] == null)
            {
                return col;
            }
        }

        throw new IllegalStateException("MinimaxAgent.chooseColumn: no valid columns, board is full");
    }

    /**
     * @return the maximum search depth
     */
    public int getMaxDepth()
    {
        return this._maxDepth;
    }
}
