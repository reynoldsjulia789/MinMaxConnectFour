/**
 * Akiva's Connect Four agent. Chooses moves using Minimax with Alpha-Beta Pruning.
 * .
 * The game calls chooseColumn() whenever it is this agent's turn.
 * See the Agent interface for the board layout and available helpers.
 */
public class AkivaAgent implements Agent
{
    private final int _maxDepth;

    /**
     * Creates the agent
     * @param maxDepth how many plies (single moves by either side) ahead the search is allowed to look
     */
    public AkivaAgent(int maxDepth)
    {
        if (maxDepth < 1)
        {
            throw new IllegalArgumentException("AkivaAgent Constructor: maxDepth must be at least 1");
        }

        this._maxDepth = maxDepth;
    }

    /**
     * @return the name shown in the game log, results.csv, and the game over banner
     */
    @Override
    public String getName()
    {
        return "Akiva's Agent";
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
        int bestCol = -1;
        int bestScore = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE, beta = Integer.MAX_VALUE;

        for (int col : columnOrder(board[0].length))
        {
            int row = dropDisc(board, col, me);
            if (row == -1) continue;

            int score;
            if (InputHandler.isWinningMove(board, row, col))
                score = WIN_SCORE + _maxDepth;
            else if (InputHandler.isBoardFull(board))
                score = 0;
            else if (_maxDepth == 1)
                score = evaluate(board, me);
            else
                score = minimax(board, _maxDepth - 1, alpha, beta, false, me);

            board[row][col] = null;

            if (bestCol == -1 || score > bestScore)  // bestCol == -1 guarantees a legal move even if every move loses
            {
                bestScore = score;
                bestCol = col;
            }
            alpha = Math.max(alpha, bestScore);
        }

        if (bestCol == -1)
            throw new IllegalStateException("AkivaAgent.chooseColumn: no valid columns, board is full");
        return bestCol;
    }


    /**
     * @return the maximum search depth
     */
    public int getMaxDepth()
    {
        return this._maxDepth;
    }

    /** ===== PRIVATE HELPERS ===== */

    /**
     * Evaluation Heuristic for a board position.
     * checks the 4-cell windows as explained in class
     * @param board
     * @param me
     * @return
     */
    private static int evaluate(InputHandler.Disc[][] board, InputHandler.Disc me)
    {
        int rows = board.length, cols = board[0].length, score = 0;

        // center column preference
        int center = cols / 2;
        for (int r = 0; r < rows; r++)
            if (board[r][center] == me) score += 3;

        // {dRow, dCol}: horizontal, vertical, diagonal down-right, diagonal down-left
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                for (int[] d : dirs)
                {
                    int endR = r + 3 * d[0], endC = c + 3 * d[1];
                    if (endR < 0 || endR >= rows || endC < 0 || endC >= cols) continue;
                    score += scoreWindow(board, r, c, d[0], d[1], me);
                }
        return score;
    }

    private static int scoreWindow(InputHandler.Disc[][] board, int r, int c, int dr, int dc, InputHandler.Disc me)
    {
        int mine = 0, theirs = 0;
        for (int i = 0; i < 4; i++)
        {
            InputHandler.Disc cell = board[r + i * dr][c + i * dc];
            if (cell == me) mine++;
            else if (cell != null) theirs++;
        }
        if (mine > 0 && theirs > 0) return 0;  // blocked window
        if (mine == 3) return 5;
        if (mine == 2) return 2;
        if (theirs == 3) return -4;
        return 0;
    }

    private static final int WIN_SCORE = 1_000_000;

    /**
     * The core of the minimax algorithm with alpha-beta pruning.
     * @param board
     * @param depth
     * @param alpha
     * @param beta
     * @param maximizing
     * @param me
     * @return
     */
    private int minimax(InputHandler.Disc[][] board, int depth, int alpha, int beta,
                        boolean maximizing, InputHandler.Disc me)
    {
        InputHandler.Disc mover = maximizing ? me : me.opponent();
        int best = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (int col : columnOrder(board[0].length))
        {
            int row = dropDisc(board, col, mover);
            if (row == -1) continue;               // column full, skip

            int score;
            if (InputHandler.isWinningMove(board, row, col))
                score = maximizing ? WIN_SCORE + depth : -(WIN_SCORE + depth);
            else if (InputHandler.isBoardFull(board))
                score = 0;                          // draw
            else if (depth == 1)
                score = evaluate(board, me);        // depth limit reached: use the heuristic
            else
                score = minimax(board, depth - 1, alpha, beta, !maximizing, me);

            board[row][col] = null;                 // undo

            if (maximizing) { best = Math.max(best, score); alpha = Math.max(alpha, best); }
            else            { best = Math.min(best, score); beta  = Math.min(beta,  best); }

            if (alpha >= beta) break;               // prune
        }
        return best;
    }

    /** Drops a disc into column and returns the row it landed in, or -1 if the column is full. */
    private static int dropDisc(InputHandler.Disc[][] board, int col, InputHandler.Disc disc)
    {
        for (int row = board.length - 1; row >= 0; row--)
        {
            if (board[row][col] == null)
            {
                board[row][col] = disc;
                return row;
            }
        }
        return -1;
    }

    /** Produces column indices ordered center-first
     * since center moves tend to be stronger in connect four
     * and this board can be dynamically sized
     * e.g. 7 cols -> 3,2,4,1,5,0,6 */
    private static int[] columnOrder(int cols)
    {
        int[] order = new int[cols];
        int center = cols / 2;
        for (int i = 0; i < cols; i++)
        {
            // alternates: center, center-1, center+1, center-2, ...
            order[i] = (i % 2 == 0) ? center + i / 2 : center - (i + 1) / 2;
        }
        return order;
    }


}
