/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
