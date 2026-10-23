import aima.search.framework.Successor;
import aima.search.framework.SuccessorFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Successor function for Simulated Annealing: generates a single random successor
public class P1SuccessorFunctionSA implements SuccessorFunction {

    // Maximum number of random attempts before giving up and returning the state unchanged
    private static final int MAX_ATTEMPTS = 100000;

    // Which operators are used to generate successors
    private boolean useMove;
    private boolean useSwap;
    private boolean useEmptyCentral;

    private Random rand;

    public P1SuccessorFunctionSA(boolean useMove, boolean useSwap, boolean useEmptyCentral, long seed) {
        this.useMove = useMove;
        this.useSwap = useSwap;
        this.useEmptyCentral = useEmptyCentral;
        this.rand = new Random(seed);
    }

    public List getSuccessors(Object state) {
        P1Board board = (P1Board) state;
        List successors = new ArrayList();
        int nClientes = board.getNClientes();
        int nCentrales = board.getNCentrales();

        // Enabled operators: 0 = move, 1 = swap, 2 = emptyCentral
        int[] operators = new int[3];
        int nOperators = 0;
        if (useMove) {
            operators[nOperators++] = 0;
        }
        if (useSwap) {
            operators[nOperators++] = 1;
        }
        if (useEmptyCentral) {
            operators[nOperators++] = 2;
        }

        try {
            // If an operator fails the board remains unchanged, so the same copy is reused between attempts
            P1Board next = new P1Board(board);

            for (int attempt = 0; attempt < MAX_ATTEMPTS && nOperators > 0; ++attempt) {
                int op = operators[rand.nextInt(nOperators)];

                if (op == 0) {
                    int cli = rand.nextInt(nClientes);
                    int cen = rand.nextInt(nCentrales + 1) - 1;   // if cen == -1 we unnasign the Cliente
                    if (next.move(cli, cen)) {
                        successors.add(new Successor("move " + cli + " -> " + cen, next));
                        return successors;
                    }
                }
                else if (op == 1) {
                    int cli1 = rand.nextInt(nClientes);
                    int cli2 = rand.nextInt(nClientes);
                    if (next.swap(cli1, cli2)) {
                        successors.add(new Successor("swap " + cli1 + " <-> " + cli2, next));
                        return successors;
                    }
                }
                else {
                    int cen = rand.nextInt(nCentrales);
                    if (next.emptyCentral(cen)) {
                        successors.add(new Successor("empty " + cen, next));
                        return successors;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // No valid successor found, returns the same state so the search can continue
        successors.add(new Successor("no change", new P1Board(board)));
        return successors;
    }
}