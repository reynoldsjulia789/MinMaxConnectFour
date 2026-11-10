import javafx.application.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Following Model, View, Controller pattern.
 * InputHandler is the controller that talks to view and model.
 */
public class InputHandler
{
    public enum Disc { PLAYER, AI }

    private static final int  CONNECT      = 4; // discs in a row needed to win; fixed at 4 per the assignment
    private static final Path RESULTS_FILE = Path.of("results.csv");
    private static final String RESULTS_HEADER =
        "timestamp,rows,cols,depth,first_player,result,ai_moves,avg_ai_ms,max_ai_ms";

    private int          _rows;
    private int          _cols;
    private MinimaxAgent _agent;
    private boolean      _playerStarts;

    private boolean _isPlayerTurn;
    private boolean _isInputLocked; // true while a disc is dropping or the AI is thinking
    private boolean _isGameOver = true; // no game running until startGame() is called
    private int     _discCount;

    private Disc[][]  _grid;
    private final GUI _view;

    // stats for the response time log and results file
    private int  _gameNumber  = 0;
    private int  _aiMoveCount = 0;
    private long _aiTotalMs   = 0;
    private long _aiLongestMs = 0;

    /**
     * Initializes the input handler. Call startGame() to begin play.
     * @param view the GUI to draw moves on
     */
    public InputHandler(GUI view)
    {
        this._view = view;
    }

    /**
     * Clears the board and starts a new game with the given settings.
     * If the AI goes first, it is asked for a move immediately.
     * @param rows the number of rows
     * @param cols the number of columns
     * @param agent the AI opponent
     * @param playerStarts true to start with the player's turn, false to start with the AI's turn
     */
    public void startGame(int rows, int cols, MinimaxAgent agent, boolean playerStarts)
    {
        if (rows < CONNECT || cols < CONNECT)
        {
            throw new IllegalArgumentException("InputHandler.startGame: rows and cols must be at least " + CONNECT);
        }

        _rows          = rows;
        _cols          = cols;
        _agent         = agent;
        _playerStarts  = playerStarts;

        _grid          = new Disc[_rows][_cols];
        _isPlayerTurn  = _playerStarts;
        _isInputLocked = false;
        _isGameOver    = false;
        _discCount     = 0;

        _gameNumber++;
        _aiMoveCount = 0;
        _aiTotalMs   = 0;
        _aiLongestMs = 0;

        _view.log(String.format("=== Game %d | %dx%d | depth %d | %s first ===",
            _gameNumber, _rows, _cols, _agent.getMaxDepth(), _playerStarts ? "player" : "AI"));

        if (!_isPlayerTurn)
        {
            requestAIMove();
        }

        _view.refreshSettingsEnabled();
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
                _discCount++;

                _view.refreshSettingsEnabled();
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
     * Ends the game, logs and saves a summary of the AI's response times, and shows the game over banner
     * @param winner who won, or null for a draw
     */
    private void endGame(Disc winner)
    {
        _isGameOver = true;

        String result = (winner == null) ? "Draw" : (winner == Disc.PLAYER) ? "Human win" : "AI win";
        long   avgMs  = (_aiMoveCount == 0) ? 0 : (_aiTotalMs / _aiMoveCount);

        _view.log(String.format("%s | %d AI moves | avg %d ms | max %d ms",
            result, _aiMoveCount, avgMs, _aiLongestMs));

        saveResult(result, avgMs);

        _view.log("");
        _view.showGameOver(winner);
        _view.refreshSettingsEnabled();
    }

    /**
     * Appends this game's results as one row of results.csv, writing the header if the file is new.
     */
    private void saveResult(String result, long avgMs)
    {
        String row = String.join(",",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            String.valueOf(_rows),
            String.valueOf(_cols),
            String.valueOf(_agent.getMaxDepth()),
            _playerStarts ? "Human" : "AI",
            result,
            String.valueOf(_aiMoveCount),
            String.valueOf(avgMs),
            String.valueOf(_aiLongestMs));

        try
        {
            if (Files.notExists(RESULTS_FILE))
            {
                Files.writeString(RESULTS_FILE, RESULTS_HEADER + System.lineSeparator());
            }

            Files.writeString(RESULTS_FILE, row + System.lineSeparator(), StandardOpenOption.APPEND);

            _view.log("Saved to " + RESULTS_FILE);
            System.out.println("  (" + RESULTS_FILE.toAbsolutePath() + ")"); // full path in the console only
        }
        catch (IOException e)
        {
            _view.log("ERROR: could not write " + RESULTS_FILE.toAbsolutePath() + ": " + e.getMessage());
        }
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
     * Settings may only change "between rounds": after a game ends, or before any disc has been placed.
     * @return true if the game settings can be changed right now
     */
    public boolean canChangeSettings()
    {
        return _isGameOver || (_discCount == 0 && !_isInputLocked);
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
