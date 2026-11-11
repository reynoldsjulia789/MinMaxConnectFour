# Connect Four with Minimax and Alpha-Beta Pruning

**Premise:** Implement a graphical Connect Four game in which a human player competes against an AI agent. Apply the Minimax algorithm and alpha-beta pruning discussed in class, and investigate how search depth affects the AI's playing strength and response time.

Red plays against Yellow. Each side can be a human or any registered AI agent, so the game supports human vs agent, agent vs agent, and human vs human on one computer.

## Requirements

| | Version | Notes |
|---|---|---|
| Java | JDK 25 or newer | |
| JavaFX | 25.0.1 | Downloaded automatically by Maven; no manual install needed |
| Apache Maven | 3.9 or newer | https://maven.apache.org/ |

## Build and Run

From the project root (the folder containing `pom.xml`):

```
mvn compile        # build only
mvn javafx:run     # build and launch the game
```

**IntelliJ:** open the project as a Maven project, then run `src/Launcher.java` with the green play button.

There are no command-line parameters or configuration files; everything is set in the window.

**Jar file:** Whether the repo was cloned or the jar file was downloaded separately, open the cli. 
From the location of the Jar file, run `java -jar ./MinimaxConnectFour.jar`. 
Note that the Opponent Harness is not available via the jar file.

## How to Play

- By default you play **Red** against **Akiva's Agent** playing **Yellow**, on a 6 × 7 board, with Red moving first.
- Click a column to drop your disc. Gravity places it in the lowest open slot. Clicking a full column flashes it red and does nothing.
- While it's a human's turn, hovering over a column shows a half-inserted disc in that player's color, so it's always clear whose turn it is.
- Agents move automatically. The window stays responsive while an agent is thinking.
- The first side to connect **four** discs horizontally, vertically, or diagonally wins. If the board fills with no winner, the game is a draw.
- When the game ends, a banner announces the result with **Play Again** and **Exit Game** buttons. No moves are accepted after the game ends.

## Configuring M, N, and d

All settings are in the **Game Settings** panel on the right side of the window. No source code changes are needed.

| Setting | Range | Default |
|---|---|---|
| Rows (M) | 4 – 12 | 6 |
| Columns (N) | 4 – 12 | 7 |
| Red | Human or any agent, plus its depth d (1 – 12) | Human |
| Yellow | Human or any agent, plus its depth d (1 – 12) | Akiva's Agent, d = 4 |
| First move | Red / Yellow | Red |

- **Available agents:** Akiva's Agent, Julia's Agent, and Opponent Harness (see [Plugging in an outside AI](#plugging-in-an-outside-ai)).
- **Depth d** counts single moves by either side (d = 4 looks four individual moves ahead). Each side has its own depth, so agents can play each other at different depths. The depth box is disabled for a human side.
- **The win condition is always four in a row**, regardless of board size.
- **When settings apply:** settings can only change between rounds. Before the first disc is placed, changes take effect immediately. After a game ends, press **Play Again** (or **New Game**) to start with the new settings.

### Example

One game on an 8 × 9 board against Julia's Agent at depth 6, with the agent moving first:

1. Run `mvn javafx:run`
2. In Game Settings, set Rows = 8, Columns = 9, Red = Human, Yellow = Julia's Agent with d = 6, First move = Yellow
3. Click **New Game**

## Recorded Data

- **Game Log panel:** lists every move. Agent moves show the time the agent spent choosing: search time only, excluding animation and human thinking time. Each game ends with an average and maximum time summary per agent. The text can be selected and copied.
- **`results.csv`:** each finished game is appended as one row, in the folder the program was launched from (the project root when using `mvn javafx:run`):

  ```
  timestamp,rows,cols,first,red,red_depth,yellow,yellow_depth,winner,winner_name,forfeit,
  red_moves,red_avg_ms,red_max_ms,yellow_moves,yellow_avg_ms,yellow_max_ms
  ```

  - `winner` is `Red`, `Yellow`, or `Draw`; `red` and `yellow` name who played each side.
  - Depth and timing columns are blank for a human side.
  - If `results.csv` has a header from an older version, it is renamed to `results-old-<time>.csv` and a new file is started.

## Adding an Agent (in this project)

Agents implement the `Agent` interface ([src/Agent.java](src/Agent.java)) and take their search depth in the constructor:

```java
public interface Agent
{
    String getName();
    int chooseColumn(InputHandler.Disc[][] board, InputHandler.Disc me);
}
```

- **`board[row][col]`:** row 0 is the top row. Each slot is `null` (empty), `Disc.RED`, or `Disc.YELLOW`.
- **`me`:** the color the agent is playing this game, which is the maximizing player. `me.opponent()` is the other side.
- **Helpers in `InputHandler`:** `isWinningMove(board, row, col)`, `isBoardFull(board)`, `copyGrid(board)`.
- **Making it selectable:** add one `register(...)` line to [src/AgentRegistry.java](src/AgentRegistry.java) to add it to the Red/Yellow dropdowns.
- **The rest of the contract is the same as for outside AIs** (threading, forfeits, etc.); see below.

## Plugging in an Outside AI

To pit another AI (e.g. a classmate's) against a human or our agents, **only [src/OpponentAI/OpponentHarness.java](src/OpponentAI/OpponentHarness.java) needs editing**. The game handles turns, gravity, timing, win and draw detection, logging, and results.

### Hooking it up

1. Copy the AI's source files into `src/OpponentAI/` and add `package OpponentAI;` to the top of each file. Alternatively, keep its own package in the matching folder under `src/` and import it in the harness.
2. In the `OpponentHarness` constructor, create the AI.
3. In `getName()`, return its display name.
4. In `chooseColumn()`, convert the board to the AI's own format, call it, and return its column.
5. Run the game and choose **Opponent Harness** for Red or Yellow.

The harness ships with a placeholder that plays a random legal column, so it works before any AI is plugged in.

### The contract

The harness is the boundary between the game and the outside AI. The game guarantees the following, and expects the following in return.

**What the game provides**

| | |
|---|---|
| `OpponentHarness(int depth)` | A new instance is created for each game, with the depth d set for that side. Pass it on or ignore it. One depth level = one move by either side. |
| `chooseColumn(int[][] board)` | Called once per turn, only on the harness's turn. |
| Board layout | `board[row][col]`, **row 0 is the top row**; gravity pulls toward the last row. |
| Board values | `0` = empty, `1` = the AI's own discs, `2` = the other side's discs (`OpponentHarness.EMPTY` / `ME` / `OTHER`). Always from the AI's own point of view, whichever color it plays. |
| Board size | Any M × N from 4 × 4 to 12 × 12. Use `board.length` (rows) and `board[0].length` (columns); never assume 6 × 7. |
| Board ownership | A new copy every call. The AI may modify it freely (e.g. place and undo discs while searching). |
| Win condition | Four in a row (`OpponentHarness.CONNECT`), horizontally, vertically, or diagonally, on every board size. |
| Color and turn order | The AI may play Red or Yellow, and may move first or second. |
| Threading | Called on a background thread, one call at a time. The window stays responsive during the search. |
| Time limit | None. Each call is timed (wall-clock, around the call) and logged as that move's response time. |

**What the AI must do**

| | |
|---|---|
| Return value | A column index from `0` to `board[0].length - 1` whose top slot is empty (`board[0][col] == 0`). |
| Forfeits | Returning a full or out-of-range column, or throwing anything (including `StackOverflowError`), **forfeits the game**. The other side wins, the reason is shown on the banner and in the log, and `results.csv` marks the game with `forfeit` = `true`. |
| `getName()` | A short display name for the log, banner, and `results.csv`. Commas, quotes, and line breaks are replaced with spaces in the CSV. |
| No GUI access | Don't touch JavaFX or the window from inside the AI. Just return the column. |
| Dependencies | Java 25 standard library only. Any other library would need to be added to `pom.xml`. |

Why plain `int[][]`: the harness lives in its own package (`OpponentAI`), and Java doesn't let it see the game's classes (which have no package). Plain ints also convert easily to whatever board format another AI already uses.

## Known Issues & Limitations

- Board size is limited to 4 – 12 rows and columns. Discs shrink on larger boards so the window fits on screen.
- High search depths on large boards can make an agent take a long time per move. The window stays responsive, but there is no time limit.

## Citations

- Connect 4 GUI tutorial:
    - **Title:** JavaFX Game: Connect4
    - **Creator:** Almas Biam
    - **Timestamps:** 0:50 - 7:00, 8:20 - 21:18
    - **URL:** https://youtu.be/B5H_t0A_C14?si=PBOI7srskGaINjvI
