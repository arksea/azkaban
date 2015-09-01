package azkaban.executor;

import azkaban.utils.GZIPUtils;
import azkaban.utils.JSONUtils;
import azkaban.utils.Props;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;

/**
 *
 * @author arksea
 */
public class ExtJdbcExecutorLoader extends JdbcExecutorLoader {

    public ExtJdbcExecutorLoader(Props props) {
        super(props);
    }

  public List<ExecutableFlow> fetchExecutableFlowAfter(int id)
      throws ExecutorManagerException {
    QueryRunner runner = createQueryRunner();
    FetchExecutableFlowsAfter flowHandler = new FetchExecutableFlowsAfter();
    try {
      List<ExecutableFlow> properties =
          runner.query(FetchExecutableFlowsAfter.FETCH_EXECUTABLE_FLOW_AFTER, flowHandler, id);
      return properties;
    } catch (SQLException e) {
      throw new ExecutorManagerException("Error fetching flow after id " + id, e);
    }
  }
  
  private static class FetchExecutableFlowsAfter implements
      ResultSetHandler<List<ExecutableFlow>> {
    private static String FETCH_EXECUTABLE_FLOW_AFTER =
        "SELECT exec_id, enc_type, flow_data FROM execution_flows "
            + "WHERE exec_id>? order by exec_id";

    @Override
    public List<ExecutableFlow> handle(ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.<ExecutableFlow> emptyList();
      }

      List<ExecutableFlow> execFlows = new ArrayList<>();
      do {
        int id = rs.getInt(1);
        int encodingType = rs.getInt(2);
        byte[] data = rs.getBytes(3);

        if (data != null) {
          EncodingType encType = EncodingType.fromInteger(encodingType);
          Object flowObj;
          try {
            // Convoluted way to inflate strings. Should find common package
            // or helper function.
            if (encType == EncodingType.GZIP) {
              // Decompress the sucker.
              String jsonString = GZIPUtils.unGzipString(data, "UTF-8");
              flowObj = JSONUtils.parseJSONFromString(jsonString);
            } else {
              String jsonString = new String(data, "UTF-8");
              flowObj = JSONUtils.parseJSONFromString(jsonString);
            }

            ExecutableFlow exFlow =
                ExecutableFlow.createExecutableFlowFromObject(flowObj);
            execFlows.add(exFlow);
          } catch (IOException e) {
            throw new SQLException("Error retrieving flow data " + id, e);
          }
        }
      } while (rs.next());

      return execFlows;
    }
  }
}
