import java.util.ArrayList;
import java.util.List;

import javafx.animation.FillTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

/**
 * GUI created by following this tutorial, modifications made to fit needs:
 * JavaFX Game: Connect4 by Almas Biam,
 * Timestamps: 0:50 - 7:00, 8:20 - 21:18,
 * URL: https://youtu.be/B5H_t0A_C14?si=PBOI7srskGaINjvI
 * .
 * Following Model, View, Controller pattern.
 * GUI handles display (view): the board, the game over banner, the settings panel, and the game log.
 * All game rules and turn handling live in InputHandler (controller).
 * .
 * Window layout: the board on the left, a side panel (Game Settings + Game Log) on the right.
 * The board is rebuilt from scratch whenever a new game starts, so its size can change between games.
 */
public class GUI extends Application
{
    // default settings (changeable in the window between rounds)
    private static final int     DEFAULT_ROWS          = 6;
    private static final int     DEFAULT_COLS          = 7;
    private static final int     DEFAULT_DEPTH         = 4;
    private static final String  DEFAULT_RED           = AgentRegistry.HUMAN;
    private static final String  DEFAULT_YELLOW        = AgentRegistry.firstAgent();
    private static final InputHandler.Disc DEFAULT_FIRST = InputHandler.Disc.RED;

    // settings limits
    private static final int MIN_SIZE  = 4;  // must fit four in a row
    private static final int MAX_SIZE  = 12;
    private static final int MIN_DEPTH = 1;
    private static final int MAX_DEPTH = 12;

    // sizing: tiles shrink on larger boards so the window still fits on screen
    private static final int MAX_TILE_SIZE    = 80;
    private static final int MAX_BOARD_WIDTH  = 800;
    private static final int MAX_BOARD_HEIGHT = 640;
    private static final int LOG_PANEL_WIDTH  = 440; // fits ~55 monospaced characters per line

    // colors: check out Oracle's JavaFX documentation for options
    private static final Color _gridColor    = Color.MIDNIGHTBLUE;
    private static final Color _redColor     = Color.FIREBRICK;
    private static final Color _yellowColor  = Color.ORANGE; // the yellow side's disc color (swap for Color.GOLD etc. to change the shade)
    private static final Color _colHighlight = Color.LIGHTYELLOW;
    private static final Color _errorColor   = Color.RED;

    // current board geometry (set by applyBoardSize)
    private int    _rows;
    private int    _cols;
    private int    _tileSize;  // diameter of one slot, in pixels
    private double _colMargin; // horizontal gap between slots
    private double _rowMargin; // vertical gap between slots

    // display
    private Stage    _stage;
    private HBox     _root;             // [board, side panel]
    private Pane     _discRoot;         // holds placed discs; drawn behind the grid so they show through the holes
    private Circle   _previewDisc;      // half-inserted disc above the hovered column
    private int      _hoveredCol = -1;  // -1 when the mouse isn't over a column
    private VBox     _gameOverBanner;
    private Label    _resultLabel;
    private Label    _detailLabel;
    private VBox     _sidePanel;
    private TextArea _logArea;

    // settings controls
    private VBox                _settingsBox;
    private Spinner<Integer>    _rowsSpinner;
    private Spinner<Integer>    _colsSpinner;
    private ChoiceBox<String>   _redChoice;
    private Spinner<Integer>    _redDepthSpinner;
    private ChoiceBox<String>   _yellowChoice;
    private Spinner<Integer>    _yellowDepthSpinner;
    private ChoiceBox<InputHandler.Disc> _firstMoveChoice;

    // mechanics
    private InputHandler _input;

    /**
     * Main method: hands control to JavaFX, which then calls start().
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args)
    {
        launch(args);
    }

    /**
     * Sets the stage: builds the window, then starts the first game with the default settings.
     * Called by JavaFX after launch().
     * @param stage the main window, provided by JavaFX
     * @throws Exception if JavaFX fails to build the window
     */
    @Override
    public void start(Stage stage) throws Exception
    {
        _stage = stage;
        _input = new InputHandler(this);

        _sidePanel = makeSidePanel();
        _root      = new HBox(new Pane(), _sidePanel); // placeholder board, replaced by startNewGame()

        stage.setTitle("Connect Four with Minimax & Alpha-Beta Pruning");
        stage.setScene(new Scene(_root));
        stage.getIcons().addAll(makeIcon(16), makeIcon(32), makeIcon(64));
        stage.setResizable(false);

        startNewGame();

        stage.show();
    }

    /**
     * Reads the settings controls, rebuilds the board at the chosen size, and starts a new game.
     * Used by the New Game and Play Again buttons, and when a setting changes before the first move.
     */
    private void startNewGame()
    {
        int rows = _rowsSpinner.getValue();
        int cols = _colsSpinner.getValue();

        InputHandler.Side red    = makeSide(_redChoice.getValue(),    _redDepthSpinner.getValue());
        InputHandler.Side yellow = makeSide(_yellowChoice.getValue(), _yellowDepthSpinner.getValue());

        applyBoardSize(rows, cols);

        // swap the old board (child 0 of the root) for a freshly built one, then fit the window to it
        _root.getChildren().set(0, makeBoard());
        _sidePanel.setPrefHeight((_rows + 1) * _tileSize);
        _stage.sizeToScene();

        _input.startGame(rows, cols, red, yellow, _firstMoveChoice.getValue());
    }

    /**
     * Turns a dropdown choice into a side for InputHandler.
     * @param choice a dropdown choice from AgentRegistry.choices()
     * @param depth the search depth for an agent (ignored for a human)
     * @return the side description for InputHandler
     */
    private InputHandler.Side makeSide(String choice, int depth)
    {
        Agent agent = AgentRegistry.create(choice, depth);

        // ternary: AgentRegistry returns null for "Human", otherwise wrap the agent with its depth
        return (agent == null) ? InputHandler.Side.human() : new InputHandler.Side(agent, depth);
    }

    /**
     * Sets the board dimensions and scales the tiles so the board fits on screen.
     * @param rows the number of rows
     * @param cols the number of columns
     */
    private void applyBoardSize(int rows, int cols)
    {
        _rows      = rows;
        _cols      = cols;
        // largest tile that keeps the whole board within MAX_BOARD_WIDTH x MAX_BOARD_HEIGHT, capped at MAX_TILE_SIZE
        _tileSize  = Math.min(MAX_TILE_SIZE,
                     Math.min(MAX_BOARD_HEIGHT / (_rows + 1), MAX_BOARD_WIDTH / (_cols + 1)));
        _colMargin = (double) _tileSize / (_cols + 1);
        _rowMargin = (double) _tileSize / (_rows + 1);
    }

    /**
     * Creates the board area: discs, grid, clickable columns, and the game over banner on top.
     * Children are layered in the order they're added (later = drawn on top).
     * @return the board
     */
    private Pane makeBoard()
    {
        Pane board = new Pane();

        _discRoot = new Pane();

        // preview sits behind the grid like real discs, so only its lower half shows through the top hole
        _previewDisc = new DiscShape(InputHandler.Disc.RED);
        _previewDisc.setOpacity(0.75);
        _previewDisc.setMouseTransparent(true);
        _previewDisc.setVisible(false);
        _hoveredCol = -1;

        board.getChildren().add(_discRoot);
        board.getChildren().add(_previewDisc);
        board.getChildren().add(makeGrid());
        board.getChildren().addAll(makeColumns());
        board.getChildren().add(makeGameOverBanner()); // added last so it sits on top of everything

        return board;
    }

    /**
     * Creates the side panel: game settings on top, the game log below.
     * @return the panel
     */
    private VBox makeSidePanel()
    {
        // settings
        Label settingsTitle = new Label("Game Settings");
        settingsTitle.setFont(Font.font("System", FontWeight.BOLD, 16));

        _rowsSpinner = makeSpinner(MIN_SIZE, MAX_SIZE, DEFAULT_ROWS);
        _colsSpinner = makeSpinner(MIN_SIZE, MAX_SIZE, DEFAULT_COLS);

        _redDepthSpinner    = makeSpinner(MIN_DEPTH, MAX_DEPTH, DEFAULT_DEPTH);
        _redChoice          = makeSideChoice(DEFAULT_RED, _redDepthSpinner);
        _yellowDepthSpinner = makeSpinner(MIN_DEPTH, MAX_DEPTH, DEFAULT_DEPTH);
        _yellowChoice       = makeSideChoice(DEFAULT_YELLOW, _yellowDepthSpinner);

        _firstMoveChoice = new ChoiceBox<>();
        _firstMoveChoice.getItems().addAll(InputHandler.Disc.values());
        _firstMoveChoice.setValue(DEFAULT_FIRST);
        // anonymous class: tells the dropdown to display each Disc by its label ("Red"/"Yellow")
        _firstMoveChoice.setConverter(new StringConverter<>()
        {
            /**
             * @param disc a dropdown item
             * @return the text shown for it
             */
            // ternary: the dropdown may ask about a null item, which shows as blank
            @Override public String toString(InputHandler.Disc disc) { return disc == null ? "" : disc.label(); }

            /**
             * Required by StringConverter, but never used since the dropdown isn't editable.
             * @param text typed text
             * @return always null
             */
            @Override public InputHandler.Disc fromString(String text) { return null; } // not editable
        });

        GridPane settingsGrid = new GridPane();
        settingsGrid.setHgap(10);
        settingsGrid.setVgap(6);
        settingsGrid.addRow(0, new Label("Rows (M)"),    _rowsSpinner);
        settingsGrid.addRow(1, new Label("Columns (N)"), _colsSpinner);
        settingsGrid.addRow(2, sideLabel("Red", _redColor),       _redChoice,    new Label("d"), _redDepthSpinner);
        settingsGrid.addRow(3, sideLabel("Yellow", _yellowColor), _yellowChoice, new Label("d"), _yellowDepthSpinner);
        settingsGrid.addRow(4, new Label("First move"),  _firstMoveChoice);

        // before the first move, changes apply right away (no need to press New Game).
        // lambdas: (observable, oldValue, newValue) -> runs every time the control's value changes
        _rowsSpinner.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());
        _colsSpinner.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());
        _redChoice.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());
        _redDepthSpinner.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());
        _yellowChoice.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());
        _yellowDepthSpinner.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());
        _firstMoveChoice.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());

        Button newGame = new Button("New Game");
        // lambda: runs when the button is clicked
        newGame.setOnAction(event -> startNewGame());

        Label hint = new Label("Settings can be changed before the first move or after a game ends.");
        hint.setWrapText(true);
        hint.setFont(Font.font(11));

        _settingsBox = new VBox(8, settingsTitle, settingsGrid, newGame, hint);

        // log
        Label logTitle = new Label("Game Log");
        logTitle.setFont(Font.font("System", FontWeight.BOLD, 16));

        _logArea = new TextArea();
        _logArea.setEditable(false); // still selectable, so results can be copied out
        _logArea.setWrapText(true);
        _logArea.setFont(Font.font("Monospaced", 12));
        _logArea.setMinHeight(120);
        VBox.setVgrow(_logArea, Priority.ALWAYS); // the log takes up whatever height is left over

        VBox panel = new VBox(8, _settingsBox, new Separator(), logTitle, _logArea);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(LOG_PANEL_WIDTH);
        panel.setMinWidth(LOG_PANEL_WIDTH);

        return panel;
    }

    /**
     * Restarts with the new settings if no move has been made yet, so the board always matches the panel.
     * After a game ends, changes wait for Play Again / New Game so the result stays on screen.
     */
    private void onSettingChanged()
    {
        // _input is null while the panel is first being built (initial values also trigger listeners)
        if (_input != null && _input.isUnstarted())
        {
            startNewGame();
        }
    }

    /**
     * Creates an integer spinner with arrow buttons only (no typing, so values are always valid).
     * @param min the smallest allowed value
     * @param max the largest allowed value
     * @param initial the starting value
     * @return the spinner
     */
    private Spinner<Integer> makeSpinner(int min, int max, int initial)
    {
        Spinner<Integer> spinner = new Spinner<>(min, max, initial);
        spinner.setPrefWidth(80);
        return spinner;
    }

    /**
     * Creates a dropdown of "Human" plus every registered agent, linked to that side's depth spinner.
     * The depth spinner is disabled whenever "Human" is selected, since depth only applies to agents.
     * @param initial the starting choice
     * @param depthSpinner the depth spinner for the same side
     * @return the dropdown
     */
    private ChoiceBox<String> makeSideChoice(String initial, Spinner<Integer> depthSpinner)
    {
        ChoiceBox<String> choice = new ChoiceBox<>();

        choice.getItems().addAll(AgentRegistry.choices());
        choice.setPrefWidth(160);
        // lambda: whenever the choice changes, grey out the depth spinner if it's now "Human"
        choice.valueProperty().addListener((obs, oldValue, newValue) ->
            depthSpinner.setDisable(AgentRegistry.HUMAN.equals(newValue)));
        choice.setValue(initial);
        depthSpinner.setDisable(AgentRegistry.HUMAN.equals(initial));

        return choice;
    }

    /**
     * Creates a bold label in a side's disc color, for the settings panel.
     * @param text the label text
     * @param color the text color
     * @return the label
     */
    private Label sideLabel(String text, Color color)
    {
        Label label = new Label(text);
        label.setTextFill(color);
        label.setFont(Font.font("System", FontWeight.BOLD, 13));
        return label;
    }

    /**
     * Enables the settings controls only between rounds. Called by InputHandler whenever that may change.
     */
    public void refreshSettingsEnabled()
    {
        _settingsBox.setDisable(!_input.canChangeSettings());
    }

    /**
     * Shows a half-inserted disc in the current player's color above the hovered column,
     * only when a human can drop a disc there. Called on hover changes and by InputHandler on turn changes.
     */
    public void refreshHoverPreview()
    {
        boolean show = _hoveredCol >= 0
            && _input.canHumanMove()
            && !_input.isColumnFull(_hoveredCol);

        _previewDisc.setVisible(show);

        if (show)
        {
            // ternary: red disc on red's turn, otherwise yellow
            _previewDisc.setFill((_input.getTurn() == InputHandler.Disc.RED) ? _redColor : _yellowColor);
            _previewDisc.setTranslateX(_hoveredCol * (_tileSize + _colMargin) + _colMargin);
            _previewDisc.setTranslateY(_rowMargin - (_tileSize / 2.0)); // half above the top slot
        }
    }

    /**
     * Adds a line to the side log panel and prints it to the console
     * @param line the text to log
     */
    public void log(String line)
    {
        System.out.println(line);
        _logArea.appendText(line + "\n");
    }

    /**
     * Creates the (initially hidden) overlay shown when the game ends,
     * with the result and Play Again / Exit Game buttons.
     * @return the banner
     */
    private VBox makeGameOverBanner()
    {
        _resultLabel = new Label();
        _resultLabel.setFont(Font.font("System", FontWeight.BOLD, 40));
        _resultLabel.setTextFill(Color.WHITE);
        _resultLabel.setWrapText(true); // long agent names on small boards
        _resultLabel.setTextAlignment(TextAlignment.CENTER);

        _detailLabel = new Label(); // e.g. why a side forfeited
        _detailLabel.setTextFill(Color.LIGHTCORAL);
        _detailLabel.setWrapText(true);
        _detailLabel.setFont(Font.font(14));

        Button playAgain = new Button("Play Again");
        playAgain.setStyle("-fx-font-size: 16px;");
        // lambda: runs when clicked
        playAgain.setOnAction(event -> startNewGame()); // uses whatever is in the settings panel

        Button exit = new Button("Exit Game");
        exit.setStyle("-fx-font-size: 16px;");
        // lambda: runs when clicked; closes the window and ends the program
        exit.setOnAction(event -> Platform.exit());

        HBox buttons = new HBox(20, playAgain, exit);
        buttons.setAlignment(Pos.CENTER);

        Label settingsHint = new Label("Change players, depth, or board size in the side panel before playing again.");
        settingsHint.setTextFill(Color.LIGHTGRAY);
        settingsHint.setWrapText(true);

        _gameOverBanner = new VBox(25, _resultLabel, _detailLabel, buttons, settingsHint);
        _gameOverBanner.setAlignment(Pos.CENTER);
        _gameOverBanner.setPadding(new Insets(20));
        _gameOverBanner.setPrefSize((_cols + 1) * _tileSize, (_rows + 1) * _tileSize);
        // 65% opaque black, so the final board still shows through
        _gameOverBanner.setBackground(new Background(
            new BackgroundFill(Color.rgb(0, 0, 0, 0.65), CornerRadii.EMPTY, Insets.EMPTY)));
        _gameOverBanner.setVisible(false);

        return _gameOverBanner;
    }

    /**
     * Shows the game over banner
     * @param headline the result, e.g. "You win!"
     * @param detail extra explanation (e.g. a forfeit reason), or null for none
     */
    public void showGameOver(String headline, String detail)
    {
        _resultLabel.setText(headline);
        // ternary: a label can't show null, so use empty text when there's no detail
        _detailLabel.setText(detail == null ? "" : detail);
        _detailLabel.setVisible(detail != null);
        _detailLabel.setManaged(detail != null); // takes no space when hidden

        _gameOverBanner.setVisible(true);
    }

    /**
     * Creates the connect four grid by first drawing a rectangle, then ripping circular holes in it.
     * @return the grid
     */
    private Shape makeGrid()
    {
        // make a background rectangle that is larger than the space needed for the rows/cols
        Shape grid = new Rectangle((_cols + 1) * _tileSize, (_rows + 1) * _tileSize);

        // Rip holes in the shape we just created to make the circle slots: one per row, per column
        for (int row = 0; row < _rows; row++)
        {
            for (int col = 0; col < _cols; col++)
            {
                Circle circle = new Circle((double) _tileSize / 2);

                // JavaFX by default does not draw the center of the circle at the top left like it does everything else,
                // so to make things easier in the future, we are moving the center of the circle.
                circle.setCenterX((double) _tileSize / 2);
                circle.setCenterY((double) _tileSize / 2);

                // create margin around circle
                circle.setTranslateX(col * (_tileSize + _colMargin) + _colMargin);
                circle.setTranslateY(row * (_tileSize + _rowMargin) + _rowMargin);

                // remove circle from rectangle to make Connect Four grid
                grid = Shape.subtract(grid, circle);
            }
        }

        grid.setFill(_gridColor);

        return grid;
    }

    /**
     * Creates one invisible, full-height rectangle per column that handles the mouse:
     * hovering highlights the column and shows the preview disc, clicking drops a disc.
     * @return the column rectangles, left to right
     */
    private List<Rectangle> makeColumns()
    {
        List<Rectangle> colList = new ArrayList<>();

        // one clickable rectangle per column
        for (int col = 0; col < _cols; col++)
        {
            final int column = col; // for disc placement by player (lambdas below need a final copy)
            Rectangle rect   = new Rectangle((_tileSize), ((_rows + 1) * _tileSize));

            rect.setTranslateX(col * (_tileSize + _colMargin) + _colMargin);

            // col color animation
            rect.setFill(Color.TRANSPARENT);
            // lambda: runs when the mouse moves onto this column
            rect.setOnMouseEntered(event -> {
                rect.setFill(_colHighlight);
                rect.setOpacity(0.5);
                _hoveredCol = column;
                refreshHoverPreview();
            });
            // lambda: runs when the mouse leaves this column
            rect.setOnMouseExited(event -> {
                rect.setFill(Color.TRANSPARENT);
                // only clear if another column hasn't already claimed the hover
                if (_hoveredCol == column)
                {
                    _hoveredCol = -1;
                }
                refreshHoverPreview();
            });

            // lambda: runs when this column is clicked
            rect.setOnMouseClicked(event -> {
                if (_input.canHumanMove())
                {
                    // the move was rejected (column full): flash the column red
                    if (!_input.humanMove(column))
                    {
                        FillTransition flash = new FillTransition(Duration.seconds(0.5), rect);

                        flash.setFromValue(Color.TRANSPARENT);
                        flash.setToValue(_errorColor);
                        flash.setAutoReverse(true); // fade back out after fading in
                        flash.setCycleCount(2);     // one fade in + one fade out

                        flash.play();
                    }
                }
            });

            colList.add(rect);
        }

        return colList;
    }

    /**
     * Creates the icon for the window: a circle split diagonally into the two disc colors.
     * @param iconSize the size in pixels of the icon to create
     * @return icon as an image
     */
    private WritableImage makeIcon(int iconSize)
    {
        WritableImage icon         = new WritableImage(iconSize, iconSize);
        PixelWriter   pixelWriter = icon.getPixelWriter();

        double center = (iconSize - 1) / 2.0;
        double radius = iconSize / 2.0;

        // color every pixel of the icon, one row (y) at a time
        for (int y = 0; y < iconSize; y++)
        {
            for (int x = 0; x < iconSize; x++)
            {
                double dx = x - center;
                double dy = y - center;
                double dist = Math.sqrt((dx * dx) + (dy * dy)); // distance from the icon's center

                if (dist <= radius)
                {
                    // Smooth 1px edge
                    double opacity = Math.min(1.0, radius - dist + 0.5);
                    // ternary: pixels above the diagonal from bottom-left to top-right are red, below are the other color
                    Color  color   = ((x + y) < iconSize) ? _redColor : _yellowColor;

                    pixelWriter.setColor(x, y, new Color(color.getRed(), color.getGreen(), color.getBlue(), opacity));
                }
                else
                {
                    pixelWriter.setColor(x, y, Color.TRANSPARENT);
                }
            }
        }
        return icon;
    }

    /**
     * Creates a disc in the specified location
     * @param row the row coordinate
     * @param col the col coordinate
     * @param side which side's disc it is
     * @param onLanded runs once the drop animation finishes
     */
    public void makeDisc(int row, int col, InputHandler.Disc side, Runnable onLanded)
    {
        DiscShape disc = new DiscShape(side);

        disc.setTranslateX(col * (_tileSize + _colMargin) + _colMargin);

        _discRoot.getChildren().add(disc);

        // slide the disc from the top of the board down to its row
        TranslateTransition drop = new TranslateTransition(Duration.seconds(0.5), disc);

        drop.setToY(row * (_tileSize + _rowMargin) + _rowMargin);
        // lambda: runs when the animation ends
        drop.setOnFinished(event -> onLanded.run());

        drop.play();
    }

    /**
     * The drawn circle for a disc, colored by side
     */
    private class DiscShape extends Circle
    {
        /**
         * Creates a disc to show who went where
         * @param side which side's disc it is
         */
        protected DiscShape(InputHandler.Disc side)
        {
            super((_tileSize / 2.0));

            // ternary: red for RED, yellow for YELLOW
            setFill((side == InputHandler.Disc.RED) ? _redColor : _yellowColor);
            setCenterX(_tileSize / 2.0);
            setCenterY(_tileSize / 2.0);
        }
    }
}
