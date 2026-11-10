import OpponentAI.OpponentHarness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * The agents offered in the Game Settings panel's Red/Orange dropdowns.
 * TO ADD AN AGENT: add one register(...) line in the static block below.
 */
public final class AgentRegistry
{
    public static final String HUMAN = "Human";

    // dropdown label -> creates the agent for a given search depth (kept in insertion order)
    private static final Map<String, IntFunction<Agent>> AGENTS = new LinkedHashMap<>();

    static
    {
        register("Akiva's Agent",    AkivaAgent::new);
        register("Julia's Agent",    JuliaAgent::new);
        register("Opponent Harness", depth -> new HarnessAgent(new OpponentHarness(depth)));
    }

    private AgentRegistry() { }

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

        return factory.apply(depth);
    }

    /**
     * Adapts OpponentHarness (which uses a plain int[][] board, since it lives in its own package)
     * to the Agent interface.
     */
    private static class HarnessAgent implements Agent
    {
        private final OpponentHarness _harness;

        HarnessAgent(OpponentHarness harness)
        {
            this._harness = harness;
        }

        @Override
        public String getName()
        {
            return _harness.getName();
        }

        @Override
        public int chooseColumn(InputHandler.Disc[][] board, InputHandler.Disc me)
        {
            return _harness.chooseColumn(InputHandler.toIntBoard(board, me));
        }
    }
}
