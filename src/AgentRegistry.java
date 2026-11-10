import OpponentAI.OpponentHarness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * The agents offered in the Game Settings panel's Red/Yellow dropdowns.
 * TO ADD AN AGENT: add one register(...) line in the static block below.
 */
public final class AgentRegistry
{
    public static final String HUMAN = "Human";

    // dropdown label -> creates the agent for a given search depth (kept in insertion order).
    // IntFunction<Agent> is a function that takes an int (the depth) and returns an Agent.
    private static final Map<String, IntFunction<Agent>> AGENTS = new LinkedHashMap<>();

    // static block: runs once, when the class is first used
    static
    {
        // AkivaAgent::new is shorthand for depth -> new AkivaAgent(depth)
        register("Akiva's Agent",    AkivaAgent::new);
        register("Julia's Agent",    JuliaAgent::new);
        // lambda: wrap a new OpponentHarness so it can be used as an Agent
        register("Opponent Harness", depth -> new HarnessAgent(new OpponentHarness(depth)));
    }

    /**
     * Not meant to be created; everything here is static.
     */
    private AgentRegistry() { }

    /**
     * Adds an agent to the dropdowns.
     * @param label the name shown in the dropdown
     * @param factory creates the agent for a given search depth
     */
    private static void register(String label, IntFunction<Agent> factory)
    {
        AGENTS.put(label, factory);
    }

    /**
     * @return every dropdown choice: "Human" first, then the registered agents
     */
    public static List<String> choices()
    {
        List<String> choices = new ArrayList<>();

        choices.add(HUMAN);
        choices.addAll(AGENTS.keySet());

        return choices;
    }

    /**
     * @return the label of the first registered agent (used as a default)
     */
    public static String firstAgent()
    {
        return AGENTS.keySet().iterator().next();
    }

    /**
     * Creates the agent for a dropdown choice.
     * @param label a dropdown choice
     * @param depth the search depth for the agent
     * @return a new agent, or null if the choice is "Human"
     */
    public static Agent create(String label, int depth)
    {
        if (HUMAN.equals(label))
        {
            return null;
        }

        IntFunction<Agent> factory = AGENTS.get(label);

        if (factory == null)
        {
            throw new IllegalArgumentException("AgentRegistry.create: unknown agent " + label);
        }

        return factory.apply(depth); // calls the function registered above, e.g. new AkivaAgent(depth)
    }

    /**
     * Adapts OpponentHarness (which uses a plain int[][] board, since it lives in its own package)
     * to the Agent interface.
     */
    private static class HarnessAgent implements Agent
    {
        private final OpponentHarness _harness;

        /**
         * @param harness the harness to forward moves to
         */
        HarnessAgent(OpponentHarness harness)
        {
            this._harness = harness;
        }

        /**
         * @return the harness's name
         */
        @Override
        public String getName()
        {
            return _harness.getName();
        }

        /**
         * Converts the board to the harness's int format and asks it for a column.
         * @param board a copy of the current board
         * @param me the color the harness is playing
         * @return the harness's chosen column
         */
        @Override
        public int chooseColumn(InputHandler.Disc[][] board, InputHandler.Disc me)
        {
            return _harness.chooseColumn(InputHandler.toIntBoard(board, me));
        }
    }
}
