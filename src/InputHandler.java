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
 * InputHandler is the controller that talks to view and model: it owns the board, enforces the rules,
 * runs the turn loop, asks agents for moves, and records results. GUI only draws what it is told.
 * .
 * Two sides play, RED and YELLOW.
 * Each side is either a human clicking columns, or an Agent.
 * .
 * Turn loop:
 *  startGame() -> [human clicks -> humanMove()] or [requestAgentMove() on a background thread]
 *              -> placeDisc() -> drop animation -> afterDiscLands() -> win/draw? endGame() : next turn
 */
public class InputHandler
{
    /**
     * The two sides of the game
     */
    public enum Disc
    {
        RED, YELLOW;

        /**
         * @return the other side
         */
        public Disc opponent()
        {
            // ternary: RED <-> YELLOW
            return (this == RED) ? YELLOW : RED;
        }

        /**
         * @return the side's display name: "Red" or "Yellow"
         */
        public String label()
        {
            // ternary: RED -> "Red", YELLOW -> "Yellow"
            return (this == RED) ? "Red" : "Yellow";
        }
    }

    /**
     * Who plays one side: an agent and its search depth, or a human (agent == null).
     * A record is a small immutable class; Java generates the constructor, agent(), depth(), equals() and hashCode().
     * @param agent the agent playing this side, or null for a human
     * @param depth the agent's search depth (0 for a human)
     */
    public record Side(Agent agent, int depth)
    {
        /**
         * @return a side played by a human
         */
        public static Side human()
        {
            return new Side(null, 0);
        }

        /**
         * @return true if a human plays this side
         */
        public boolean isHuman()
        {
            return agent == null;
        }

        /**
         * @return "Human", or the agent's name
         */
        public String name()
        {
            // ternary: humans have no agent to ask for a name
            return isHuman() ? "Human" : agent.getName();
        }

        /**
         * @return "Human", or the agent's name with its depth, e.g. "Akiva's Agent (d=4)"
         */
        @Override
        public String toString()
        {
            // ternary: depth only means something for an agent
            return isHuman() ? "Human" : String.format("%s (d=%d)", name(), depth);
        }
    }

    private static final int  CONNECT      = 4; // discs in a row needed to win; fixed at 4 per the assignment
    private static final Path RESULTS_FILE = Path.of("results.csv"); // relative to the folder the game was launched from
    private static final String RESULTS_HEADER = "timestamp,rows,cols,first,"
        + "red,red_depth,yellow,yellow_depth,winner,winner_name,forfeit,"
        + "red_moves,red_avg_ms,red_max_ms,yellow_moves,yellow_avg_ms,yellow_max_ms";

    // settings for the current game
    private int  _rows;
    private int  _cols;
    private Side _red;
    private Side _yellow;
    private Disc _first;

    // state of the current game
    private Disc    _turn;
    private boolean _isInputLocked; // true while a disc is dropping or an agent is thinking
    private boolean _isGameOver = true; // no game running until startGame() is called
    private int     _discCount;

    private Disc[][]  _grid; // _grid[row][col]; row 0 is the top row; null = empty
    private final GUI _view;

    // stats for the game log and results file
    private int             _gameNumber  = 0;
    private final MoveStats _redStats    = new MoveStats();
    private final MoveStats _yellowStats = new MoveStats();

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
     * @param yellow who plays yellow
     * @param first which side moves first
     */
    public void startGame(int rows, int cols, Side red, Side yellow, Disc first)
    {
        if (rows < CONNECT || cols < CONNECT)
        {
            throw new IllegalArgumentException("InputHandler.startGame: rows and cols must be at least " + CONNECT);
        }

        _rows   = rows;
        _cols   = cols;
        _red    = red;
        _yellow = yellow;
        _first  = first;

        _grid          = new Disc[_rows][_cols];
        _turn          = _first;
        _isInputLocked = false;
        _isGameOver    = false;
        _discCount     = 0;

        _redStats.reset();
        _yellowStats.reset();

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
        // && short-circuits: placeDisc only runs if a human is allowed to move
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

        // gravity: scan from the bottom row up and use the first empty slot
        for (int row = (_rows - 1); row >= 0; row--)
        {
            if (_grid[row][col] == null)
            {
                final int  landedRow = row; // final copies so the lambda below can use them
                final Disc disc      = _turn;

                if (_discCount == 0)
                {
                    logGameHeader();
                }

                _grid[row][col] = disc;
                _isInputLocked  = true;
                _discCount++;

                // e.g. " 7. Red    col  3     12 ms"; ternary: humans have no search time
                _view.log(String.format("%2d. %-6s col %2d %9s", _discCount, disc.label(), col,
                    (elapsedMs < 0) ? "human" : elapsedMs + " ms"));

                _view.refreshSettingsEnabled();
                _view.refreshHoverPreview(); // hide while the disc drops
                // lambda: afterDiscLands runs once the drop animation finishes
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
        _view.log("Yellow: " + _yellow);
    }

    /**
     * Runs once a disc finishes dropping: ends the game on a win/draw, otherwise switches turns.
     * @param row the row the disc landed in
     * @param col the column the disc landed in
     * @param disc whose disc it was
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
     * .
     * JavaFX only allows changes to the window from its own thread, so the agent searches on a separate
     * thread (keeping the window responsive) and Platform.runLater() hands the result back.
     */
    private void requestAgentMove()
    {
        _isInputLocked = true;

        // final copies: the background thread must not read fields that may change while it runs
        final Disc      me       = _turn;
        final Agent     agent    = side(me).agent();
        final MoveStats stats    = stats(me);
        final Disc[][]  snapshot = copyGrid(_grid);

        // lambda: the code the background thread runs
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
                // lambda: run endGame back on the JavaFX thread
                Platform.runLater(() -> endGame(me.opponent(), agent.getName() + " crashed: " + e));
                return;
            }

            final int  chosen  = col;
            final long elapsed = (System.nanoTime() - start) / 1_000_000; // nanoseconds -> milliseconds

            // lambda: record the time and place the disc back on the JavaFX thread
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

        // ternary: "Result: Draw", or e.g. "Result: Red wins (Human)"
        _view.log((winner == null) ? "Result: Draw"
            : String.format("Result: %s wins (%s)", winner.label(), side(winner).name()));

        // timing summary for each side played by an agent
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
     * Builds the game over banner's main text.
     * @param winner who won, or null for a draw
     * @return e.g. "You win!", "Akiva's Agent wins!", "Red wins!", or "It's a draw!"
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
     * Builds the game over banner's smaller text.
     * @param winner who won, or null for a draw
     * @param forfeitReason why the loser forfeited, or null for a normal finish
     * @return the winning agent's name (agent vs agent only) and/or the forfeit reason, or null for none
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
            // ternary: use the reason alone, or add it on a new line below the winner's name
            detail = (detail == null) ? forfeitReason : detail + "\n" + forfeitReason;
        }

        return detail;
    }

    /**
     * Appends this game's results as one row of results.csv.
     * If the file is new, writes the header; if it has an outdated header, it is renamed to keep the old data.
     * @param winner who won, or null for a draw
     * @param wasForfeit true if the game ended by forfeit
     */
    private void saveResult(Disc winner, boolean wasForfeit)
    {
        // one cell per RESULTS_HEADER column, in the same order.
        // ternaries: depth is blank for a human side; winner columns read "Draw" / blank when nobody won
        String row = String.join(",",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            String.valueOf(_rows),
            String.valueOf(_cols),
            _first.label(),
            csvSafe(_red.name()),
            _red.isHuman() ? "" : String.valueOf(_red.depth()),
            csvSafe(_yellow.name()),
            _yellow.isHuman() ? "" : String.valueOf(_yellow.depth()),
            (winner == null) ? "Draw" : winner.label(),
            (winner == null) ? "" : csvSafe(side(winner).name()),
            String.valueOf(wasForfeit),
            statsCells(Disc.RED),
            statsCells(Disc.YELLOW));

        try
        {
            if (Files.exists(RESULTS_FILE))
            {
                // first line of the file, or "" if the file is empty
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
     * Builds one side's three timing cells for results.csv.
     * @param disc which side
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

    /**
     * @param disc which side
     * @return who is playing that side this game
     */
    private Side side(Disc disc)
    {
        // ternary: pick the matching field
        return (disc == Disc.RED) ? _red : _yellow;
    }

    /**
     * @param disc which side
     * @return that side's move timing stats for this game
     */
    private MoveStats stats(Disc disc)
    {
        // ternary: pick the matching field
        return (disc == Disc.RED) ? _redStats : _yellowStats;
    }

    /**
     * Makes text safe to put in one CSV cell.
     * @param text the text, e.g. an agent's name
     * @return the text with characters that would break a CSV cell removed
     */
    private static String csvSafe(String text)
    {
        // regex: replace any comma, double quote, carriage return, or newline with a space
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
        return _grid[0][col] != null; // the top slot fills last
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

        // {row step, col step} for each line direction:
        // horizontal, vertical, diagonal (down-right), diagonal (down-left)
        int[][] directions = { {0, 1}, {1, 0}, {1, 1}, {1, -1} };

        // for each direction, count matching discs both ways along the line, plus the disc itself
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
     * @param grid the board, grid[row][col]
     * @param row the starting row
     * @param col the starting column
     * @param dRow row step per move (-1, 0, or 1)
     * @param dCol column step per move (-1, 0, or 1)
     * @param disc the disc to match
     * @return how many matching discs in a row, not counting the starting slot
     */
    private static int countInDirection(Disc[][] grid, int row, int col, int dRow, int dCol, Disc disc)
    {
        int count = 0;
        int r     = row + dRow;
        int c     = col + dCol;

        // keep stepping while still on the board and still on a matching disc
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
        // a column is full once its top slot is taken; any empty top slot means the board isn't full
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
     * @param grid the board to copy
     * @return a deep copy of the given board (changing the copy never changes the original)
     */
    public static Disc[][] copyGrid(Disc[][] grid)
    {
        Disc[][] copy = new Disc[grid.length][];

        // copy each row separately; copying only the outer array would still share the rows
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

        // convert every slot, row by row
        for (int row = 0; row < grid.length; row++)
        {
            for (int col = 0; col < grid[0].length; col++)
            {
                Disc disc = grid[row][col];

                // chained ternary: empty -> EMPTY, my disc -> ME, otherwise -> OTHER
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

        /**
         * Clears the totals for a new game.
         */
        void reset()
        {
            count   = 0;
            totalMs = 0;
            maxMs   = 0;
        }

        /**
         * Records one move's time.
         * @param ms how long the move took to choose, in milliseconds
         */
        void add(long ms)
        {
            count++;
            totalMs += ms;
            maxMs    = Math.max(maxMs, ms);
        }

        /**
         * @return the average move time in milliseconds, or 0 if no moves were made
         */
        long averageMs()
        {
            // ternary: avoid dividing by zero
            return (count == 0) ? 0 : (totalMs / count);
        }

        /**
         * @return e.g. "9 moves | avg 12 ms | max 40 ms", for the game log
         */
        @Override
        public String toString()
        {
            return String.format("%d moves | avg %d ms | max %d ms", count, averageMs(), maxMs);
        }
    }
}
