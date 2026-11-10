import javafx.animation.FillTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.scene.Scene;
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

    // display
    private Pane _discRoot = new Pane();

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
        _input = new InputHandler(true, _rows, _cols, this);

        stage.setTitle("Connect Four with Minimax & Alpha-Beta Pruning");
        stage.setScene(new Scene(createContent()));
        stage.getIcons().addAll(makeIcon(16), makeIcon(32), makeIcon(64));
        stage.setResizable(false);

        stage.show();
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

        return root;
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
                if (_input.IsPlayerTurn())
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
     */
    public void makeDisc(int row, int col, boolean isPlayerDisc)
    {
        Disc disc = new Disc(isPlayerDisc);

        disc.setTranslateX(col * (_tileSize + _colMargin) + _colMargin);

        _discRoot.getChildren().add(disc);

        TranslateTransition drop = new TranslateTransition(Duration.seconds(0.5), disc);

        drop.setToY(row * (_tileSize + _rowMargin) + _rowMargin);

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
