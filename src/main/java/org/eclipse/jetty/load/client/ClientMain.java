package org.eclipse.jetty.load.client;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.HdrHistogram.Histogram;
import org.eclipse.jetty.toolchain.perf.HistogramSnapshot;
import org.mortbay.jetty.load.generator.HTTP1ClientTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.load.generator.listeners.ReportListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientMain
{
    private static final Logger LOG = LoggerFactory.getLogger(ClientMain.class);

    public static void main(String[] args)
    {
        ReportListener listener = new ReportListener();

        Resource resource = new Resource("/slow/?duration=60");

        LoadGenerator generator = LoadGenerator.builder()
            .scheme("http")
            .host("localhost")
            .port(9999)
            .resource(resource)
            .httpClientTransportBuilder(new HTTP1ClientTransportBuilder().selectors(1))
            .threads(1)
            .usersPerThread(10)
            .channelsPerUser(6)
            .warmupIterationsPerThread(10)
            .iterationsPerThread(100)
            .runFor(2, TimeUnit.MINUTES)
            .resourceListener(listener)
            .build();

        run(generator);

        ReportListener.Report report = listener.whenComplete().join();
        displayReport(generator.getConfig(), report);
    }

    private static void run(LoadGenerator loadGenerator)
    {
        LOG.info("load generator config: {}", loadGenerator.getConfig());
        LOG.info("load generation begin");
        CompletableFuture<Void> cf = loadGenerator.begin();
        cf.whenComplete((x, f) ->
        {
            if (f == null)
            {
                LOG.info("load generation complete");
            }
            else
            {
                LOG.info("load generation failure", f);
            }
        }).join();
    }

    private static void displayReport(LoadGenerator.Config config, ReportListener.Report report)
    {
        Histogram responseTimes = report.getResponseTimeHistogram();
        HistogramSnapshot snapshot = new HistogramSnapshot(responseTimes, 20, "response times", "ms", TimeUnit.NANOSECONDS::toMillis);
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());
        LOG.info("");
        LOG.info("----------------------------------------------------");
        LOG.info("-------------  Load Generator Report  --------------");
        LOG.info("----------------------------------------------------");
        LOG.info("{}://{}:{} over {}", config.getScheme(), config.getHost(), config.getPort(), config.getHttpClientTransportBuilder().getType());
        int resourceCount = config.getResource().descendantCount();
        LOG.info("resource tree     : {} resource(s)", resourceCount);
        Instant beginInstant = report.getBeginInstant();
        LOG.info("begin date time   : {}", dateTimeFormatter.format(beginInstant));
        Instant completeInstant = report.getCompleteInstant();
        LOG.info("complete date time: {}", dateTimeFormatter.format(completeInstant));
        LOG.info("recording time    : {} s", String.format("%.3f", (double)report.getRecordingDuration().toMillis() / 1000));
        LOG.info("average cpu load  : {}/{}", String.format("%.3f", report.getAverageCPUPercent()), Runtime.getRuntime().availableProcessors() * 100);
        LOG.info("");
        if (responseTimes.getTotalCount() > 0)
        {
            LOG.info("histogram:");
            Arrays.stream(snapshot.toString().split(System.lineSeparator())).forEach(line -> LOG.info("{}", line));
            LOG.info("");
        }
        double resourceRate = config.getResourceRate();
        LOG.info("nominal resource rate (resources/s): {}", String.format("%.3f", resourceRate));
        LOG.info("nominal request rate (requests/s)  : {}", String.format("%.3f", resourceRate * resourceCount));
        LOG.info("request rate (requests/s)          : {}", String.format("%.3f", report.getRequestRate()));
        LOG.info("response rate (responses/s)        : {}", String.format("%.3f", report.getResponseRate()));
        LOG.info("send rate (bytes/s)                : {}", String.format("%.3f", report.getSentBytesRate()));
        LOG.info("receive rate (bytes/s)             : {}", String.format("%.3f", report.getReceivedBytesRate()));
        LOG.info("failures          : {}", report.getFailures());
        LOG.info("response 1xx group: {}", report.getResponses1xx());
        LOG.info("response 2xx group: {}", report.getResponses2xx());
        LOG.info("response 3xx group: {}", report.getResponses3xx());
        LOG.info("response 4xx group: {}", report.getResponses4xx());
        LOG.info("response 5xx group: {}", report.getResponses5xx());
        LOG.info("----------------------------------------------------");
    }
}
