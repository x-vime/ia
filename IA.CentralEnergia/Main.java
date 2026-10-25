import aima.search.framework.GoalTest;
import aima.search.framework.HeuristicFunction;
import aima.search.framework.Problem;
import aima.search.framework.Search;
import aima.search.framework.SearchAgent;
import aima.search.framework.SuccessorFunction;
import aima.search.informed.HillClimbingSearch;
import aima.search.informed.SimulatedAnnealingSearch;

// Runs the experiments of the practica and prints the results as CSV (one line per run).
//
// Usage:  java Main <experiment> [repetitions]
//   experiment: 0 (single test run), 1, 2, 3, 4a, 4b, 5 or 6
//   repetitions: optional, default REPS
//
// Example: java Main 1 > exp1.csv
public class Main {

    // ---------- BEGIN: CONFIGURATION ----------

    // Repetitions of each configuration (the scenario seed goes from 1 to REPS)
    private static int REPS = 10;

    // Base scenario
    private static final int N_CLIENTES = 1000;
    private static final double[] PROP_CLIENTES = {0.25, 0.30, 0.45};   // XG, MG, G
    private static final double PROP_GARANTIZADOS = 0.75;
    private static final int[] CENTRALES = {5, 10, 25};                 // A, B, C

    // Initial solution strategies
    private static final int INIT_EMPTY = 0;
    private static final int INIT_BASIC = 1;
    private static final int INIT_NEAREST = 2;
    private static final int INIT_RANDOM = 3;
    private static final int INIT_GARANTIZADOS = 4;
    private static final int INIT_CHEAPEST = 5;
    private static final String[] INIT_NAMES = {"empty", "basic", "nearest", "random", "garantizados", "cheapest"};

    // Probability used by initialAssigRandom to try to assign a Cliente no garantizado
    private static final double RANDOM_PROB_ASSIGN = 0.5;

    // Operator sets: {move, swap, emptyCentral}
    private static final boolean[][] OPERATOR_SETS = {
        {true, false, false},
        {true, false, true},
        {true, true, false},
        {true, true, true}
    };

    // Choices fixed by the previous experiments. Update them with your results:
    //   HEURISTIC: heuristic used in experiments 1-4 and 6
    //   INIT_EXP1: initial solution used in experiment 1
    //   CHOSEN_OPERATORS: result of experiment 1 (index in OPERATOR_SETS)
    //   CHOSEN_INIT: result of experiment 2
    private static final int HEURISTIC = 2;
    private static final int INIT_EXP1 = INIT_BASIC;
    private static final int CHOSEN_OPERATORS = 2;
    private static final int CHOSEN_INIT = INIT_NEAREST;

    // Heuristic weights
    private static final double LOSS_WEIGHT = 1.0;
    private static final double WASTE_WEIGHT = 1.0;

    // Simulated Annealing parameters used in experiments 5 and 6 (result of experiment 3)
    private static final int SA_STEPS = 200000;
    private static final int SA_STITER = 100;
    private static final int SA_K = 5;
    private static final double SA_LAMBDA = 0.001;

    // Experiment 3: values of the Simulated Annealing parameters to try
    private static final int[] EXP3_STEPS = {200000};
    private static final int[] EXP3_STITER = {100};
    private static final int[] EXP3_K = {1, 5, 25, 125};
    private static final double[] EXP3_LAMBDA = {1, 0.01, 0.0001};

    // Experiment 4: number of increments
    private static final int EXP4_LEVELS = 6;

    // Experiment 5: penalties (euros per MW of unserved Clientes garantizados)
    private static final double[] EXP5_PENALTIES = {0, 100, 1000, 10000, 100000};

    // Experiment 6: Centrales of each type
    private static final int[][] EXP6_CENTRALES = {{5, 10, 25}, {5, 10, 50}, {5, 10, 75}};

    // ---------- END: CONFIGURATION ----------


    public static void main(String[] args) throws Exception {
        String experiment = "0";
        if (args.length > 0) {
            experiment = args[0];
        }
        if (args.length > 1) {
            REPS = Integer.parseInt(args[1]);
        }

        P1Board.setLossWeight(LOSS_WEIGHT);
        P1Board.setWasteWeight(WASTE_WEIGHT);

        printHeader();

        if (experiment.equals("0")) {
            singleRun();
        }
        else if (experiment.equals("1")) {
            experiment1();
        }
        else if (experiment.equals("2")) {
            experiment2();
        }
        else if (experiment.equals("3")) {
            experiment3();
        }
        else if (experiment.equals("4a")) {
            experiment4Clientes();
        }
        else if (experiment.equals("4b")) {
            experiment4Centrales();
        }
        else if (experiment.equals("5")) {
            experiment5();
        }
        else if (experiment.equals("6")) {
            experiment6();
        }
        else {
            System.err.println("Unknown experiment: " + experiment + ". Use 0, 1, 2, 3, 4a, 4b, 5 or 6");
        }
    }


    // ---------- BEGIN: EXPERIMENTS ----------

    // Quick test: one Hill Climbing run on the base scenario
    private static void singleRun() throws Exception {
        boolean[] ops = OPERATOR_SETS[CHOSEN_OPERATORS];
        runHC("test", N_CLIENTES, CENTRALES, 1, CHOSEN_INIT, ops, HEURISTIC);
    }

    // Experiment 1: operator sets, Hill Climbing, base scenario
    private static void experiment1() throws Exception {
        for (int set = 0; set < OPERATOR_SETS.length; ++set) {
            for (int seed = 1; seed <= REPS; ++seed) {
                runHC("exp1", N_CLIENTES, CENTRALES, seed, INIT_EXP1, OPERATOR_SETS[set], HEURISTIC);
            }
        }
    }

    // Experiment 2: initial solution strategies, Hill Climbing, base scenario
    private static void experiment2() throws Exception {
        int[] inits = {INIT_BASIC, INIT_NEAREST, INIT_RANDOM, INIT_GARANTIZADOS, INIT_CHEAPEST};
        for (int i = 0; i < inits.length; ++i) {
            for (int seed = 1; seed <= REPS; ++seed) {
                runHC("exp2", N_CLIENTES, CENTRALES, seed, inits[i], OPERATOR_SETS[CHOSEN_OPERATORS], HEURISTIC);
            }
        }
    }

    // Experiment 3: Simulated Annealing parameters, base scenario
    private static void experiment3() throws Exception {
        for (int a = 0; a < EXP3_STEPS.length; ++a) {
            for (int b = 0; b < EXP3_STITER.length; ++b) {
                for (int c = 0; c < EXP3_K.length; ++c) {
                    for (int d = 0; d < EXP3_LAMBDA.length; ++d) {
                        for (int seed = 1; seed <= REPS; ++seed) {
                            runSA("exp3", N_CLIENTES, CENTRALES, seed, CHOSEN_INIT, OPERATOR_SETS[CHOSEN_OPERATORS],
                                  HEURISTIC, EXP3_STEPS[a], EXP3_STITER[b], EXP3_K[c], EXP3_LAMBDA[d]);
                        }
                    }
                }
            }
        }
    }

    // Experiment 4a: 40 Centrales, Clientes from 250 increasing by 250
    private static void experiment4Clientes() throws Exception {
        for (int level = 1; level <= EXP4_LEVELS; ++level) {
            int nClientes = 250 * level;
            for (int seed = 1; seed <= REPS; ++seed) {
                runHC("exp4a", nClientes, CENTRALES, seed, CHOSEN_INIT, OPERATOR_SETS[CHOSEN_OPERATORS], HEURISTIC);
            }
        }
    }

    // Experiment 4b: 1000 Clientes, Centrales from 40 increasing by 40 (keeping the proportions 5:10:25)
    private static void experiment4Centrales() throws Exception {
        for (int level = 1; level <= EXP4_LEVELS; ++level) {
            int[] centrales = {CENTRALES[0] * level, CENTRALES[1] * level, CENTRALES[2] * level};
            for (int seed = 1; seed <= REPS; ++seed) {
                runHC("exp4b", N_CLIENTES, centrales, seed, CHOSEN_INIT, OPERATOR_SETS[CHOSEN_OPERATORS], HEURISTIC);
            }
        }
    }

    // Experiment 5: garantizados enforced by the heuristic, starting from an empty solution
    private static void experiment5() throws Exception {
        P1Board.setAllowUnservedGarantizados(true);
        for (int p = 0; p < EXP5_PENALTIES.length; ++p) {
            P1Board.setGarantizadoPenalty(EXP5_PENALTIES[p]);
            String label = "exp5_P" + EXP5_PENALTIES[p];
            for (int seed = 1; seed <= REPS; ++seed) {
                runHC(label, N_CLIENTES, CENTRALES, seed, INIT_EMPTY, OPERATOR_SETS[CHOSEN_OPERATORS], 4);
                runSA(label, N_CLIENTES, CENTRALES, seed, INIT_EMPTY, OPERATOR_SETS[CHOSEN_OPERATORS], 4,
                      SA_STEPS, SA_STITER, SA_K, SA_LAMBDA);
            }
        }
        P1Board.setAllowUnservedGarantizados(false);
    }

    // Experiment 6: more Centrales of type C
    private static void experiment6() throws Exception {
        for (int c = 0; c < EXP6_CENTRALES.length; ++c) {
            for (int seed = 1; seed <= REPS; ++seed) {
                runHC("exp6", N_CLIENTES, EXP6_CENTRALES[c], seed, CHOSEN_INIT, OPERATOR_SETS[CHOSEN_OPERATORS], HEURISTIC);
                runSA("exp6", N_CLIENTES, EXP6_CENTRALES[c], seed, CHOSEN_INIT, OPERATOR_SETS[CHOSEN_OPERATORS], HEURISTIC,
                      SA_STEPS, SA_STITER, SA_K, SA_LAMBDA);
            }
        }
    }

    // ---------- END: EXPERIMENTS ----------


    // ---------- BEGIN: RUNNING A SEARCH ----------

    private static void runHC(String label, int nClientes, int[] centrales, int seed,
                              int init, boolean[] ops, int heuristic) throws Exception {
        P1Board board = createBoard(nClientes, centrales, seed, init);
        SuccessorFunction successors = new P1SuccessorFunction(ops[0], ops[1], ops[2]);
        runSearch(label, "HC", "-", board, nClientes, centrales, seed, init, ops, heuristic,
                  successors, new HillClimbingSearch());
    }

    private static void runSA(String label, int nClientes, int[] centrales, int seed,
                              int init, boolean[] ops, int heuristic,
                              int steps, int stiter, int k, double lambda) throws Exception {
        P1Board board = createBoard(nClientes, centrales, seed, init);
        SuccessorFunction successors = new P1SuccessorFunctionSA(ops[0], ops[1], ops[2], seed);
        String params = steps + " " + stiter + " " + k + " " + lambda;
        runSearch(label, "SA", params, board, nClientes, centrales, seed, init, ops, heuristic,
                  successors, new SimulatedAnnealingSearch(steps, stiter, k, lambda));
    }

    // Creates the scenario and the initial solution
    private static P1Board createBoard(int nClientes, int[] centrales, int seed, int init) throws Exception {
        P1Board board = new P1Board(nClientes, PROP_CLIENTES, PROP_GARANTIZADOS, centrales, seed);
        if (init == INIT_BASIC) {
            board.initialAssigBasic();
        }
        else if (init == INIT_NEAREST) {
            board.initialAssigNearest();
        }
        else if (init == INIT_RANDOM) {
            board.initialAssigRandom(seed, RANDOM_PROB_ASSIGN);
        }
        else if (init == INIT_GARANTIZADOS) {
            board.initialAssigGarantizados();
        }
        else if (init == INIT_CHEAPEST) {
            board.initialAssigCheapest();
        }
        // INIT_EMPTY: the constructor already leaves every Cliente unassigned
        return board;
    }

    // Runs the search, measures the time and prints one CSV line with the results
    private static void runSearch(String label, String algorithm, String saParams, P1Board board,
                                  int nClientes, int[] centrales, int seed, int init, boolean[] ops,
                                  int heuristic, SuccessorFunction successors, Search search) throws Exception {
        double initialProfit = board.getProfit();
        GoalTest goal = new P1GoalTest();
        HeuristicFunction h = new P1HeuristicFunction(heuristic);
        Problem problem = new Problem(board, successors, goal, h);

        long start = System.currentTimeMillis();
        SearchAgent agent = new SearchAgent(problem, search);
        long time = System.currentTimeMillis() - start;

        P1Board result;
        if (search instanceof HillClimbingSearch) {
            result = (P1Board) ((HillClimbingSearch) search).getGoalState();
        }
        else {
            result = (P1Board) ((SimulatedAnnealingSearch) search).getGoalState();
        }

        int steps = agent.getActions().size();
        int[] active = result.countActiveCentralesByType();
        int[] total = result.countCentralesByType();
        double[] used = result.getUsedProductionByType();

        System.out.println(label + "," + algorithm + "," + saParams + "," + seed + ","
                + nClientes + "," + centrales[0] + "," + centrales[1] + "," + centrales[2] + ","
                + INIT_NAMES[init] + "," + operatorsName(ops) + "," + heuristic + ","
                + round2(initialProfit) + "," + round2(result.getProfit()) + ","
                + time + "," + steps + ","
                + active[0] + "," + active[1] + "," + active[2] + ","
                + (total[0] + total[1] + total[2] - active[0] - active[1] - active[2]) + ","
                + round2(used[0]) + "," + round2(used[1]) + "," + round2(used[2]) + ","
                + result.countUnassignedClientes() + "," + round2(result.getUnservedGarantizados()) + ","
                + round2(result.getLostDistance()) + "," + round2(result.getWastedProduction()) + ","
                + result.isValidSolution());
    }

    // ---------- END: RUNNING A SEARCH ----------


    private static void printHeader() {
        System.out.println("experiment,algorithm,sa_params,seed,clientes,centrales_A,centrales_B,centrales_C,"
                + "init,operators,heuristic,initial_profit,final_profit,time_ms,steps,"
                + "active_A,active_B,active_C,stopped,used_MW_A,used_MW_B,used_MW_C,"
                + "unassigned_clientes,unserved_garantizados_MW,lost_distance_MW,wasted_MW,valid");
    }

    private static String operatorsName(boolean[] ops) {
        String name = "";
        if (ops[0]) {
            name += "move+";
        }
        if (ops[1]) {
            name += "swap+";
        }
        if (ops[2]) {
            name += "empty+";
        }
        if (name.length() > 0) {
            name = name.substring(0, name.length() - 1);
        }
        return name;
    }

    private static double round2(double x) {
        return Math.round(x * 100.0) / 100.0;
    }
}
