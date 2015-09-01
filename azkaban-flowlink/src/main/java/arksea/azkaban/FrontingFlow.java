package arksea.azkaban;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author arksea
 */
public class FrontingFlow{
    public FrontingFlow(String projectName,String flowId) {
        this.projectName = projectName;
        this.flowId = flowId;
        followingFlows = new HashSet<>();
    }
    public final String projectName;
    public final String flowId;

    String condition;
    long lastStartTime;
    long lastSucceededTime;
    Set<FollowingFlow> followingFlows;  //存储所有依赖本Flow的后置Flow，定时清除并Update

    private int hash;

    @Override
    public int hashCode() {
        if (hash == 0) {
            hash = (projectName+":"+flowId).hashCode();
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final FrontingFlow other = (FrontingFlow) obj;
        return this.projectName.equals(other.projectName) && this.flowId.equals(other.flowId);
    }
    
}
