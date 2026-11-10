Connect Four with Minimax and Alpha-Beta Pruning
================================================

A graphical Connect Four game in which a human plays against an AI agent that
uses Minimax with alpha-beta pruning.


LANGUAGE, VERSION, DEPENDENCIES
-------------------------------
- Java 25 (JDK 25 or newer)
- JavaFX 25.0.1 (downloaded automatically by Maven; no manual install needed)
- Apache Maven 3.9 or newer


BUILD
-----
From the project root (the folder containing pom.xml):

    mvn compile


LAUNCH
------
From the project root:

    mvn javafx:run

IntelliJ: open the project as a Maven project, then run src/Launcher.java.


HOW TO PLAY
-----------
- By default you play red against an AI agent playing orange.
- Click a column to drop your disc into it. Gravity places it in the lowest
  open slot. Clicking a full column flashes it red and does nothing.
- The agent then picks its move automatically.
- The first player to connect four discs horizontally, vertically, or
  diagonally wins. If the board fills with no winner, the game is a draw.
- When the game ends, a banner announces the result with "Play Again" and
  "Exit Game" buttons.


CONFIGURING M, N, AND d
-----------------------
All settings are in the "Game Settings" panel on the right side of the window.
No source code changes are needed.

    Setting       Range                         Default
    Rows (M)      4 - 12                        6
    Columns (N)   4 - 12                        7
    Red           Human or any agent, + depth d Human
    Orange        Human or any agent, + depth d Akiva's Agent, d = 4
    First move    Red / Orange                  Red

Each side has its own depth d (1 - 12), so agents can play each other at
different depths. The depth box is disabled when a side is Human.

Available agents: Akiva's Agent, Julia's Agent, and Opponent Harness (see
below). Any combination works: human vs agent, agent vs agent, or even
human vs human.

Settings can only be changed between rounds: before the first disc is placed,
or after a game ends. Press "New Game" (or "Play Again" on the result banner)
to start a game with the selected settings.

The winning condition is always four connected pieces, regardless of board
size. Search depth d counts single moves by either player (d = 4 looks four
individual moves ahead).

There are no command-line parameters or configuration files.


EXAMPLE
-------
To play one game on an 8 x 9 board against Julia's Agent at depth 6, with the
agent moving first:

    1. mvn javafx:run
    2. In Game Settings, set Rows = 8, Columns = 9, Red = Human,
       Orange = Julia's Agent with d = 6, First move = Orange
    3. Click "New Game"


ADDING AN AGENT
---------------
Agents implement the Agent interface (src/Agent.java):

    String getName();
    int chooseColumn(InputHandler.Disc[][] board, InputHandler.Disc me);

"me" is the color the agent is playing that game (RED or ORANGE), so the same
agent can play either side. To make a new agent selectable, add one line to
src/AgentRegistry.java. Any agent that throws an exception or returns an
invalid or full column forfeits the game.


OUTSIDE AIs (OPPONENT HARNESS)
------------------------------
To plug in an AI written by someone else, edit only
src/OpponentAI/OpponentHarness.java (instructions are at the top of that
file), then choose "Opponent Harness" for either side. The harness receives
the board as int[row][col] with row 0 at the top: 0 = empty, 1 = its own
discs, 2 = the other side's discs. It returns the column to play.


RECORDED DATA
-------------
- The "Game Log" panel lists every move. Agent moves show the time the agent
  spent choosing (search time only; animation and human thinking time are
  excluded). Each game ends with an average/maximum time summary per agent.
- Each finished game is appended as one row to results.csv in the folder the
  program was launched from (the project root when using mvn javafx:run):

    timestamp,rows,cols,first,red,red_depth,orange,orange_depth,
    winner,winner_name,forfeit,red_moves,red_avg_ms,red_max_ms,
    orange_moves,orange_avg_ms,orange_max_ms

  "winner" is Red, Orange, or Draw; "red"/"orange" name who played each side.
  Depth and timing columns are blank for a human side. If results.csv has a
  header from an older version, it is renamed to results-old-<time>.csv and a
  new file is started.


KNOWN ISSUES AND LIMITATIONS
----------------------------
- Board size is limited to 4 - 12 rows/columns; discs shrink on larger boards
  so the window fits on screen.
- High search depths on large boards can make the AI take a long time per move.
  The window stays responsive while it searches, but there is no time limit.
