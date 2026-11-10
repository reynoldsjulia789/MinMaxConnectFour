/**
 * Following Model, View, Controller pattern.
 * InputHandler is the controller that talks to view and model.
 */
public class InputHandler
{
    public enum Disc { PLAYER, AI }

    private int _rows;
    private int _cols;

    private boolean  _isPlayerTurn;
    private Disc[][] _grid;
    private GUI      _view;

    /**
     * Initializes the input handler
     * @param isPlayerTurn boolean determining whether to start with the player or AI turn
     * @param rows the number of rows
     * @param cols the number of columns
     */
    public InputHandler(boolean isPlayerTurn, int rows, int cols, GUI view)
    {
        if (rows < 0 || cols < 0)
        {
            throw new IllegalArgumentException("InputHandler Constructor: rows/cols can't be negative");
        }

        this._isPlayerTurn = isPlayerTurn;
        this._grid         = new Disc[rows][cols];
        this._rows         = rows;
        this._cols         = cols;
        this._view         = view;
    }

    /**
     * Finds the location row in which to place the disc in the given column, accounting for gravity.
     * @param col the column to place the disc in
     * @return true if the disc was successfully placed, false if the disc could not be placed
     */
    public boolean placeDisc(int col)
    {
        int row;

        for (row = (_rows - 1); row >= 0; row--)
        {
            if (getDisc(row, col) == null)
            {
                _grid[row][col] = (_isPlayerTurn) ? Disc.PLAYER : Disc.AI;

                _view.makeDisc(row, col, _isPlayerTurn);

                // switch turns, trigger AI if AI's turn

                return true;
            }
        }

        return false;
    }

    /**
     * @return true if it is the player's turn, false otherwise
     */
    public boolean IsPlayerTurn()
    {
        return this._isPlayerTurn;
    }

    /**
     * Determines whether the specified location is occupied
     * @param row the row coordinate
     * @param col the column coordinate
     * @return if the slot was selected by the player, AI, or null if it's empty
     */
    public Disc getDisc(int row, int col)
    {
        if (row < 0
            || row >= _rows
            || col < 0
            || col >= _cols)
        {
            throw new IllegalArgumentException("InputHandler.getDisc: row or col outside range");
        }

        return _grid[row][col];
    }
}
