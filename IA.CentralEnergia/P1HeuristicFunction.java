import aima.search.framework.HeuristicFunction;

public class P1HeuristicFunction implements HeuristicFunction {

    // Heuristic function to use (1-3)
    private int heuristic;

    public P1HeuristicFunction(int h) {
        heuristic = h;
    }

    public double getHeuristicValue(Object state) {
        P1Board board = (P1Board) state;
        try {
            if (heuristic == 1) {
                return board.getHeuristic1();
            }
            if (heuristic == 2) {
                return board.getHeuristic2();
            }
            if (heuristic == 3) {
                return board.getHeuristic3();
            }
            if (heuristic == 4) {
                return board.getHeuristic4();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new IllegalArgumentException("Heuristic " + heuristic + " does not exist");
    }
}