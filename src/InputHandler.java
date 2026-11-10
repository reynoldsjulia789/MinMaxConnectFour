import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import OpponentAI.OpponentHarness;
import javafx.application.Platform;

/**
 * Following Model, View, Controller pattern.
 * InputHandler is the controller that talks to view and model.
 * .
 * Two sides play, RED and ORANGE. Each side is either a human clicking columns, or an Agent.
 */
public class InputHandler
{
    /**
     * The two sides of the game
     */
    public enum Disc
    {
        RED, ORANGE;

        /**
         * @return the other side
         */
        public Disc opponent()
        {
            return (this == RED) ? ORANGE : RED;
        }

        /**
         * @return "Red" or "Yellow"
         */
        public String label()
        {
            return (this == RED) ? "Red" : "Yellow";
        }
    }

    /**
     * Who plays one side: an agent and its search depth, or a human (agent == null).
     */
    public record Side(Agent agent, int depth)
    {
        public static Side human()
        {
            return new Side(null, 0);
        }

        public boolean isHuman()
        {
            return agent == null;
        }

        public String name()
        {
            return isHuman() ? "Human" : agent.getName();
        }

        @Override
        public String toString()
        {
            return isHuman() ? "Human" : String.format("%s (d=%d)", name(), depth);
        }
    }

    private static final int  CONNECT      = 4; // discs in a row needed to win; fixed at 4 per the assignment
    private static final Path RESULTS_FILE = Path.of("results.csv");
    private static final String RESULTS_HEADER = "timestamp,rows,cols,first,"
        + "red,red_depth,orange,orange_depth,winner,winner_name,forfeit,"
        + "red_moves,red_avg_ms,red_max_ms,orange_moves,orange_avg_ms,orange_max_ms";

    private int  _rows;
    private int  _cols;
    private Side _red;
    private Side _orange;
    private Disc _first;

    private Disc    _turn;
    private boolean _isInputLocked; // true while a disc is dropping or an agent is thinking
    private boolean _isGameOver = true; // no game running until startGame() is called
    private int     _discCount;

    private Disc[][]  _grid;
    private final GUI _view;

    // stats for the game log and results file
    private int             _gameNumber  = 0;
    private final MoveStats _redStats    = new MoveStats();
    private final MoveStats _orangeStats = new MoveStats();

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
     * If an agent moves first, it is asked for a move immediately.
     * @param rows the number of rows
     * @param cols the number of columns
     * @param red who plays red
     * @param orange who plays orange
     * @param first which side moves first
     */
    public void startGame(int rows, int cols, Side red, Side orange, Disc first)
    {
        if (rows < CONNECT || cols < CONNECT)
        {
            throw new IllegalArgumentException("InputHandler.startGame: rows and cols must be at least " + CONNECT);
        }

        _rows   = rows;
        _cols   = cols;
        _red    = red;
        _orange = orange;
        _first  = first;

        _grid          = new Disc[_rows][_cols];
        _turn          = _first;
        _isInputLocked = false;
        _isGameOver    = false;
        _discCount     = 0;

        _redStats.reset();
        _orangeStats.reset();

        // the "=== Game N ===" header is logged at the first move (see logGameHeader),
        // so games abandoned before any move don't clutter the log

        if (!side(_turn).isHuman())
        {
            requestAgentMove();
        }

        _view.refreshSettingsEnabled();
        _view.refreshHoverPreview();
    }

    /**
     * Places a disc for the human whose turn it is.
     * @param col the column clicked
     * @return true if the disc was placed, false if the move was rejected (e.g. full column)
     */
    public boolean humanMove(int col)
    {
        return canHumanMove() && placeDisc(col, -1);
    }

    /**
     * Finds the location row in which to place the disc in the given column, accounting for gravity.
     * Places the current turn's disc there, then checks for a win/draw once the drop animation finishes.
     * @param col the column to place the disc in
     * @param elapsedMs how long the agent took to choose this move, or -1 for a human move
     * @return true if the disc was successfully placed, false if the disc could not be placed
     */
    private boolean placeDisc(int col, long elapsedMs)
    {
        if (_isGameOver || col < 0 || col >= _cols)
        {
            return false;
        }

        for (int row = (_rows - 1); row >= 0; row--)
        {
            if (_grid[row][col] == null)
            {
                final int  landedRow = row;
                final Disc disc      = _turn;

                if (_discCount == 0)
                {
                    logGameHeader();
                }

                _grid[row][col] = disc;
                _isInputLocked  = true;
                _discCount++;

                _view.log(String.format("%2d. %-6s col %2d %9s", _discCount, disc.label(), col,
                    (elapsedMs < 0) ? "human" : elapsedMs + " ms"));

                _view.refreshSettingsEnabled();
                _view.refreshHoverPreview(); // hide while the disc drops
                _view.makeDisc(row, col, disc, () -> afterDiscLands(landedRow, col, disc));

                return true;
            }
        }

        return false;
    }

    /**
     * Numbers the game and logs who is playing. Called when the first disc is placed.
     */
    private void logGameHeader()
    {
        _gameNumber++;

        _view.log(String.format("=== Game %d | %dx%d | %s first ===", _gameNumber, _rows, _cols, _first.label()));
        _view.log("Red   : " + _red);
        _view.log("Orange: " + _orange);
    }

    /**
     * Runs once a disc finishes dropping: ends the game on a win/draw, otherwise switches turns.
     */
    private void afterDiscLands(int row, int col, Disc disc)
    {
        if (isWinningMove(_grid, row, col))
        {
            endGame(disc, null);
            return;
        }

        if (isBoardFull(_grid))
        {
            endGame(null, null);
            return;
        }

        _turn          = disc.opponent();
        _isInputLocked = false;

        if (!side(_turn).isHuman())
        {
            requestAgentMove();
        }

        _view.refreshHoverPreview(); // next human's color, or hidden if an agent is up
    }

    /**
     * Asks the agent whose turn it is for a move on a background thread,
     * then places its disc back on the JavaFX thread. A crash or invalid column forfeits the game.
     */
    private void requestAgentMove()
    {
        _isInputLocked = true;

        final Disc      me       = _turn;
        final Agent     agent    = side(me).agent();
        final MoveStats stats    = stats(me);
        final Disc[][]  snapshot = copyGrid(_grid);

        Thread thread = new Thread(() -> {
            int  col;
            long start = System.nanoTime();

            try
            {
                col = agent.chooseColumn(snapshot, me);
            }
            catch (Throwable e) // any failure (including StackOverflowError) is a forfeit, not a frozen game
            {
                e.printStackTrace();
                Platform.runLater(() -> endGame(me.opponent(), agent.getName() + " crashed: " + e));
                return;
            }

            final int  chosen  = col;
            final long elapsed = (System.nanoTime() - start) / 1_000_000;

            Platform.runLater(() -> {
                stats.add(elapsed);

                if (!placeDisc(chosen, elapsed))
                {
                    endGame(me.opponent(), agent.getName() + " chose invalid or full column " + chosen);
                }
            });
        }, "agent-move");

        thread.setDaemon(true); // don't keep the app alive if the window closes mid-search
        thread.start();
    }

    /**
     * Ends the game, logs and saves a summary, and shows the game over banner
     * @param winner who won, or null for a draw
     * @param forfeitReason why the loser forfeited, or null for a normal finish
     */
    private void endGame(Disc winner, String forfeitReason)
    {
        _isGameOver = true;

        if (forfeitReason != null)
        {
            _view.log("FORFEIT: " + forfeitReason);
        }

        _view.log((winner == null) ? "Result: Draw"
            : String.format("Result: %s wins (%s)", winner.label(), side(winner).name()));

        for (Disc disc : Disc.values())
        {
            if (!side(disc).isHuman())
            {
                _view.log(String.format("%-6s: %s", disc.label(), stats(disc)));
            }
        }

        saveResult(winner, forfeitReason != null);

        _view.log("");

        _view.showGameOver(headline(winner), detail(winner, forfeitReason));
        _view.refreshSettingsEnabled();
        _view.refreshHoverPreview();
    }

    /**
     * @return the game over banner's main text
     */
    private String headline(Disc winner)
    {
        if (winner == null)
        {
            return "It's a draw!";
        }

        Side winnerSide = side(winner);
        Side loserSide  = side(winner.opponent());

        if (winnerSide.isHuman() && !loserSide.isHuman())
        {
            return "You win!";
        }

        if (!winnerSide.isHuman() && loserSide.isHuman())
        {
            return winnerSide.name() + " wins!";
        }

        return winner.label() + " wins!"; // human vs human or agent vs agent
    }

    /**
     * @return the game over banner's smaller text, or null for none
     */
    private String detail(Disc winner, String forfeitReason)
    {
        String detail = null;

        // agent vs agent: the headline says the color, so name the winner here
        if (winner != null && !side(winner).isHuman() && !side(winner.opponent()).isHuman())
        {
            detail = side(winner).toString();
        }

        if (forfeitReason != null)
        {
            detail = (detail == null) ? forfeitReason : detail + "\n" + forfeitReason;
        }

        return detail;
    }

    /**
     * Appends this game's results as one row of results.csv.
     * If the file is new, writes the header; if it has an outdated header, it is renamed to keep the old data.
     */
    private void saveResult(Disc winner, boolean wasForfeit)
    {
        String row = String.join(",",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            String.valueOf(_rows),
            String.valueOf(_cols),
            _first.label(),
            csvSafe(_red.name()),
            _red.isHuman() ? "" : String.valueOf(_red.depth()),
            csvSafe(_orange.name()),
            _orange.isHuman() ? "" : String.valueOf(_orange.depth()),
            (winner == null) ? "Draw" : winner.label(),
            (winner == null) ? "" : csvSafe(side(winner).name()),
            String.valueOf(wasForfeit),
            statsCells(Disc.RED),
            statsCells(Disc.ORANGE));

        try
        {
            if (Files.exists(RESULTS_FILE))
            {
                String existingHeader = Files.readAllLines(RESULTS_FILE).stream().findFirst().orElse("");

                if (!existingHeader.equals(RESULTS_HEADER))
                {
                    Path backup = Path.of("results-old-"
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv");

                    Files.move(RESULTS_FILE, backup);
                    _view.log("Old-format results moved to " + backup);
                }
            }

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
     * @return "moves,avg_ms,max_ms" for an agent side, or three blank cells for a human side
     */
    private String statsCells(Disc disc)
    {
        if (side(disc).isHuman())
        {
            return ",,";
        }

        MoveStats stats = stats(disc);

        return stats.count + "," + stats.averageMs() + "," + stats.maxMs;
    }

    private Side side(Disc disc)
    {
        return (disc == Disc.RED) ? _red : _orange;
    }

    private MoveStats stats(Disc disc)
    {
        return (disc == Disc.RED) ? _redStats : _orangeStats;
    }

    /**
     * @return the text with characters that would break a CSV cell removed
     */
    private static String csvSafe(String text)
    {
        return text.replaceAll("[,\"\\r\\n]", " ");
    }

    /**
     * @return true if a human is allowed to place a disc right now
     */
    public boolean canHumanMove()
    {
        return !_isGameOver && !_isInputLocked && side(_turn).isHuman();
    }

    /**
     * @return the side whose turn it is
     */
    public Disc getTurn()
    {
        return _turn;
    }

    /**
     * @param col a column index
     * @return true if the column has no empty slots
     */
    public boolean isColumnFull(int col)
    {
        return _grid[0][col] != null;
    }

    /**
     * @return true if a game is set up but nobody has moved yet (and no agent is thinking)
     */
    public boolean isUnstarted()
    {
        return !_isGameOver && _discCount == 0 && !_isInputLocked;
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

    /**
     * Converts the board to the plain-int format OpponentHarness uses.
     * @param grid the board, grid[row][col]
     * @param me whose point of view to encode from
     * @return a new board: OpponentHarness.EMPTY, ME (discs of 'me'), or OTHER
     */
    public static int[][] toIntBoard(Disc[][] grid, Disc me)
    {
        int[][] board = new int[grid.length][grid[0].length];

        for (int row = 0; row < grid.length; row++)
        {
            for (int col = 0; col < grid[0].length; col++)
            {
                Disc disc = grid[row][col];

                board[row][col] = (disc == null) ? OpponentHarness.EMPTY
                                : (disc == me)   ? OpponentHarness.ME
                                :                  OpponentHarness.OTHER;
            }
        }

        return board;
    }

    /**
     * Running totals of one side's move times.
     */
    private static class MoveStats
    {
        int  count;
        long totalMs;
        long maxMs;

        void reset()
        {
            count   = 0;
            totalMs = 0;
            maxMs   = 0;
        }

        void add(long ms)
        {
            count++;
            totalMs += ms;
            maxMs    = Math.max(maxMs, ms);
        }

        long averageMs()
        {
            return (count == 0) ? 0 : (totalMs / count);
        }

        @Override
        public String toString()
        {
            return String.format("%d moves | avg %d ms | max %d ms", count, averageMs(), maxMs);
        }
    }
}
