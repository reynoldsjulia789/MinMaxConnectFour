/**
 * Program entry point.
 * .
 * JavaFX refuses to start when the main class itself extends Application (GUI) and JavaFX is loaded
 * from the classpath, so this separate class with a plain main() starts the program instead.
 */
public class Launcher
{
    /**
     * Starts the game window.
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args)
    {
        GUI.main(args);
    }
}
