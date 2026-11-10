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
- Click a column to drop your disc (red) into it. Gravity places it in the
  lowest open slot. Clicking a full column flashes it red and does nothing.
- The AI (orange) then picks its move automatically.
- The first player to connect four discs horizontally, vertically, or
  diagonally wins. If the board fills with no winner, the game is a draw.
- When the game ends, a banner announces the result with "Play Again" and
  "Exit Game" buttons.


CONFIGURING M, N, AND d
-----------------------
All settings are in the "Game Settings" panel on the right side of the window.
No source code changes are needed.

    Setting       Range    Default
    Rows (M)      4 - 12   6
    Columns (N)   4 - 12   7
    Depth (d)     1 - 12   4
    First move    Player / AI   Player

Settings can only be changed between rounds: before the first disc is placed,
or after a game ends. Press "New Game" (or "Play Again" on the result banner)
to start a game with the selected settings.

The winning condition is always four connected pieces, regardless of board
size. Search depth d counts single moves by either player (d = 4 looks four
individual moves ahead).

There are no command-line parameters or configuration files.


EXAMPLE
-------
To play one game on an 8 x 9 board at depth 6 with the AI moving first:

    1. mvn javafx:run
    2. In Game Settings, set Rows = 8, Columns = 9, Depth = 6, First move = AI
    3. Click "New Game"


RECORDED DATA
-------------
- The "AI Response Times" panel shows the time the AI spent choosing each move
  (search time only; animation and human thinking time are excluded), plus a
  summary at the end of each game.
- Each finished game is appended as one row to results.csv in the folder the
  program was launched from (the project root when using mvn javafx:run):

    timestamp,rows,cols,depth,first_player,result,ai_moves,avg_ai_ms,max_ai_ms

  "result" is AI win, Human win, or Draw.


KNOWN ISSUES AND LIMITATIONS
----------------------------
- Board size is limited to 4 - 12 rows/columns; discs shrink on larger boards
  so the window fits on screen.
- High search depths on large boards can make the AI take a long time per move.
  The window stays responsive while it searches, but there is no time limit.
