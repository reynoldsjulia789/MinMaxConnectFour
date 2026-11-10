/**
 * Julia's Connect Four agent. Chooses moves using Minimax with Alpha-Beta Pruning.
 * .
 * The game calls chooseColumn() whenever it is this agent's turn.
 * See the Agent interface for the board layout and available helpers.
 */
public class JuliaAgent implements Agent
{
    private final int _maxDepth;

    /**
     * Creates the agent
     * @param maxDepth how many plies (single moves by either side) ahead the search is allowed to look
     */
    public JuliaAgent(int maxDepth)
    {
        if (maxDepth < 1)
        {
            throw new IllegalArgumentException("JuliaAgent Constructor: maxDepth must be at least 1");
        }

        this._maxDepth = maxDepth;
    }

    @Override
    public String getName()
    {
        return "Julia's Agent";
    }

    /**
     * Chooses the column to drop a disc into.
     * @param board a copy of the current board; board[row][col], row 0 is the top, null = empty
     * @param me the color this agent is playing (the maximizing player); me.opponent() is the other side
     * @return the column (0 to cols - 1) to place the disc in; must not be full
     */
    @Override
    public int chooseColumn(InputHandler.Disc[][] board, InputHandler.Disc me)
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

        throw new IllegalStateException("JuliaAgent.chooseColumn: no valid columns, board is full");
    }

    /**
     * @return the maximum search depth
     */
    public int getMaxDepth()
    {
        return this._maxDepth;
    }
}
