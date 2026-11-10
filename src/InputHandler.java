import javafx.application.Platform;

/**
 * Following Model, View, Controller pattern.
 * InputHandler is the controller that talks to view and model.
 */
public class InputHandler
{
    public enum Disc { PLAYER, AI }

    private static final int CONNECT = 4; // discs in a row needed to win

    private final int _rows;
    private final int _cols;

    private final boolean _playerStarts;
    private boolean       _isPlayerTurn;
    private boolean       _isInputLocked; // true while a disc is dropping or the AI is thinking
    private boolean       _isGameOver;

    // stats for the response time log
    private int  _gameNumber   = 0;
    private int  _aiMoveCount  = 0;
    private long _aiTotalMs    = 0;
    private long _aiLongestMs  = 0;

    private Disc[][]           _grid;
    private final GUI          _view;
    private final MinimaxAgent _agent;

    /**
     * Initializes the input handler. Call startGame() to begin play.
     * @param playerStarts true to start with the player's turn, false to start with the AI's turn
     * @param rows the number of rows
     * @param cols the number of columns
     * @param view the GUI to draw moves on
     * @param agent the AI opponent
     */
    public InputHandler(boolean playerStarts, int rows, int cols, GUI view, MinimaxAgent agent)
    {
        if (rows < CONNECT && cols < CONNECT)
        {
            throw new IllegalArgumentException("InputHandler Constructor: board too small to connect " + CONNECT);
        }

        this._playerStarts = playerStarts;
        this._rows         = rows;
        this._cols         = cols;
        this._view         = view;
        this._agent        = agent;
    }

    /**
     * Clears the board and starts a new game. If the AI goes first, it is asked for a move immediately.
     */
    public void startGame()
    {
        _grid          = new Disc[_rows][_cols];
        _isPlayerTurn  = _playerStarts;
        _isInputLocked = false;
        _isGameOver    = false;

        _gameNumber++;
        _aiMoveCount = 0;
        _aiTotalMs   = 0;
        _aiLongestMs = 0;

        _view.log(String.format("=== Game %d | depth %d | %s first ===",
            _gameNumber, _agent.getMaxDepth(), _playerStarts ? "player" : "AI"));

        if (!_isPlayerTurn)
        {
            requestAIMove();
        }
    }

    /**
     * Finds the location row in which to place the disc in the given column, accounting for gravity.
     * Places the current turn's disc there, then checks for a win/draw once the drop animation finishes.
     * @param col the column to place the disc in
     * @return true if the disc was successfully placed, false if the disc could not be placed
     */
    public boolean placeDisc(int col)
    {
        if (_isGameOver || col < 0 || col >= _cols)
        {
            return false;
        }

        for (int row = (_rows - 1); row >= 0; row--)
        {
            if (getDisc(row, col) == null)
            {
                final int     landedRow    = row;
                final boolean isPlayerDisc = _isPlayerTurn;

                _grid[row][col] = isPlayerDisc ? Disc.PLAYER : Disc.AI;
                _isInputLocked  = true;

                _view.makeDisc(row, col, isPlayerDisc, () -> afterDiscLands(landedRow, col, isPlayerDisc));

                return true;
            }
        }

        return false;
    }

    /**
     * Runs once a disc finishes dropping: ends the game on a win/draw, otherwise switches turns.
     */
    private void afterDiscLands(int row, int col, boolean wasPlayerDisc)
    {
        if (isWinningMove(_grid, row, col))
        {
            endGame(wasPlayerDisc ? Disc.PLAYER : Disc.AI);
            return;
        }

        if (isBoardFull(_grid))
        {
            endGame(null);
            return;
        }

        _isPlayerTurn  = !wasPlayerDisc;
        _isInputLocked = false;

        if (!_isPlayerTurn)
        {
            requestAIMove();
        }
    }

    /**
     * Ends the game, logs a summary of the AI's response times, and shows the game over banner
     * @param winner who won, or null for a draw
     */
    private void endGame(Disc winner)
    {
        _isGameOver = true;

        String result = (winner == null) ? "Draw" : (winner == Disc.PLAYER) ? "Player wins" : "AI wins";
        long   avgMs  = (_aiMoveCount == 0) ? 0 : (_aiTotalMs / _aiMoveCount);

        _view.log(String.format("%s | %d AI moves | avg %d ms | max %d ms%n",
            result, _aiMoveCount, avgMs, _aiLongestMs));

        _view.showGameOver(winner);
    }

    /**
     * Asks the agent for a move on a background thread, then places its disc back on the JavaFX thread.
     */
    private void requestAIMove()
    {
        _isInputLocked = true;

        Disc[][] snapshot = copyGrid(_grid);

        Thread aiThread = new Thread(() -> {
            long start   = System.nanoTime();
            int  col     = _agent.chooseColumn(snapshot);
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            Platform.runLater(() -> {
                _aiMoveCount++;
                _aiTotalMs  += elapsed;
                _aiLongestMs = Math.max(_aiLongestMs, elapsed);

                _view.log(String.format("AI move %2d: col %d  %5d ms", _aiMoveCount, col, elapsed));

                if (!placeDisc(col))
                {
                    throw new IllegalStateException("MinimaxAgent chose an invalid or full column: " + col);
                }
            });
        }, "minimax-agent");

        aiThread.setDaemon(true); // don't keep the app alive if the window closes mid-search
        aiThread.start();
    }

    /**
     * @return true if the player is allowed to place a disc right now
     */
    public boolean canPlayerMove()
    {
        return _isPlayerTurn && !_isInputLocked && !_isGameOver;
    }

    /**
     * @return true if it is the player's turn, false otherwise
     */
    public boolean IsPlayerTurn()
    {
        return this._isPlayerTurn;
    }

    /**
     * Determines whether the specified location is occupied
     * @param row the row coordinate
     * @param col the column coordinate
     * @return if the slot was selected by the player, AI, or null if it's empty
     */
    public Disc getDisc(int row, int col)
    {
        if (row < 0
            || row >= _rows
            || col < 0
            || col >= _cols)
        {
            throw new IllegalArgumentException("InputHandler.getDisc: row or col outside range");
        }

        return _grid[row][col];
    }

    /**
     * Checks whether the disc at (row, col) is part of a line of four or more.
     * Only lines through that disc are checked, so call it with the most recently placed disc.
     * @param grid the board, grid[row][col]
     * @param row the row of the disc just placed
     * @param col the column of the disc just placed
     * @return true if that disc completes a win for its owner
     */
    public static boolean isWinningMove(Disc[][] grid, int row, int col)
    {
        Disc disc = grid[row][col];

        if (disc == null)
        {
            return false;
        }

        // horizontal, vertical, diagonal (down-right), diagonal (down-left)
        int[][] directions = { {0, 1}, {1, 0}, {1, 1}, {1, -1} };

        for (int[] dir : directions)
        {
            int count = 1
                + countInDirection(grid, row, col,  dir[0],  dir[1], disc)
                + countInDirection(grid, row, col, -dir[0], -dir[1], disc);

            if (count >= CONNECT)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Counts consecutive matching discs starting one step away from (row, col) in direction (dRow, dCol).
     */
    private static int countInDirection(Disc[][] grid, int row, int col, int dRow, int dCol, Disc disc)
    {
        int count = 0;
        int r     = row + dRow;
        int c     = col + dCol;

        while (r >= 0 && r < grid.length && c >= 0 && c < grid[0].length && grid[r][c] == disc)
        {
            count++;
            r += dRow;
            c += dCol;
        }

        return count;
    }

    /**
     * @param grid the board, grid[row][col]
     * @return true if every column is full (a draw, assuming nobody has won)
     */
    public static boolean isBoardFull(Disc[][] grid)
    {
        for (int col = 0; col < grid[0].length; col++)
        {
            if (grid[0][col] == null)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * @return a deep copy of the given board
     */
    public static Disc[][] copyGrid(Disc[][] grid)
    {
        Disc[][] copy = new Disc[grid.length][];

        for (int row = 0; row < grid.length; row++)
        {
            copy[row] = grid[row].clone();
        }

        return copy;
    }
}
