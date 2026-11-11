/**
 * Akiva's Connect Four agent. Chooses moves using Minimax with Alpha-Beta Pruning.
 * .
 * The game calls chooseColumn() whenever it is this agent's turn.
 * See the Agent interface for the board layout and available helpers.
 * .
 * Algorithm overview:
 *  1. ROOT (chooseColumn): try every legal column, score each one with minimax, keep the best column.
 *  2. SEARCH (minimax): recursively alternate between our moves (MAX) and the opponent's moves (MIN),
 *     pruning branches with alpha-beta once they can no longer affect the result.
 *  3. TERMINAL CHECKS: a win, a draw (full board), or hitting the depth limit stops the recursion.
 *  4. HEURISTIC (evaluate): at the depth limit, estimate how good the position is for us.
 *  5. MOVE ORDERING (columnOrder): search center columns first so alpha-beta prunes more.
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
     * This is the ROOT of the minimax search tree: it acts as a MAX node that also remembers
     * WHICH column produced the best score (minimax() only returns scores).
     * @param board a copy of the current board; board[row][col], row 0 is the top, null = empty
     * @param me the color this agent is playing (the maximizing player); me.opponent() is the other side
     * @return the column (0 to cols - 1) to place the disc in; must not be full
     */
    @Override
    public int chooseColumn(InputHandler.Disc[][] board, InputHandler.Disc me)
    {
        // ===== ROOT STEP 1: initialize search state =====
        int bestCol = -1;                         // -1 = no legal column found yet
        int bestScore = Integer.MIN_VALUE;        // worst possible score, so any real score beats it
        // alpha = best score MAX (us) is guaranteed so far; beta = best score MIN (opponent) is guaranteed so far.
        // Start with the widest possible window: nothing is guaranteed yet.
        int alpha = Integer.MIN_VALUE, beta = Integer.MAX_VALUE;

        // ===== ROOT STEP 2: try each column, center-first (move ordering improves pruning) =====
        for (int col : columnOrder(board[0].length))
        {
            // make the move: drop our disc; dropDisc returns the row it landed in
            int row = dropDisc(board, col, me);
            if (row == -1) continue;              // column is full, not a legal move, skip it

            // ===== ROOT STEP 3: score this move =====
            int score;
            if (InputHandler.isWinningMove(board, row, col))
                score = WIN_SCORE + _maxDepth;    // TERMINAL: immediate win; + depth so faster wins score higher
            else if (InputHandler.isBoardFull(board))
                score = 0;                        // TERMINAL: board filled with no winner = draw
            else if (_maxDepth == 1)
                score = evaluate(board, me);      // DEPTH LIMIT: depth 1 only looks at our move, so use the heuristic
            else
                // RECURSE: opponent moves next, so the child is a MIN node (maximizing = false), one less ply remaining
                score = minimax(board, _maxDepth - 1, alpha, beta, false, me);

            // undo the move so the board is clean for the next column (we search on one shared board copy)
            board[row][col] = null;

            // ===== ROOT STEP 4: keep the best column seen so far =====
            if (bestCol == -1 || score > bestScore)  // bestCol == -1 guarantees a legal move even if every move loses
            {
                bestScore = score;
                bestCol = col;
            }
            // raise alpha: later columns' subtrees can prune anything that can't beat bestScore
            alpha = Math.max(alpha, bestScore);
        }

        // ===== ROOT STEP 5: return the chosen column =====
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
     * Only used at the depth limit, when the position is not already a win or a draw.
     * @param board the board position to evaluate
     * @param me the agent's color; positive scores favor this color
     * @return a heuristic score: higher is better for me, lower is better for the opponent
     */
    private static int evaluate(InputHandler.Disc[][] board, InputHandler.Disc me)
    {
        int rows = board.length, cols = board[0].length, score = 0;

        // ===== HEURISTIC STEP 1: center column preference =====
        // center discs are part of the most possible 4-in-a-rows, so each one we own is worth +3
        int center = cols / 2;
        for (int r = 0; r < rows; r++)
            if (board[r][center] == me) score += 3;

        // ===== HEURISTIC STEP 2: score every 4-cell window on the board =====
        // each direction is a {dRow, dCol} step: horizontal, vertical, diagonal down-right, diagonal down-left
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        // treat every cell (r, c) as the START of a window, in each of the 4 directions
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                for (int[] d : dirs)
                {
                    // the window covers (r, c) plus 3 more steps in direction d; find its last cell
                    int endR = r + 3 * d[0], endC = c + 3 * d[1];
                    // skip windows that would run off the board
                    if (endR < 0 || endR >= rows || endC < 0 || endC >= cols) continue;
                    score += scoreWindow(board, r, c, d[0], d[1], me);
                }
        return score;
    }

    /**
     * Scores a single 4-cell window starting at (r, c) and stepping by (dr, dc).
     * @param board the board position
     * @param r starting row of the window
     * @param c starting column of the window
     * @param dr row step between cells (0 or 1)
     * @param dc column step between cells (-1, 0, or 1)
     * @param me the agent's color
     * @return the window's contribution to the heuristic score
     */
    private static int scoreWindow(InputHandler.Disc[][] board, int r, int c, int dr, int dc, InputHandler.Disc me)
    {
        // count how many of the 4 cells are ours vs the opponent's (the rest are empty)
        int mine = 0, theirs = 0;
        for (int i = 0; i < 4; i++)
        {
            // i-th cell of the window: start + i steps in direction (dr, dc)
            InputHandler.Disc cell = board[r + i * dr][c + i * dc];
            if (cell == me) mine++;
            else if (cell != null) theirs++;   // not ours and not empty = opponent's disc
        }
        if (mine > 0 && theirs > 0) return 0;  // blocked window: both colors present, nobody can complete it
        if (mine == 3) return 5;               // 3 of ours + 1 empty: one move from a win
        if (mine == 2) return 2;               // 2 of ours + 2 empty: building toward a win
        if (theirs == 3) return -4;            // 3 of theirs + 1 empty: opponent threat
        return 0;                              // anything else (empty, single discs) is neutral
    }

    // score for a guaranteed win; far larger than any heuristic total so a real win always beats a "good-looking" position
    private static final int WIN_SCORE = 1_000_000;

    /**
     * The core of the minimax algorithm with alpha-beta pruning.
     * Plays every legal move for the current player, scores the result, then undoes it.
     * @param board the current board; modified during the search but restored before returning
     * @param depth plies remaining, INCLUDING the move made at this node (1 = this is the last move searched)
     * @param alpha the best score MAX is already guaranteed elsewhere in the tree
     * @param beta the best (lowest) score MIN is already guaranteed elsewhere in the tree
     * @param maximizing true if it is our turn (MAX node), false if it is the opponent's turn (MIN node)
     * @param me the agent's color (always the maximizing player)
     * @return the minimax value of this position from our perspective
     */
    private int minimax(InputHandler.Disc[][] board, int depth, int alpha, int beta,
                        boolean maximizing, InputHandler.Disc me)
    {
        // ===== MINIMAX STEP 1: set up this node =====
        // whose disc gets dropped at this node: ours on a MAX node, the opponent's on a MIN node
        InputHandler.Disc mover = maximizing ? me : me.opponent();
        // start best at the worst value for this node's player, so the first real score replaces it:
        // MAX starts at -infinity (looking for higher), MIN starts at +infinity (looking for lower)
        int best = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // ===== MINIMAX STEP 2: generate children (each legal column, center-first) =====
        for (int col : columnOrder(board[0].length))
        {
            // make the move
            int row = dropDisc(board, col, mover);
            if (row == -1) continue;               // column full, skip

            // ===== MINIMAX STEP 3: terminal checks, else recurse =====
            int score;
            if (InputHandler.isWinningMove(board, row, col))
                // TERMINAL (win): if we (MAX) won, score is +(WIN_SCORE + depth); if the opponent (MIN) won, it's negative.
                // adding depth (plies remaining) makes sooner wins worth more and sooner losses worth less (more negative),
                // so the agent wins as fast as possible and delays losses as long as possible
                score = maximizing ? WIN_SCORE + depth : -(WIN_SCORE + depth);
            else if (InputHandler.isBoardFull(board))
                score = 0;                          // TERMINAL (draw)
            else if (depth == 1)
                score = evaluate(board, me);        // DEPTH LIMIT reached: use the heuristic
            else
                // RECURSE: the other player moves next (!maximizing flips MAX <-> MIN), one less ply remaining
                score = minimax(board, depth - 1, alpha, beta, !maximizing, me);

            board[row][col] = null;                 // undo the move so siblings see the original board

            // ===== MINIMAX STEP 4: update best score and the alpha/beta window =====
            // MAX node: keep the highest score and raise alpha (our guaranteed minimum)
            if (maximizing) { best = Math.max(best, score); alpha = Math.max(alpha, best); }
            // MIN node: keep the lowest score and lower beta (opponent's guaranteed maximum)
            else            { best = Math.min(best, score); beta  = Math.min(beta,  best); }

            // ===== MINIMAX STEP 5: alpha-beta pruning =====
            // once alpha >= beta, the player above this node already has a better option elsewhere,
            // so it will never let the game reach here; the remaining children can't matter
            if (alpha >= beta) break;               // prune
        }

        // ===== MINIMAX STEP 6: return this node's value to its parent =====
        return best;
    }

    /**
     * Drops a disc into column and returns the row it landed in, or -1 if the column is full.
     * Simulates gravity: scans from the bottom row (board.length - 1) upward to the first empty slot.
     */
    private static int dropDisc(InputHandler.Disc[][] board, int col, InputHandler.Disc disc)
    {
        // start at the bottom row and move up until we find an empty slot
        for (int row = board.length - 1; row >= 0; row--)
        {
            if (board[row][col] == null)
            {
                board[row][col] = disc;  // place the disc in the lowest empty slot
                return row;
            }
        }
        return -1;  // every slot in this column is filled
    }

    /** Produces column indices ordered center-first
     * since center moves tend to be stronger in connect four
     * and this board can be dynamically sized
     * e.g. 7 cols -> 3,2,4,1,5,0,6
     * Searching strong moves first lets alpha-beta prune more branches. */
    private static int[] columnOrder(int cols)
    {
        int[] order = new int[cols];
        int center = cols / 2;
        for (int i = 0; i < cols; i++)
        {
            // alternates: center, center-1, center+1, center-2, ...
            // even i (0, 2, 4, ...) -> center + 0, +1, +2, ...  (i / 2 steps to the right)
            // odd i  (1, 3, 5, ...) -> center - 1, -2, -3, ...  ((i + 1) / 2 steps to the left)
            order[i] = (i % 2 == 0) ? center + i / 2 : center - (i + 1) / 2;
        }
        return order;
    }


}
