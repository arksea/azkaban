package arksea.azkaban;

import azkaban.executor.ExecutionOptions;

/**
 *
 * @author arksea
 */
public class Condition {
    public Condition(String expression) {
        this.expression = expression;
        this.triggered = true;
        this.inheritedOpt = new ExecutionOptions();
    }
    
    public final String expression;
    private ExecutionOptions inheritedOpt; //从前置flow继承的运行参数
    
    //一次条件满足只触发一次FollowingFlow的运行
    //用于防止多次运行某个FrontingFlow时，造成多次运行他的FollowingFlow（不止依赖这一个FrongingFlow,而其他FrongtingFlow又已经是Succeed状态）
    //初始状态应该设置为true
    //当FrontingFlow执行完成时将其所有FollowingFlow中的本FrongtingFlow的triggered设置为false
    //当FollowingFlow执行条件全部满足在发起执行请求前将其所有FrontingFlow的triggered设置为true
    private boolean triggered; 
    
    public boolean getTriggered() {
        return triggered;
    }
    
    public void followingFlowReady() {
        triggered = true;
    }
    
    public void oneFrontingFlowSucceed(ExecutionOptions inheritedOpt) {
        triggered = false;
        this.inheritedOpt = inheritedOpt;
    }
    
    public ExecutionOptions getIneritedOpt() {
        return inheritedOpt;
    }
}
