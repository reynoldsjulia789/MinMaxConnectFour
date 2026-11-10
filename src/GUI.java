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
 * GUI handles display(view)
 */
public class GUI extends Application
{
    // default settings (changeable in the window between rounds)
    private static final int     DEFAULT_ROWS          = 6;
    private static final int     DEFAULT_COLS          = 7;
    private static final int     DEFAULT_DEPTH         = 4;
    private static final String  DEFAULT_RED           = AgentRegistry.HUMAN;
    private static final String  DEFAULT_ORANGE        = AgentRegistry.firstAgent();
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
    private static final Color _orangeColor  = Color.ORANGE;
    private static final Color _colHighlight = Color.LIGHTYELLOW;
    private static final Color _errorColor   = Color.RED;

    // current board geometry (set by applyBoardSize)
    private int    _rows;
    private int    _cols;
    private int    _tileSize;
    private double _colMargin;
    private double _rowMargin;

    // display
    private Stage    _stage;
    private HBox     _root;
    private Pane     _discRoot;
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
    private ChoiceBox<String>   _orangeChoice;
    private Spinner<Integer>    _orangeDepthSpinner;
    private ChoiceBox<InputHandler.Disc> _firstMoveChoice;

    // mechanics
    private InputHandler _input;

    /**
     * Main method
     * @param args Args
     */
    public static void main(String[] args)
    {
        launch(args);
    }

    /**
     * Sets the stage
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
     */
    private void startNewGame()
    {
        int rows = _rowsSpinner.getValue();
        int cols = _colsSpinner.getValue();

        InputHandler.Side red    = makeSide(_redChoice.getValue(),    _redDepthSpinner.getValue());
        InputHandler.Side orange = makeSide(_orangeChoice.getValue(), _orangeDepthSpinner.getValue());

        applyBoardSize(rows, cols);

        _root.getChildren().set(0, makeBoard());
        _sidePanel.setPrefHeight((_rows + 1) * _tileSize);
        _stage.sizeToScene();

        _input.startGame(rows, cols, red, orange, _firstMoveChoice.getValue());
    }

    /**
     * @param choice a dropdown choice from AgentRegistry.choices()
     * @param depth the search depth for an agent (ignored for a human)
     * @return the side description for InputHandler
     */
    private InputHandler.Side makeSide(String choice, int depth)
    {
        Agent agent = AgentRegistry.create(choice, depth);

        return (agent == null) ? InputHandler.Side.human() : new InputHandler.Side(agent, depth);
    }

    /**
     * Sets the board dimensions and scales the tiles so the board fits on screen.
     */
    private void applyBoardSize(int rows, int cols)
    {
        _rows      = rows;
        _cols      = cols;
        _tileSize  = Math.min(MAX_TILE_SIZE,
                     Math.min(MAX_BOARD_HEIGHT / (_rows + 1), MAX_BOARD_WIDTH / (_cols + 1)));
        _colMargin = (double) _tileSize / (_cols + 1);
        _rowMargin = (double) _tileSize / (_rows + 1);
    }

    /**
     * Creates the board area: discs, grid, clickable columns, and the game over banner on top.
     * @return the board
     */
    private Pane makeBoard()
    {
        Pane board = new Pane();

        _discRoot = new Pane();

        board.getChildren().add(_discRoot);
        board.getChildren().add(makeGrid());
        board.getChildren().addAll(makeColumns());
        board.getChildren().add(makeGameOverBanner()); // added last so it sits on top of everything

        return board;
    }

    /**
     * Creates the side panel: game settings on top, AI response time log below.
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
        _orangeDepthSpinner = makeSpinner(MIN_DEPTH, MAX_DEPTH, DEFAULT_DEPTH);
        _orangeChoice       = makeSideChoice(DEFAULT_ORANGE, _orangeDepthSpinner);

        _firstMoveChoice = new ChoiceBox<>();
        _firstMoveChoice.getItems().addAll(InputHandler.Disc.values());
        _firstMoveChoice.setValue(DEFAULT_FIRST);
        _firstMoveChoice.setConverter(new StringConverter<>()
        {
            @Override public String toString(InputHandler.Disc disc) { return disc == null ? "" : disc.label(); }
            @Override public InputHandler.Disc fromString(String text) { return null; } // not editable
        });

        GridPane settingsGrid = new GridPane();
        settingsGrid.setHgap(10);
        settingsGrid.setVgap(6);
        settingsGrid.addRow(0, new Label("Rows (M)"),    _rowsSpinner);
        settingsGrid.addRow(1, new Label("Columns (N)"), _colsSpinner);
        settingsGrid.addRow(2, sideLabel("Red", _redColor),       _redChoice,    new Label("d"), _redDepthSpinner);
        settingsGrid.addRow(3, sideLabel("Yellow", _orangeColor), _orangeChoice, new Label("d"), _orangeDepthSpinner);
        settingsGrid.addRow(4, new Label("First move"),  _firstMoveChoice);

        // before the first move, changes apply right away (no need to press New Game)
        _rowsSpinner.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());
        _colsSpinner.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());
        _redChoice.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());
        _redDepthSpinner.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());
        _orangeChoice.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());
        _orangeDepthSpinner.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());
        _firstMoveChoice.valueProperty().addListener((obs, oldValue, newValue) -> onSettingChanged());

        Button newGame = new Button("New Game");
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
        VBox.setVgrow(_logArea, Priority.ALWAYS);

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
        if (_input != null && _input.isUnstarted())
        {
            startNewGame();
        }
    }

    /**
     * @return an integer spinner with arrow buttons only (no typing, so values are always valid)
     */
    private Spinner<Integer> makeSpinner(int min, int max, int initial)
    {
        Spinner<Integer> spinner = new Spinner<>(min, max, initial);
        spinner.setPrefWidth(80);
        return spinner;
    }

    /**
     * @return a dropdown of "Human" plus every registered agent; the depth spinner is disabled for Human
     */
    private ChoiceBox<String> makeSideChoice(String initial, Spinner<Integer> depthSpinner)
    {
        ChoiceBox<String> choice = new ChoiceBox<>();

        choice.getItems().addAll(AgentRegistry.choices());
        choice.setPrefWidth(160);
        choice.valueProperty().addListener((obs, oldValue, newValue) ->
            depthSpinner.setDisable(AgentRegistry.HUMAN.equals(newValue)));
        choice.setValue(initial);
        depthSpinner.setDisable(AgentRegistry.HUMAN.equals(initial));

        return choice;
    }

    /**
     * @return a bold label in the side's disc color
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
        playAgain.setOnAction(event -> startNewGame()); // uses whatever is in the settings panel

        Button exit = new Button("Exit Game");
        exit.setStyle("-fx-font-size: 16px;");
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

        // Rip holes in the shape we just created to make the circle slots
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

    private List<Rectangle> makeColumns()
    {
        List<Rectangle> colList = new ArrayList<>();

        for (int col = 0; col < _cols; col++)
        {
            final int column = col; // for disc placement by player
            Rectangle rect   = new Rectangle((_tileSize), ((_rows + 1) * _tileSize));

            rect.setTranslateX(col * (_tileSize + _colMargin) + _colMargin);

            // col color animation
            rect.setFill(Color.TRANSPARENT);
            rect.setOnMouseEntered(event -> {
                rect.setFill(_colHighlight);
                rect.setOpacity(0.5);
            });
            rect.setOnMouseExited(event -> rect.setFill(Color.TRANSPARENT));

            rect.setOnMouseClicked(event -> {
                if (_input.canHumanMove())
                {
                    if (!_input.humanMove(column))
                    {
                        FillTransition flash = new FillTransition(Duration.seconds(0.5), rect);

                        flash.setFromValue(Color.TRANSPARENT);
                        flash.setToValue(_errorColor);
                        flash.setAutoReverse(true);
                        flash.setCycleCount(2);

                        flash.play();
                    }
                }
            });

            colList.add(rect);
        }

        return colList;
    }

    /**
     * Creates the icon for the window
     * @param iconSize the size in pixels of the icon to create
     * @return icon as an image
     */
    private WritableImage makeIcon(int iconSize)
    {
        WritableImage icon         = new WritableImage(iconSize, iconSize);
        PixelWriter   pixelWriter = icon.getPixelWriter();

        double center = (iconSize - 1) / 2.0;
        double radius = iconSize / 2.0;

        for (int y = 0; y < iconSize; y++)
        {
            for (int x = 0; x < iconSize; x++)
            {
                double dx = x - center;
                double dy = y - center;
                double dist = Math.sqrt((dx * dx) + (dy * dy));

                if (dist <= radius)
                {
                    // Smooth 1px edge
                    double opacity = Math.min(1.0, radius - dist + 0.5);
                    Color  color   = ((x + y) < iconSize) ? _redColor : _orangeColor;

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

        TranslateTransition drop = new TranslateTransition(Duration.seconds(0.5), disc);

        drop.setToY(row * (_tileSize + _rowMargin) + _rowMargin);
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

            setFill((side == InputHandler.Disc.RED) ? _redColor : _orangeColor);
            setCenterX(_tileSize / 2.0);
            setCenterY(_tileSize / 2.0);
        }
    }
}
