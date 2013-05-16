package com.continuuity.performance.application;

import com.continuuity.common.conf.CConfiguration;
import com.continuuity.common.conf.Constants;
import com.continuuity.metrics2.thrift.Counter;
import com.continuuity.metrics2.thrift.CounterRequest;
import com.continuuity.metrics2.thrift.FlowArgument;
import com.continuuity.metrics2.thrift.MetricsFrontendService;
import com.google.common.base.Throwables;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.hsqldb.lib.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runtime statistics of an application during a benchmark.
 */
public final class BenchmarkRuntimeStats {
  private static Logger LOG = LoggerFactory.getLogger(BenchmarkRuntimeStats.class);

  private static MetricsFrontendService.Client metricsClient = getMetricsClient();

  public static BenchmarkRuntimeMetrics getFlowletMetrics(final String applicationId, final String flowId,
                                                          final String flowletId) {
    return getMetrics(applicationId, flowId, flowletId);
  }

  private static BenchmarkRuntimeMetrics getMetrics(final String applicationId, final String flowId,
                                                    final String flowletId) {
    final String inputName = String.format("%s.tuples.read.count", flowletId);
    final String processedName = String.format("%s.processed.count", flowletId);

    return new BenchmarkRuntimeMetrics() {
      @Override
      public long getInput() {
        Double input = getCounters(applicationId, flowId, flowletId).get(inputName);
        if (input == null) {
          return 0L;
        } else {
          return input.longValue();
        }
      }

      @Override
      public long getProcessed() {
        Double processed = getCounters(applicationId, flowId, flowletId).get(processedName);
        if (processed == null) {
          return 0L;
        } else {
          return processed.longValue();
        }
      }

      @Override
      public void waitForinput(long count, long timeout, TimeUnit timeoutUnit)
        throws TimeoutException, InterruptedException {
        waitFor(inputName, count, timeout, timeoutUnit);
      }

      @Override
      public void waitForProcessed(long count, long timeout, TimeUnit timeoutUnit)
        throws TimeoutException, InterruptedException {
        waitFor(processedName, count, timeout, timeoutUnit);
      }

      private void waitFor(String name, long count, long timeout, TimeUnit timeoutUnit)
        throws TimeoutException, InterruptedException {
        Double value = getCounters(applicationId, flowId, flowletId).get(name);
        while (timeout > 0 && (value == null || value.longValue() < count)) {
          timeoutUnit.sleep(1);
          value = getCounters(applicationId, flowId, flowletId).get(name);
          timeout--;
        }
        if (timeout == 0 && (value == null || value.longValue() < count)) {
          throw new TimeoutException("Time limit reached.");
        }
      }

      @Override
      public String toString() {
        return String.format("%s; input=%d, processed=%d, exception=%d", flowletId, getInput(), getProcessed());
      }
    };
  }

  public static Counter getCounter(String applicationId, String flowName, String flowletName, String counterName) {
    FlowArgument arg = new FlowArgument("developer", applicationId, flowName);
    try {
      List<Counter> counters = metricsClient.getCounters(new CounterRequest(arg));
      for (Counter counter : counters) {
        if (counter.getQualifier().equals(flowletName) && counter.getName().equals(counterName)) {
          return counter;
        }
      }
      return null;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<String, Double> getCounters(String applicationId, String flowName) {
    return getCounters(applicationId, flowName, null);
  }

  public static Map<String, Double> getCounters(String applicationId, String flowName, String flowletName) {
    FlowArgument arg = new FlowArgument("developer", applicationId, flowName);
    try {
      List<Counter> counters = metricsClient.getCounters(new CounterRequest(arg));
      Map<String, Double> counterMap = new HashMap<String, Double>(counters.size());
      if (StringUtil.isEmpty(flowletName)) {
        for (Counter counter : counters) {
          counterMap.put(counter.getQualifier() + "." + counter.getName(), counter.getValue());
        }
      } else {
        for (Counter counter : counters) {
          if (counter.getQualifier().equals(flowletName)) {
            counterMap.put(counter.getQualifier() + "." + counter.getName(), counter.getValue());
          }
        }
      }
      return counterMap;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static MetricsFrontendService.Client getMetricsClient() {
    CConfiguration config = CConfiguration.create();
    try {
      return
        new MetricsFrontendService.Client(
          getThriftProtocol(config.get(Constants.CFG_METRICS_FRONTEND_SERVER_ADDRESS,
                                       Constants.DEFAULT_OVERLORD_SERVER_ADDRESS),
                            config.getInt(Constants.CFG_METRICS_FRONTEND_SERVER_PORT,
                                          Constants.DEFAULT_METRICS_FRONTEND_SERVER_PORT)));
    } catch (TTransportException e) {
      Throwables.propagate(e);
    }
    return null;
  }

  private static TProtocol getThriftProtocol(String serviceHost, int servicePort) throws TTransportException {
    TTransport transport = new TFramedTransport(new TSocket(serviceHost, servicePort));
    try {
      transport.open();
    } catch (TTransportException e) {
      String message = String.format("Unable to connect to thrift service at %s:%d. Reason: %s", serviceHost,
                                     servicePort, e.getMessage());
      LOG.error(message);
      throw e;
    }
    //now try to connect the thrift client
    return new TBinaryProtocol(transport);
  }
}
