package arksea.azkaban;

import arksea.jactor.Message;
import arksea.jactor.Actor;
import arksea.jactor.ChildInfo;
import arksea.jactor.TaskContext;
import azkaban.alert.Alerter;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutorManager;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.ExtJdbcExecutorLoader;
import azkaban.executor.Status;
import azkaban.flow.Flow;
import azkaban.flow.Node;
import azkaban.project.JdbcProjectLoader;
import azkaban.project.Project;
import azkaban.project.ProjectManagerException;
import azkaban.server.AzkabanServer;
import azkaban.utils.Emailer;
import azkaban.utils.Props;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author arksea
 */
public class FlowDependencyUpdater extends Actor<FlowDependencyUpdater.State> {

    public static final String ACTOR_NAME = "flow-dependency-updater";
    public static final long DEFAULT_TIMEOUT = 10000;
    public static final long UPDATE_DEPEND_DELAY = 100000;
    public static final long UPDATE_EXEC_DELAY = 30000;
    private static final int DEFAULT_PORT_NUMBER = 8081;
    private static final int DEFAULT_SSL_PORT_NUMBER = 8443;
    private static final Logger logger = LoggerFactory.getLogger(FlowDependencyUpdater.class);
    private JdbcProjectLoader projectLoader;
    private ExtJdbcExecutorLoader execLoader;
    private ExecutorManager execManager;

    //Actor的参数与状态，Actor因为错误被重启后会被继承
    public static class State {

        String azkabanConfPath;
        Map<String, FrontingFlow> frontingFlows;  //以projectName:flowId为Key
        Map<String, FollowingFlow> followingFlows;  //以projectName:flowId为Key
        int lastExecId;
    }

    public static ChildInfo createChildInfo(String azkabanConfPath) {
        State s = new State();
        s.azkabanConfPath = azkabanConfPath;
        s.frontingFlows = new HashMap<>();
        s.followingFlows = new HashMap<>();
        return new ChildInfo(ACTOR_NAME, FlowDependencyUpdater.class, s);
    }

    public FlowDependencyUpdater(String name, long msgQueuLen, State state) {
        super(name, msgQueuLen, state);
    }

    @Override
    protected void init() throws Throwable {
        logger.info("use the conf path start server: "+state.azkabanConfPath);
        Props azkabanSettings = AzkabanServer.loadProps(new String[]{"-conf", state.azkabanConfPath});
        String hostname = azkabanSettings.getString("jetty.hostname", "localhost");
        int port;
        boolean ssl;
        if (azkabanSettings.getBoolean("jetty.use.ssl", true)) {
            int sslPortNumber = azkabanSettings.getInt("jetty.ssl.port", DEFAULT_SSL_PORT_NUMBER);
            port = sslPortNumber;
            ssl = true;
        } else {
            ssl = false;
            port = azkabanSettings.getInt("jetty.port", DEFAULT_PORT_NUMBER);
        }
        azkabanSettings.put("server.hostname", hostname);
        azkabanSettings.put("server.port", port);
        azkabanSettings.put("server.useSSL", String.valueOf(ssl));
        projectLoader = new JdbcProjectLoader(azkabanSettings);
        execLoader = new ExtJdbcExecutorLoader(azkabanSettings);
        Map<String, Alerter> alerters = loadAlerters(azkabanSettings);
        execManager = new ExecutorManager(azkabanSettings, execLoader, alerters);
        TaskContext.instance().send_after(10000, ACTOR_NAME, new Message("on_update_depend", ""));
        TaskContext.instance().send_after(20000, ACTOR_NAME, new Message("on_update_exec", ""));
    }

    private Map<String, Alerter> loadAlerters(Props props) {
        Map<String, Alerter> allAlerters = new HashMap<>();
        // load built-in alerters
        Emailer mailAlerter = new Emailer(props);
        allAlerters.put("email", mailAlerter);
        return allAlerters;
    }

    @Override
    protected void terminate(Throwable ex) {
        logger.info("FlowDependencyUpdate Actor terminated");
    }

    @Override
    protected Message handle_call(Message msg, String string) throws Throwable {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void handle_info(Message msg, String from) throws Throwable {
        String key = msg.name;
        logger.debug(key);
        switch (key) {
            case "on_update_depend":
                updateDependency();
                TaskContext.instance().send_after(UPDATE_DEPEND_DELAY, ACTOR_NAME, new Message("on_update_depend", ""));
                break;
            case "on_update_exec":
                updateExec();
                TaskContext.instance().send_after(UPDATE_EXEC_DELAY, ACTOR_NAME, new Message("on_update_exec", ""));
                break;
        }
    }

    private void parseFrontingString(String frontingStr, Project prj, Flow flow) {
        String ffkey = prj.getName() + ":" + flow.getId();
        FollowingFlow followingFlow = state.followingFlows.get(ffkey);
        boolean updateFrontingCondition = false;
        if (followingFlow == null || !followingFlow.frontingFlowsCfg.equals(frontingStr)) {
            if (followingFlow == null) {
                logger.info(ffkey+" added fronting-flows=" + frontingStr);
            } else {
                logger.info(ffkey+" modified fronting-flows="+followingFlow.frontingFlowsCfg+"->" + frontingStr);
            }
            Map<FrontingFlow, Condition> frontingCondition = new HashMap<>();
            followingFlow = new FollowingFlow(frontingStr, prj, flow, frontingCondition);
            state.followingFlows.put(ffkey, followingFlow);
            updateFrontingCondition = true;
        }
        String[] strs = frontingStr.split(",");
        for (String str : strs) {
            String[] args = str.split(":");
            if (args.length != 2 && args.length != 3) {
                logger.warn("" + prj.getId() + ":" + flow.getId() + "的fronting-flows配置错误: " + frontingStr);
                continue;
            }
            String prjStr = args[0].trim();
            String flowStr = args[1].trim();
            String key = prjStr + ":" + flowStr;
            FrontingFlow frontingFlow = state.frontingFlows.get(key);
            if (frontingFlow == null) {
                frontingFlow = new FrontingFlow(prjStr, flowStr);
                state.frontingFlows.put(key, frontingFlow);
            }
            if (updateFrontingCondition) {
                Condition condition = new Condition(args.length == 3 ? args[2] : "");
                followingFlow.frontingCondition.put(frontingFlow, condition);
                logger.debug(ffkey+"'s fronting-flow added: "+key);
            }
            frontingFlow.followingFlows.add(followingFlow);
        }
    }

    private void updateDependency() throws ProjectManagerException {
        for (FrontingFlow ff : state.frontingFlows.values()) {
            ff.followingFlows.clear();
        }
        List<Project> projects = projectLoader.fetchAllActiveProjects();
        for (Project prj : projects) {
            List<Flow> flows = projectLoader.fetchAllProjectFlows(prj);
            //logger.debug("project "+prj.getName()+" flow count: "+flows.size());
            for (Flow flow : flows) {
                Collection<Node> nodes = flow.getNodes();
                for (Node n : nodes) {
                    String source = n.getJobSource();
                    Props props = projectLoader.fetchProjectProperty(prj.getId(), prj.getVersion(), source);
                    if (props.containsKey("fronting-flows")) {
                        String str = props.getString("fronting-flows");
                        parseFrontingString(str, prj, flow);
                        break; //以搜索到的第一个fronting-flows为准
                    }
                }
            }
        }
    }

    private void updateExec() throws ExecutorManagerException {
        if (state.lastExecId == 0) {
            List<ExecutableFlow> his = execLoader.fetchFlowHistory(0, 1);
            if (his.size() > 0) {
                state.lastExecId = his.get(0).getExecutionId();
            }
            return;
        }
        List<ExecutableFlow> succeeded = execLoader.fetchExecutableFlowAfter(state.lastExecId);
        boolean skipUnsucceeded = false;
        for (ExecutableFlow s : succeeded) {
            String key = s.getProjectName() + ":" + s.getFlowId();
            FrontingFlow fronting = state.frontingFlows.get(key);
            //把fronting!=null的判断放在SUCCEEDED的外层是为了先跳过那些没有配置fronting-flow的flow
            if (fronting != null) {
                if (s.getStatus() == Status.SUCCEEDED) {
                    if (!skipUnsucceeded) {
                        state.lastExecId = s.getExecutionId();
                    }
                    if (s.getStartTime() > fronting.lastStartTime && s.getEndTime() > fronting.lastSucceededTime) {
                        fronting.lastStartTime = s.getStartTime();
                        fronting.lastSucceededTime = s.getEndTime();
                        ExecutionOptions opt = s.getExecutionOptions();
                        if (opt != null && opt.getFlowParameters().containsKey("skip-following-flow")) {
                            logger.info(key + " skip following flow because it's executed by arg skip-following-flow");
                        } else {
                            logger.debug(key +" is succeed");
                            frontingSucceed(fronting);
                        }
                    } else {
                        logger.debug("skip checked succeed flow: "+key);
                    }
                } else { //当检测列表中还有未完成的任务时，将跳过state.lastExecId的赋值，下次从这个未完成的任务开始重新扫描
                    logger.debug("skip executing flow of not succeed: "+key);
                    skipUnsucceeded = true;
                }
            } else {
                logger.debug("skip executing flow of not configed dependencies: "+key);
                if (!skipUnsucceeded) {
                    state.lastExecId = s.getExecutionId();
                }
            }
        }
    }

    private void frontingSucceed(FrontingFlow succeed) {
        //当FrontingFlow执行完成时将其所有FollowingFlow中的本FrongtingFlow的triggered设置为false
        for (FollowingFlow following : succeed.followingFlows) {
            following.frontingCondition.get(succeed).oneFrontingFlowSucceed();
        }
        for (FollowingFlow following : succeed.followingFlows) {
            boolean pass = true;
            long now = System.currentTimeMillis();
            Calendar cnow = Calendar.getInstance();
            OUTER:
            for (Map.Entry<FrontingFlow, Condition> e : following.frontingCondition.entrySet()) {
                FrontingFlow fronting = e.getKey();
                Condition condition = e.getValue();
                String expression = condition.expression.trim();
                Calendar startTime = Calendar.getInstance();
                startTime.setTimeInMillis(fronting.lastStartTime);
                //有frontingFlow的条件不成立或者trigger已经被触发过的则认为不满足执行FollowingFlow的条件
                if (condition.getTriggered()) {
                    logger.debug(following.toString()+" -> "+fronting.toString()+":triggered;");
                    pass = false;
                    break;
                }
                switch (expression) {
                    case "sameday()":
                        if (!DateUtils.isSameDay(cnow, startTime)) {
                            logger.debug(following.toString()+" -> "+fronting.toString()+":sameday()=timeout; ");
                            pass = false;
                            break OUTER;
                        }
                        logger.debug(following.toString()+" -> "+fronting.toString()+":sameday()=pass;");
                        break;
                    case "samehour()":
                        if (!DateUtils.isSameDay(cnow, startTime)
                                || cnow.get(Calendar.HOUR_OF_DAY) != startTime.get(Calendar.HOUR_OF_DAY)) {
                            logger.debug(following.toString()+" -> "+fronting.toString()+":samehour()=timeout;");
                            pass = false;
                            break OUTER;
                        }
                        logger.debug(following.toString()+" -> "+fronting.toString()+":samehour()=pass;");
                        break;
                    default:
                        //默认为12小时内
                        if (now - fronting.lastStartTime > 12 * 3600 * 1000) {
                            logger.debug(following.toString()+" -> "+fronting.toString()+":withinHours(12)=timeout;");
                            pass = false;
                            break OUTER;
                        }
                        logger.debug(following.toString()+" -> "+fronting.toString()+":withinHours(12)=pass;");
                        break;
                }
            }
            if (pass) {
                requestExecuteFlow(following);
            }
        }
    }

    void requestExecuteFlow(FollowingFlow followingFlow) {
        //当FollowingFlow执行条件全部满足时将其所有FrontingFlow的triggered设置为true
        for (Condition c : followingFlow.frontingCondition.values()) {
            c.followingFlowReady();
        }
        ExecutableFlow exflow = new ExecutableFlow(followingFlow.project, followingFlow.flow);
        exflow.setSubmitUser(followingFlow.project.getLastModifiedUser());
        exflow.addAllProxyUsers(followingFlow.project.getProxyUsers());

        ExecutionOptions executionOptions = new ExecutionOptions();
        executionOptions.setFailureEmails(followingFlow.flow.getFailureEmails());
        executionOptions.setSuccessEmails(followingFlow.flow.getSuccessEmails());

        exflow.setExecutionOptions(executionOptions);

        try {
            execManager.submitExecutableFlow(exflow, followingFlow.project.getLastModifiedUser());
            logger.info("Invoked flow " + followingFlow.toString());
        } catch (ExecutorManagerException ex) {
            logger.error("Invoked flow failed " + followingFlow.toString(), ex);
            throw new RuntimeException(ex);
        }
    }
}
