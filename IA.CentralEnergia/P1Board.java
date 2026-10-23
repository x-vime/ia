// TODO

// TODO: comprobar si hace falta todas estas clases o se puede quitar alguna
import IA.Energia.Central;
import IA.Energia.Centrales;
import IA.Energia.Cliente;
import IA.Energia.Clientes;
import IA.Energia.TestEnergia;
import IA.Energia.VEnergia;

import IA.Energia.Central;
import IA.Energia.Centrales;
import IA.Energia.Cliente;
import IA.Energia.Clientes;
import IA.Energia.VEnergia;

public class P1Board {
    private static Centrales centrales;
    private static Clientes clientes;

    // Number of Centrales and Clientes
    private static int nCentrales;
    private static int nClientes;

    private double profit;
    private double lostDistance; // Profit lost because of energy losses over distance

    // For experiment 5
    private static boolean allowUnservedGarantizados = false;
    private double unservedGarantizados;   // Power (MW) demanded by Clientes of type GARANTIZADO that is not served
    private static double garantizadoPenalty = 1000.0;


    /*
    private double profit_diff; // UNUSED
    private double[] production; // UNUSED, using remaining instead
    */

    private int[] assignments;   // assignments[i] = Central to which the Cliente i is assigned. -1 if none
    private double[] remaining;  // Reamaining MW available for each Central

    // Used for heuristics 2 and 3 respectively to determine how important it is the loss of energy due to
    // distance (heuristic 2) or the unused energy from active Centrales (heuristic 3) for determining a solutions quality
    private static final double lossWeight = 1.0;
    private static final double wasteWeight = 1.0;



    // static VEnergia vEnergia; // Unnecessary

    // Initializes assignments and and remaining. All Clientes are unassigned and all Centrales stopped
    // and calculates the profit and lostDistance.
    private void initState() throws Exception {
        assignments = new int[nClientes];
        remaining = new double[nCentrales];
        profit = 0;
        lostDistance = 0;
        unservedGarantizados = 0;
        for (int cli = 0; cli < nClientes; ++cli) {
            assignments[cli] = -1;
            profit += clienteProfit(cli, false);
            if (isGarantizado(cli)) {
                unservedGarantizados = round3(unservedGarantizados + clientes.get(cli).getConsumo());
            }
        }
        for (int cen = 0; cen < nCentrales; ++cen) {
            remaining[cen] = centrales.get(cen).getProduccion();
            profit -= getCentralCost(cen, false);
        }
    }

// ---------- BEGIN: CONSTRUCTORS ----------


    // Copy constructor
    public P1Board(P1Board state) {
        assignments = state.assignments.clone();
        remaining = state.remaining.clone();
        profit = state.profit;
        lostDistance = state.lostDistance;
        unservedGarantizados = state.unservedGarantizados;
    }

    // Generate new problem
    // args are as defined in the javadocs
    public P1Board(int ncl, double[] propc, double propg, int[] cent, int seed) throws Exception {
        centrales = new Centrales(cent, seed);
        clientes = new Clientes(ncl, propc, propg, seed);
        nCentrales = centrales.size();
        nClientes = clientes.size();
        initState();
    }

// ---------- END: CONSTRUCTORS ----------





    // Returns the distance between a Cliente and a Central
    private static double distancia(Cliente cli, Central cen) {
        double dx = cli.getCoordX() - cen.getCoordX();
        double dy = cli.getCoordY() - cen.getCoordY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    // To prevent precision errors when operating with doubles.
    private static double round3(double x) {
        return Math.round(x * 1000.0) / 1000.0;
    }

    // Returns the power (MW) the Central has to produce to serve the Cliente (include losses by distance)
    private static double consumoReal(Cliente cli, Central cen) {
        return round3(cli.getConsumo() * (1.0 + VEnergia.getPerdida(distancia(cli, cen))));
    }

    // Claude
    // <0 si a va antes que b, >0 si va después, 0 si da igual
    private static int compareClientes(Cliente cli1, Cliente cli2) {
        if (cli1.getContrato() != cli2.getContrato()) {
            if (cli1.getContrato() == Cliente.GARANTIZADO) {
                return -1;
            }
            return 1;
        }
        return Double.compare(cli2.getConsumo(), cli1.getConsumo());
    }

    // Claude
    // Devuelve los índices de los clientes en el orden de asignación
    private static int[] sortClientes() {
        int n = clientes.size();
        int[] idx = new int[n];
        for (int i = 0; i < n; ++i) idx[i] = i;
        mergeSort(idx, new int[n], 0, n);
        return idx;
    }

    // Claude
    // cambiar nombre a mergesortclientes
    // Ordena idx[lo, hi)
    private static void mergeSort(int[] idx, int[] tmp, int lo, int hi) {
        if (hi - lo < 2) return;
        int mid = (lo + hi) / 2;
        mergeSort(idx, tmp, lo, mid);
        mergeSort(idx, tmp, mid, hi);

        int i = lo, j = mid, k = lo;
        while (i < mid && j < hi) {
            if (compareClientes(clientes.get(idx[i]), clientes.get(idx[j])) <= 0) tmp[k++] = idx[i++];
            else                                                                    tmp[k++] = idx[j++];
        }
        while (i < mid) tmp[k++] = idx[i++];
        while (j < hi)  tmp[k++] = idx[j++];
        for (k = lo; k < hi; ++k) idx[k] = tmp[k];
    }


// ---------- BEGIN: INITIAL SOLUTIONS ----------

    // Assigns each Cliente to the first Central with enough capacity
    // First assigns Clientes of type GARANTIZADO, and within each type it assigngs them by descending order of consumption
    public void initialAssigBasic() throws Exception {
        initState();
        int[] sortedClientes = sortClientes();

        for (int i = 0; i < nClientes; ++i) {
            int cli = sortedClientes[i];
            for (int cen = 0; cen < nCentrales && assignments[cli] == -1; ++cen) {
                if (canBeAssigned(cli, cen)) {
                    assign(cli, cen);
                }
            }
            if (assignments[cli] == -1 && isGarantizado(cli)) {
                throw new IllegalStateException("El cliente garantizado " + cli + " does no cabe en ninguna central");
            }
        }
    }


    // Assigns each Cliente to the nearest Central with enough capacity
    // First assigns Clientes of type GARANTIZADO, and within each type it assigngs them by descending order of consumption
    public void initialAssigNearest() throws Exception {
        initState();
        int[] sortedClientes = sortClientes();

        for (int i = 0; i < nClientes; ++i) {
            int cli = sortedClientes[i];
            int best = -1;
            double bestDist = 0;
            for (int cen = 0; cen < nCentrales; ++cen) {
                if (canBeAssigned(cli, cen)) {
                    double d = distancia(clientes.get(cli), centrales.get(cen));
                    if (best == -1 || d < bestDist) {
                        best = cen;
                        bestDist = d;
                    }
                }
            }
            if (best != -1) {
                assign(cli, best);
            }
            else if (isGarantizado(cli)) {
                throw new IllegalStateException("El cliente garantizado " + cli + " no cabe en ninguna central");
            }
        }
    }

    // Assigns each Cliente GARANTIZADO to a random Central (or the first one with enough capacity if they do not fit).
    // Assigns each Cliente NOGARANNTIZADO to a random Central with probability probAssign, and leaves them unassigned if they do not fit.
    // First assigns Clientes of type GARANTIZADO, and within each type it assigngs them by descending order of consumption
    public void initialAssigRandom(long seed, double probAssign) throws Exception {
        initState();
        Random rand = new Random(seed);
        int[] sortedClientes = sortClientes();

        for (int i = 0; i < nClientes; ++i) {
            int cli = sortedClientes[i];
            if (isGarantizado(cli)) {
                int cen = rand.nextInt(nCentrales);
                if (!canBeAssigned(cli, cen)) {
                    cen = findAvailableCentral(cli, -1);
                }
                if (cen == -1) {
                    throw new IllegalStateException("El cliente garantizado " + cli + " no cabe en ninguna central");
                }
                assign(cli, cen);
            }
            else if (rand.nextDouble() < probAssign) {
                int cen = rand.nextInt(nCentrales);
                if (canBeAssigned(cli, cen)) {
                    assign(cli, cen);
                }
            }
        }
    }

    // Same as InitialAssigBasic() but only assigns Clientes of type GARANTIZADO
    public void initialAssigGarantizados() throws Exception {
        initState();
        int[] sortedClientes = sortClientes();

        for (int i = 0; i < nClientes; ++i) {
            int cli = sortedClientes[i];
            if (!isGarantizado(cli)) {
                break;   // sortedClientes has all the garantizados first
            }
            for (int cen = 0; cen < nCentrales && assignments[cli] == -1; ++cen) {
                if (canBeAssigned(cli, cen)) {
                    assign(cli, cen);
                }
            }
            if (assignments[cli] == -1) {
                throw new IllegalStateException("El cliente garantizado " + cli + " no cabe en ninguna central");
            }
        }
    }

    // Same as InitialAssigBasic() but Centrales are order by ascending order of price/MW
    public void initialAssigCheapest() throws Exception {
        initState();
        int[] sortedClientes = sortClientes();
        int[] sortedCentrales = sortCentralesByCost();

        for (int i = 0; i < nClientes; ++i) {
            int cli = sortedClientes[i];
            for (int k = 0; k < nCentrales && assignments[cli] == -1; ++k) {
                int cen = sortedCentrales[k];
                if (canBeAssigned(cli, cen)) {
                    assign(cli, cen);
                }
            }
            if (assignments[cli] == -1 && isGarantizado(cli)) {
                throw new IllegalStateException("El cliente garantizado " + cli + " no cabe en ninguna central");
            }
        }
    }


// ---------- END: INITIAL SOLUTIONS ----------

// ---------- BEGIN: HEURISTICS ----------

    // Aims to maximize profit
    public double getHeuristic1() {
        return -profit;
    }

    // Aims to maximize profit while minimizing the losses caused by distance
    public double getHeuristic2() {
        return -profit + lossWeight * lostDistance;
    }

    // Aims to maximize profit while minimizing the cost of wasted energy (that is, power that is produced but not used)
    public double getHeuristic3() throws Exception {
        return -profit + wasteWeight * calcWastedCost();
    }

    // Aims to maximize profit while minimizing power not served to Clientes of type GARANTIZADO
    public double getHeuristic4() {
        return -profit + garantizadoPenalty * unservedGarantizados;
    }



//  ---------- END: HEURISTICS ----------

    // TODO: Order all these functions


    public static void setAllowUnservedGarantizados(boolean allow) {
        allowUnservedGarantizados = allow;
    }

    public static void setGarantizadoPenalty(double p) {
        garantizadoPenalty = p;
    }

    // Returns the power (MW) remaining to be served to Clientes of type GARANTIZADO
    public double getUnservedGarantizados() {
        return unservedGarantizados;
    }

    // Returns the cost of the energy produced by active Centrales but not used by any Cliente
    private double calcWastedCost() throws Exception {
        double cost = 0;
        for (int cen = 0; cen < nCentrales; ++cen) {
            if (!isCentralEmpty(cen)) {
                cost += remaining[cen] * VEnergia.getCosteProduccionMW(centrales.get(cen).getTipo());
            }
        }
        return cost;
    }
    // Returnns the cost per MW of having Central cen active instead of stopped
    private static double costPerMW(int cen) throws Exception {
        return (centralCost(cen, true) - centralCost(cen, false)) / centrales.get(cen).getProduccion();
    }

    // Claude
    // Returns the indices of the Centrales sorted by costPerMW, cheapest first (insertion sort)
    private static int[] sortCentralesByCost() throws Exception {
        double[] cost = new double[nCentrales];
        for (int cen = 0; cen < nCentrales; ++cen) {
            cost[cen] = costPerMW(cen);
        }

        int[] idx = new int[nCentrales];
        for (int i = 0; i < nCentrales; ++i) {
            idx[i] = i;
        }
        for (int i = 1; i < nCentrales; ++i) {
            int cen = idx[i];
            int j = i - 1;
            while (j >= 0 && cost[idx[j]] > cost[cen]) {
                idx[j + 1] = idx[j];
                --j;
            }
            idx[j + 1] = cen;
        }
        return idx;
    }

    public static void setLossWeight(double w) {
        lossWeight = w;
    }

    public static void setWasteWeight(double w) {
        wasteWeight = w;
    }

    public double getLostDistance() {
        return lostDistance;
    }

    // Calculates and returns the profit for the current state
    // UNUSED. Profit is calculated inside the functions that cause profit changes
    public double calcProfit() throws Exception {
        double p = 0;
        for (int cli = 0; cli < nClientes; ++cli) {
            p += clienteProfit(cli, assignments[cli] != -1);
        }
        for (int cen = 0; cen < nCentrales; ++cen) {
            p -= getCentralCost(cen, !isCentralEmpty(cen));
        }
        return p;
    }

    /* UNUSED. Should probably be removed
    // Calculates and returns the profit lost by power dissipation (cause by distance between a Cliente and their Central
    private double calcLostProfitDistance() {

    }

    // Calculates and returns the profit lost by wasted production (energy produced but not used)
    private double calcLostProfitWasted() {

    }

    // Calculates and returns the profit loss caused by distance between Clientes-Centrales and by energy produced but not used
    private double calcLostProfitTotal() {

    }


    // Calculates and returns the sum of costs of all active Centrales
    private double calcProductionCost() {

    }

    // Calculates and returns the sum of costs of all stopped Centrales
    private double calcStoppageCost() {

    }

    // Calculates and returns the sum of costs of all active and inactive Centrales (calcProductionCost() + calcStoppageCost())
    private double calcCentralesCost() {

    }

    // Calculates and returns the cost of compensating all the unassignend Clientes no garantizados
    private double calcCompensationsCost() {

    }

    // Calculates and returns the sum of all the costs: Energy production, stopped Centrales and compensation for Clientes
    private double calcTotalCost() {

    }
    */

    // Returns true if every Cliente GARANTIZADO is assigned to a Central
    public boolean areGarantizadosServed() {
        for (int cli = 0; cli < nClientes; ++cli) {
            if (isGarantizado(cli) && assignments[cli] == -1) {
                return false;
            }
        }
        return true;
    }

    // Returns true if the Cliente is GARANTIZADO
    private static boolean isGarantizado(int cli) {
        return clientes.get(cli).getContrato() == Cliente.GARANTIZADO;
    }

    // Returns true if Central cen has enough remaining capacity to serve Cliente cli
    private boolean canBeAssigned(int cli, int cen) {
        return consumoReal(clientes.get(cli), centrales.get(cen)) <= remaining[cen];
    }

    // Returns true if Central cen has no clients assigned to it
    private boolean isCentralEmpty(int cen) {
        return remaining[cen] == centrales.get(cen).getProduccion();
    }


    // Assigns Cliente cli to Central cen and calculates the new profit
    // Does not check if the Central has enough capacity, it must be checked beforehand with canBeAssigned()
    private void assign(int cli, int cen) throws Exception {
        if (isCentralEmpty(cen)) {
            // change from stopped to active
            profit += getCentralCost(cen, false) - getCentralCost(cen, true);
        }
        assignments[cli] = cen;
        remaining[cen] = round3(remaining[cen] - consumoReal(clientes.get(cli), centrales.get(cen)));
        lostDistance = round3(lostDistance + getLostPower(cli, cen));
        profit += clienteProfit(cli, true) - clienteProfit(cli, false);
        if (isGarantizado(cli)) {
            unservedGarantizados = round3(unservedGarantizados - clientes.get(cli).getConsumo());
        }
    }

    // Unassigns Cliente cli from its Central and recalculates profit
    // Does not check whether cli is GARANTIZADO or not
    private void unassign(int cli) throws Exception {
        int cen = assignments[cli];
        assignments[cli] = -1;
        remaining[cen] = round3(remaining[cen] + consumoReal(clientes.get(cli), centrales.get(cen)));
        lostDistance = round3(lostDistance - getLostPower(cli, cen));
        profit += clienteProfit(cli, false) - clienteProfit(cli, true);
        if (isCentralEmpty(cen)) {
            // change from active to stopped
            profit += getCentralCost(cen, true) - getCentralCost(cen, false);
        }
        if (isGarantizado(cli)) {
            unservedGarantizados = round3(unservedGarantizados + clientes.get(cli).getConsumo());
        }
    }

    // Returns a Central which can serve Cliente cli, excluding the one they are currently assigned to
    // -1 if none
    // First tries to assign them to an active Central to reduce costs, if it cannot then it tries to
    // asign them to a stopped one
    private int findAvailableCentral(int cli, int oldCent) {
        for (int cen = 0; cen < nCentrales; ++cen) {
            if (cen != oldCent && !isCentralEmpty(cen) && canBeAssigned(cli, cen)) {
                return cen;
            }
        }
        for (int cen = 0; cen < nCentrales; ++cen) {
            if (cen != oldCent && isCentralEmpty(cen) && canBeAssigned(cli, cen)) {
                return cen;
            }
        }
        return -1;
    }

    // Returns the profit made from Cliente cli. Negative means we pay compensation for not serving them
    private static double clienteProfit(int cli, boolean served) throws Exception {
        Cliente c = clientes.get(cli);
        if (!served) {
            return -c.getConsumo() * VEnergia.getTarifaClientePenalizacion(c.getTipo());
        }
        if (c.getContrato() == Cliente.GARANTIZADO) {
            return c.getConsumo() * VEnergia.getTarifaClienteGarantizada(c.getTipo());
        }
        return c.getConsumo() * VEnergia.getTarifaClienteNoGarantizada(c.getTipo());
    }


    // If active == true, returns the cost of starting Central and operating it for a day
    // If active == false, returns the cost of stopping the Central
    private static double getCentralCost(int cen, boolean active) throws Exception {
        Central c = centrales.get(cen);
        if (active) {
            return c.getProduccion() * VEnergia.getCosteProduccionMW(c.getTipo())
                    + VEnergia.getCosteMarcha(c.getTipo());
        }
        return VEnergia.getCosteParada(c.getTipo());
    }

    // Returns how much extra power is needed to serve a Cliente due to losses by distance
    private static double getLostPower(int cli, int cen) {
        Cliente c = clientes.get(cli);
        return round3(consumoReal(c, centrales.get(cen)) - c.getConsumo());
    }



    // ----- BEGIN: OPERATORS -----

    // Tries to assign the Cliente cli to the Central newCent
    // newCent can be -1 if we just want to unassign the Cliente, but only if the Cliente is not a Cliente garantizado
    // Returns true if it could be assigned while maintaining a correct solution
    public boolean move(int cli, int newCent) throws Exception {
        int oldCent = assignments[cli];
        if (newCent == oldCent) {
            return false;
        }
        if (newCent == -1) {
            if (isGarantizado(cli) && !allowUnservedGarantizados) {
                return false;
            }
            unassign(cli);
            return true;
        }
        if (!canBeAssigned(cli, newCent)) {
            return false;
        }
        if (oldCent != -1) {
            unassign(cli);
        }
        assign(cli, newCent);
        return true;
    }

    // Assigns cli1 to the central cli2 is assigned to, and then assigns cli2 to the central cli1 was assigned to
    // If none of them is assigned to a central, return false. Otherwise return true if they could be swapped while keeping a correct solution
    // If they cannot be swapped keeping a correct solution, do not swap the and return false
    public boolean swap(int cli1, int cli2) throws Exception {
        int cen1 = assignments[cli1];
        int cen2 = assignments[cli2];
        // Both assigned to the same central, change nothing
        if (cen1 == cen2) {
            return false;   // both unassigned, or same Central: nothing changes
        }
        // One is GARANTIZADO and the other unassigned, cannot swap
        if (cen2 == -1 && isGarantizado(cli1) && !allowUnservedGarantizados) {
            return false;
        }
        if (cen1 == -1 && isGarantizado(cli2) && !allowUnservedGarantizados) {
            return false;
        }
        // One Central cannot serve its new Cliente, cannot swapp
        if (cen2 != -1) {
            Central c2 = centrales.get(cen2);
            if (consumoReal(clientes.get(cli1), c2) > round3(remaining[cen2] + consumoReal(clientes.get(cli2), c2))) {
                return false;
            }
        }
        if (cen1 != -1) {
            Central c1 = centrales.get(cen1);
            if (consumoReal(clientes.get(cli2), c1) > round3(remaining[cen1] + consumoReal(clientes.get(cli1), c1))) {
                return false;
            }
        }
        // swap
        if (cen1 != -1) {
            unassign(cli1);
        }
        if (cen2 != -1) {
            unassign(cli2);
        }
        if (cen2 != -1) {
            assign(cli1, cen2);
        }
        if (cen1 != -1) {
            assign(cli2, cen1);
        }
        return true;
    }


    // Removes all assignations of Clientes to the Central cent and tries to assign them to other Centrales.
    // Returns true if it can do so while keeping the state valid (i.e. it can assigned all Clientes garantizados to other Centrales)
    public boolean emptyCentral(int cen) throws Exception {
        if (isCentralEmpty(cen)) {
            return false;
        }
        // Count number of Clientes in Central cen
        int n = 0;
        for (int cli = 0; cli < nClientes; ++cli) {
            if (assignments[cli] == cen) {
                ++n;
            }
        }

        // moved stores the Clientes in cen. Stores GARANTIZADOS first
        int[] moved = new int[n];
        n = 0;
        for (int cli = 0; cli < nClientes; ++cli) {
            if (assignments[cli] == cen && isGarantizado(cli)) {
                moved[n++] = cli;
            }
        }
        for (int cli = 0; cli < nClientes; ++cli) {
            if (assignments[cli] == cen && !isGarantizado(cli)) {
                moved[n++] = cli;
            }
        }

        // unassigned() stops cen automatically after removing the last Cliente
        for (int i = 0; i < n; ++i) {
            unassign(moved[i]);
        }

        for (int i = 0; i < n; ++i) {
            int cli = moved[i];
            int target = findAvailableCentral(cli, cen);
            if (target != -1) {
                assign(cli, target);
            }
            else if (isGarantizado(cli) && !allowUnservedGarantizados) {
                // Could not assigned a Cliente GARANTIZADO, undo all changes
                for (int k = 0; k < n; ++k) {
                    if (assignments[moved[k]] != -1) {
                        unassign(moved[k]);
                    }
                }
                for (int k = 0; k < n; ++k) {
                    assign(moved[k], cen);
                }
                return false;
            }
        }
        return true;
    }

// ---------- END: OPERATORS ----------


// ---------- BEGIN: GETTERS ----------

    // Returns the number of clients assigned to any Central
    public long countAssignedClientes() {
        long count = 0;
        for (int i = 0; i < nClientes; ++i) {
            if (assignments[i] != -1) {
                ++count;
            }
        }
        return count;
    }

    // Returns the number of clients not assigned to any Central
    public long countUnassignedClientes() {
        return nClientes - countAssignedClientes();
    }

    // Returns the production that is produced but not used
    public double getWastedProduction() {
        boolean[] active = getActiveCentrales();
        double wasted = 0;
        for (int j = 0; j < nCentrales; ++j) {
            if (active[j]) {
                wasted += remaining[j];
            }
        }
        return wasted;
    }

    // Returns the production that is not used, whether produced or not
    // (getWastedProduction plus the potential production of stopped Centrales)
    public double getUnusedProduction() {
        double unused = 0;
        for (int j = 0; j < nCentrales; ++j) {
            unused += remaining[j];
        }
        return unused;
    }

    // Returns the number of active Centrales, i.e. Centrales with at least 1 client assigned
    public long countActiveCentrales() {
        boolean[] active = getActiveCentrales();
        long count = 0;
        for (int j = 0; j < nCentrales; ++j) {
            if (active[j]) {
                ++count;
            }
        }
        return count;
    }

    // Returns the number of stopped Centrales, i.e. Centrales with no clients assigned
    public long countStoppedCentrales() {
        return nCentrales - countActiveCentrales();
    }

    public double getProfit() {
        return profit;
    }

    public int getNClientes() {
        return nClientes;
    }

    public int getNCentrales() {
        return nCentrales;
    }



// ---------- END: GETTERS ----------

// ---------- BEGIN: SETTERS ----------


// ---------- END: SETTERS ----------


    // active[j] == true if Central j has at least one client assigned
    private boolean[] getActiveCentrales() {
        boolean[] active = new boolean[nCentrales];
        for (int i = 0; i < nClientes; ++i) {
            if (assignments[i] != -1) {
                active[assignments[i]] = true;
            }
        }
        return active;
    }

    // Returns how many Centrales of each type are running: [A, B, C]
    public int[] countActiveCentralesByType() {
        int[] count = new int[3];
        for (int cen = 0; cen < nCentrales; ++cen) {
            if (!isCentralEmpty(cen)) {
                count[centrales.get(cen).getTipo()]++;
            }
        }
        return count;
    }

    // Returns how many Centrales of each type there are: [A, B, C]
    public int[] countCentralesByType() {
        int[] count = new int[3];
        for (int cen = 0; cen < nCentrales; ++cen) {
            count[centrales.get(cen).getTipo()]++;
        }
        return count;
    }

    // Returns the MW used (served to Clientes, including losses) in Centrales of each type: [A, B, C]
    public double[] getUsedProductionByType() {
        double[] used = new double[3];
        for (int cen = 0; cen < nCentrales; ++cen) {
            Central c = centrales.get(cen);
            used[c.getTipo()] += c.getProduccion() - remaining[cen];
        }
        return used;
    }

    // Checks whether the current solution is valid or not. (garantizdos serevd and Centrales within capacity)
    public boolean isValidSolution() {
        if (!areGarantizadosServed()) {
            return false;
        }
        double[] used = new double[nCentrales];
        for (int cli = 0; cli < nClientes; ++cli) {
            int cen = assignments[cli];
            if (cen != -1) {
                used[cen] += consumoReal(clientes.get(cli), centrales.get(cen));
            }
        }
        for (int cen = 0; cen < nCentrales; ++cen) {
            if (round3(used[cen]) > centrales.get(cen).getProduccion()) {
                return false;
            }
        }
        return true;
    }


}