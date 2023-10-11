package io.helidon.webserver.accesslog;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;

import io.helidon.common.Weighted;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

class AccessLogHttpFeature implements HttpFeature, Weighted {
    private final System.Logger logger;
    private final double weight;
    private final Clock clock;
    private final List<AccessLogEntry> logFormat;

    AccessLogHttpFeature(double weight, Clock clock, List<AccessLogEntry> logFormat, String baseLogger, String socketName) {
        this.weight = weight;
        this.clock = clock;
        this.logFormat = logFormat;
        this.logger = System.getLogger(baseLogger + "." + socketName);
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.addFilter(this::filter);
    }

    @Override
    public double weight() {
        return weight;
    }

    String createLogRecord(RoutingRequest req,
                           RoutingResponse res,
                           ZonedDateTime timeStart,
                           long nanoStart,
                           ZonedDateTime timeNow,
                           long nanoNow) {

        AccessLogContext ctx = new ContextImpl(nanoStart,
                                               nanoNow,
                                               timeStart,
                                               timeNow,
                                               req,
                                               res);

        StringBuilder sb = new StringBuilder();

        for (AccessLogEntry entry : logFormat) {
            sb.append(entry.apply(ctx));
            sb.append(" ");
        }

        if (sb.length() > 1) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }

    private void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        long nanoNow = System.nanoTime();

        try {
            chain.proceed();
        } finally {
            log(logger, req, res, now, nanoNow);
        }
    }

    private void log(System.Logger logger, RoutingRequest req, RoutingResponse res, ZonedDateTime timeStart, long nanoStart) {
        logger.log(System.Logger.Level.INFO,
                   createLogRecord(req, res, timeStart, nanoStart, ZonedDateTime.now(clock), System.nanoTime()));
    }

    private record ContextImpl(long requestNanoTime,
                               long responseNanoTime,
                               ZonedDateTime requestDateTime,
                               ZonedDateTime responseDateTime,
                               RoutingRequest serverRequest,
                               RoutingResponse serverResponse) implements AccessLogContext {

    }

}
