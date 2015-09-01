package arksea.azkaban;

import arksea.jactor.ChildInfo;
import arksea.jactor.RestartStrategies;
import arksea.jactor.TaskContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.log4j.xml.DOMConfigurator;

public class FlowLinkServer {

    private static final Logger logger = LoggerFactory.getLogger(FlowLinkServer.class);
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            DOMConfigurator.configure("./conf/log4j.xml");
            ChildInfo[] childs = new ChildInfo[1];
            String confPath = args.length==1 ? args[0] : "./conf";
            childs[0] = FlowDependencyUpdater.createChildInfo(confPath);
            TaskContext context = TaskContext.instance();
            context.start("trigger_sup", RestartStrategies.ONE_FOR_ONE, childs);
        } catch (Exception ex) {
            logger.error("启动服务进程失败", ex);
        }
    }
}
