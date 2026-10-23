import aima.search.framework.GoalTest;

public class P1GoalTest implements GoalTest {

    // There is no goal state so we just return false always
    public boolean isGoalState(Object state) {
        return false;
    }
}