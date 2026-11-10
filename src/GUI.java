import javafx.animation.FillTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import javafx.scene.Parent;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

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
    // sizing
    private static int   _tileSize  = 80;
    private static int   _rows      = 6;
    private static int   _cols      = 7;

    // spacing
    private static double _colMargin = (double) _tileSize / (_cols + 1);
    private static double _rowMargin = (double) _tileSize / (_rows + 1);

    // colors: check out Oracle's JavaFX documentation for options
    private static Color _gridColor    = Color.MIDNIGHTBLUE;
    private static Color _playerColor  = Color.FIREBRICK;
    private static Color _AIColor      = Color.ORANGE;
    private static Color _colHighlight = Color.LIGHTYELLOW;
    private static Color _errorColor   = Color.RED;

    // game settings
    private static int     _aiDepth      = 4;    // search depth handed to the MinimaxAgent
    private static boolean _playerStarts = true; // false to let the AI move first

    // display
    private Pane  _discRoot = new Pane();
    private VBox  _gameOverBanner;
    private Label _resultLabel;
    private TextArea _logArea;

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
        _input = new InputHandler(_playerStarts, _rows, _cols, this, new MinimaxAgent(_aiDepth));

        stage.setTitle("Connect Four with Minimax & Alpha-Beta Pruning");
        stage.setScene(new Scene(createContent()));
        stage.getIcons().addAll(makeIcon(16), makeIcon(32), makeIcon(64));
        stage.setResizable(false);

        stage.show();

        _input.startGame();
    }

    /**
     * Creates the content for the stage
     */
    private Parent createContent()
    {
        Pane root  = new Pane();
        Shape grid = makeGrid();

        root.getChildren().add(_discRoot);
        root.getChildren().add(grid);
        root.getChildren().addAll(makeColumns());
        root.getChildren().add(makeGameOverBanner()); // added last so it sits on top of everything

        return new HBox(root, makeLogPanel());
    }

    /**
     * Creates the side panel that logs AI moves and response times as the game is played
     * @return the panel
     */
    private VBox makeLogPanel()
    {
        Label title = new Label("AI Response Times");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));

        _logArea = new TextArea();
        _logArea.setEditable(false); // still selectable, so results can be copied out
        _logArea.setWrapText(true);
        _logArea.setFont(Font.font("Monospaced", 13));
        VBox.setVgrow(_logArea, Priority.ALWAYS);

        VBox panel = new VBox(8, title, _logArea);
        panel.setPadding(new Insets(10));
        panel.setPrefSize(300, (_rows + 1) * _tileSize);

        return panel;
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

        Button playAgain = new Button("Play Again");
        playAgain.setStyle("-fx-font-size: 16px;");
        playAgain.setOnAction(event -> {
            _gameOverBanner.setVisible(false);
            _discRoot.getChildren().clear();
            _input.startGame();
        });

        Button exit = new Button("Exit Game");
        exit.setStyle("-fx-font-size: 16px;");
        exit.setOnAction(event -> Platform.exit());

        HBox buttons = new HBox(20, playAgain, exit);
        buttons.setAlignment(Pos.CENTER);

        _gameOverBanner = new VBox(25, _resultLabel, buttons);
        _gameOverBanner.setAlignment(Pos.CENTER);
        _gameOverBanner.setPrefSize((_cols + 1) * _tileSize, (_rows + 1) * _tileSize);
        _gameOverBanner.setBackground(new Background(
            new BackgroundFill(Color.rgb(0, 0, 0, 0.65), CornerRadii.EMPTY, Insets.EMPTY)));
        _gameOverBanner.setVisible(false);

        return _gameOverBanner;
    }

    /**
     * Shows the game over banner
     * @param winner who won, or null for a draw
     */
    public void showGameOver(InputHandler.Disc winner)
    {
        if (winner == InputHandler.Disc.PLAYER)
        {
            _resultLabel.setText("You win!");
        }
        else if (winner == InputHandler.Disc.AI)
        {
            _resultLabel.setText("The AI wins!");
        }
        else
        {
            _resultLabel.setText("It's a draw!");
        }

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
                if (_input.canPlayerMove())
                {
                    if (!_input.placeDisc(column))
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
                    Color  color   = ((x + y) < iconSize) ? _playerColor : _AIColor;

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
     * @param isPlayerDisc true if the disc is a player disc, false otherwise
     * @param onLanded runs once the drop animation finishes
     */
    public void makeDisc(int row, int col, boolean isPlayerDisc, Runnable onLanded)
    {
        Disc disc = new Disc(isPlayerDisc);

        disc.setTranslateX(col * (_tileSize + _colMargin) + _colMargin);

        _discRoot.getChildren().add(disc);

        TranslateTransition drop = new TranslateTransition(Duration.seconds(0.5), disc);

        drop.setToY(row * (_tileSize + _rowMargin) + _rowMargin);
        drop.setOnFinished(event -> onLanded.run());

        drop.play();
    }

    /**
     * A representation of the connect four discs that indicate the player
     */
    private class Disc extends Circle
    {
        private boolean _isPlayerDisc;

        /**
         * Creates a disk to show who went where
         * @param isPlayerDisc true if player's disc, false if AI's disc
         */
        protected Disc (boolean isPlayerDisc)
        {
            super((_tileSize / 2.0));

            this._isPlayerDisc = isPlayerDisc;

            setFill(isPlayerDisc ? _playerColor : _AIColor);
            setCenterX(_tileSize / 2.0);
            setCenterY(_tileSize / 2.0);
        }

        /**
         * @return true if the disc belongs to the player, false otherwise
         */
        public boolean isPlayerDisc()
        {
            return this._isPlayerDisc;
        }
    }
}
