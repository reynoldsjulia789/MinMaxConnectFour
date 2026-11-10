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

import java.util.ArrayList;
import java.util.List;

/**
 * GUI created in part by following this tutorial:
 * JavaFX Game: Connect4 by Almas Biam,
 * Timestamps: 0:50 - 7:00, 8:20 - ,
 * URL: https://youtu.be/B5H_t0A_C14?si=PBOI7srskGaINjvI
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
            Rectangle rect = new Rectangle((_tileSize), ((_rows + 1) * _tileSize));

            rect.setTranslateX(col * (_tileSize + _colMargin) + _colMargin);

            // col color animation
            rect.setFill(Color.TRANSPARENT);
            rect.setOnMouseEntered(event -> {
                rect.setFill(_colHighlight);
                rect.setOpacity(0.5);
            });
            rect.setOnMouseExited(event -> rect.setFill(Color.TRANSPARENT));

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
}
