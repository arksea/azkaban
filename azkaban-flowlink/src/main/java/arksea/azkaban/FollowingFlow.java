package arksea.azkaban;

import azkaban.executor.ExecutionOptions;
import azkaban.flow.Flow;
import azkaban.project.Project;
import java.util.Map;

/**
 *
 * @author arksea
 */
public class FollowingFlow {
    public FollowingFlow(String frontingFlowsCfg, Project project,Flow flow,Map<FrontingFlow, Condition> frontingCondition) {
        this.project = project;
        this.flow = flow;
        this.frontingCondition = frontingCondition;
        this.frontingFlowsCfg = frontingFlowsCfg;
    }
    public final Project project;
    public final Flow flow;
    public final String frontingFlowsCfg;
    public final Map<FrontingFlow, Condition> frontingCondition; //保存condition
    
    private int hash;
    
    @Override
    public int hashCode() {
        if (hash == 0) {
            hash = (project.getName()+":"+flow.getId()).hashCode();
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final FollowingFlow other = (FollowingFlow) obj;
        return this.project.getName().equals(other.project.getName()) && this.flow.getId().equals(other.flow.getId());
    }
    
    @Override
    public String toString() {
        return project.getName()+":"+flow.getId();
    }
}
