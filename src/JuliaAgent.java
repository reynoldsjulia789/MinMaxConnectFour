/**
 * Julia's Connect Four agent. Chooses moves using Minimax with Alpha-Beta Pruning.
 * .
 * The game calls chooseColumn() whenever it is this agent's turn.
 * See the Agent interface for the board layout and available helpers.
 */
public class JuliaAgent implements Agent
{
    private static final int WIN_VAL = 100000;

    private final int _maxDepth;

    /**
     * Creates the agent
     * @param maxDepth how many plies (single moves by either side) ahead the search is allowed to look
     */
    public JuliaAgent(int maxDepth)
    {
        if (maxDepth < 1)
        {
            throw new IllegalArgumentException("JuliaAgent.Constructor: maxDepth must be at least 1");
        }

        this._maxDepth = maxDepth;
    }

    /**
     * @return the name shown in the game log, results.csv, and the game over banner
     */
    @Override
    public String getName()
    {
        return "Julia's Agent";
    }

    /**
     * @return the maximum search depth
     */
    public int getMaxDepth()
    {
        return this._maxDepth;
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
        int   numCols   = board[0].length;
        int[] colOrder  = generateColumnOrder(numCols);

        int bestColumn  = -1;
        int maxScore    = Integer.MIN_VALUE;

        int alpha       = Integer.MIN_VALUE;
        int beta        = Integer.MAX_VALUE;

        for (int col : colOrder)
        {
            if (isLegalMove(board, col))
            {
                int row = dropDisc(board, col, me);

                // If a move instantly wins, take it
                if (InputHandler.isWinningMove(board, row, col))
                {
                    undoMove(board, row, col);
                    return col;
                }

                // recursively explore
                int score = minimaxSearch(board, this._maxDepth - 1, alpha, beta, false, 1, me, row, col);

                // backtrack
                undoMove(board, row, col);

                if (score > maxScore)
                {
                    maxScore = score;
                    bestColumn    = col;
                }

                alpha = Math.max(alpha, score);
            }
        }

        // if no col is chosen as best option, determine if there are any legal moves
        if (bestColumn == -1)
        {
            for (int col : colOrder)
            {
                if (isLegalMove(board, col)) return col;
            }

            throw new IllegalStateException("JuliaAgent.chooseColumn: board is full");
        }

        return bestColumn;
    }

    /**
     * Uses minimax with alpha beta pruning to determine the best placement option for a disc.
     * @param board the current state of the board
     * @param depth the depth to search
     * @param alpha the best value maximizer can guarantee so far
     * @param beta the best value minimizer can guarantee so far
     * @param isMaximizing true if maximizing, false otherwise
     * @param ply the number of individual turns (plies) i.e. 2 is me & opponent have each taken a turn
     * @param me my disc
     * @param lastRow last row disc was placed in
     * @param lastCol last col disc was placed in
     * @return score
     */
    private int minimaxSearch(
            InputHandler.Disc[][] board,
            int depth,
            int alpha,                      // my guaranteed min score
            int beta,                       // opponent's guaranteed max score
            boolean isMaximizing,
            int ply,
            InputHandler.Disc me,
            int lastRow,
            int lastCol)
    {
        // base cases: check for win conditions/full board
        if (isMaximizing && InputHandler.isWinningMove(board, lastRow, lastCol)) return -WIN_VAL + ply; // Opponent won on previous turn
        if (!isMaximizing && InputHandler.isWinningMove(board, lastRow, lastCol)) return WIN_VAL - ply;  // Agent (me) won on previous turn
        if (InputHandler.isBoardFull(board)) return 0;

        // base case: search depth reached
        if (depth == 0) return evaluateBoard(board, me);

        int   numCols     = board[0].length;
        int[] columnOrder = generateColumnOrder(numCols);

        InputHandler.Disc opponent = me.opponent();

        if (isMaximizing)
        {
            int maxScore = Integer.MIN_VALUE;
            for (int col : columnOrder)
            {
                if (isLegalMove(board, col))
                {
                    int row   = dropDisc(board, col, me);
                    int score = minimaxSearch(board, depth - 1,
                            alpha, beta, false, ply + 1, me, row, col);

                    undoMove(board, row, col);

                    maxScore = Math.max(maxScore, score);
                    alpha    = Math.max(alpha, score);

                    if (beta <= alpha) break; // prune branch
                }
            }

            return maxScore;
        }
        else
        {
            int minScore = Integer.MAX_VALUE;
            for (int col : columnOrder)
            {
                if (isLegalMove(board, col))
                {
                    int row   = dropDisc(board, col, opponent);
                    int score = minimaxSearch(board, depth - 1,
                            alpha, beta, true, ply + 1, me, row, col);

                    undoMove(board, row, col);

                    minScore = Math.min(minScore, score);
                    beta     = Math.min(beta, score);

                    if (beta <= alpha) break; // prune branch
                }
            }

            return minScore;
        }
    }

    /**
     * Determines if the specified column contains at least one legal move
     * @param board the current board state
     * @param col the col idx to check
     * @return true if there is >= 1 legal move, false if the col is full
     */
    private boolean isLegalMove(InputHandler.Disc[][] board, int col)
    {
        // because of gravity if top row is empty, there is at least one legal move in the column
        return board[0][col] == null;
    }

    /**
     * Drops a disc in the board at the specified column
     * @param board the current board state
     * @param col the col to drop the disc in
     * @param disc the disc to drop
     * @return the row the disk landed, or -1 if there was no space in the col
     */
    private int dropDisc(InputHandler.Disc[][] board, int col, InputHandler.Disc disc)
    {
        if (!isLegalMove(board, col)) return -1;

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

    /**
     * Undoes a move
     * @param board the current board state
     * @param row the row coordinate
     * @param col the col coordinate
     */
    private void undoMove(InputHandler.Disc[][] board, int row, int col)
    {
        if (row < 0
        || row >= board.length
        || col < 0
        || col >= board[0].length)
        {
            return;
        }

        board[row][col] = null;
    }


    /**
     * Generates a column order starting from the center outwards
     * @param width the number of columns in the board
     * @return array with columns
     */
    private int[] generateColumnOrder(int width)
    {
        int[] order  = new int[width];
        int   center = width / 2;

        int idx    = 0;
        order[idx] = center;
        idx++;

        for (int offset = 1; offset <= center; offset++)
        {
            if (center - offset >= 0)
            {
                order[idx] = center - offset;
                idx++;
            }

            if (center + offset < width)
            {
                order[idx] = center + offset;
                idx++;
            }
        }

        return order;
    }

    /**
     * Evaluates the board state and returns a score to determine how good/bad it is overall
     * @param board the current board state
     * @param me my (agent) disc
     * @return score
     */
    private int evaluateBoard(InputHandler.Disc[][] board, InputHandler.Disc me)
    {
        int score = 0;
        int rows  = board.length;
        int cols  = board[0].length;

        InputHandler.Disc opponent = me.opponent();

        // Position Control: Center column weight
        int centerCol = cols / 2;

        for (int row = 0; row < rows; row++)
        {
            if (board[row][centerCol] == me)       score += 3;
            if (board[row][centerCol] == opponent) score -= 3;
        }

        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

        for (int row = 0; row < rows; row++)
        {
            for (int col = 0; col < cols; col++)
            {
                for (int[] direction : directions)
                {
                    int endRow = row + 3 * direction[0];
                    int endCol = col + 3 * direction[1];

                    if (endRow >= 0 && endRow < rows && endCol >= 0 && endCol < cols)
                    {
                        score += evaluateWindow(board, row, col, direction[0], direction[1], me, opponent);
                    }
                }
            }
        }

        return score;
    }

    /**
     * Evaluates the given 4-space window and returns a score based on who is most likely to win
     * @param board the current board state
     * @param row the starting row
     * @param col the starting col
     * @param dRow row direction to go
     * @param dCol col direction to go
     * @param me my disc
     * @param opponent opponent disc
     * @return score
     */
    private int evaluateWindow(
            InputHandler.Disc[][] board,
            int row,
            int col,
            int dRow,
            int dCol,
            InputHandler.Disc me,
            InputHandler.Disc opponent)
    {
        int myDiscs       = 0;
        int opponentDiscs = 0;

        for (int idx = 0; idx < 4; idx++)
        {
            InputHandler.Disc disc = board[row + idx * dRow][col + idx * dCol];

            if (disc == me)       myDiscs++;
            if (disc == opponent) opponentDiscs++;
        }

        if (myDiscs > 0 && opponentDiscs > 0) return 0; // Segment mutually blocked

        if (myDiscs == 3)       return 100;  // Agent (me) close to winning
        if (myDiscs == 2)       return 10;
        if (opponentDiscs == 3) return -120; // Opponent close to winning
        if (opponentDiscs == 2) return -10;

        return 0;
    }
}
