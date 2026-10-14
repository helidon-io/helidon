/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.publishers.otlp;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.http.HeaderNames;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@Isolated
class TestOtlpLogging {
    @Test
    void responseDefaultsUnknownFieldsAndPartialSuccessAreHandled() {
        List<ResponseCase> responses = List.of(
                new ResponseCase("{}", 0, ""),
                new ResponseCase(" \t\r\n{} \t\r\n", 0, ""),
                new ResponseCase("{\"partialSuccess\":{}}", 0, ""),
                new ResponseCase("{\"partialSuccess\":null}", 0, ""),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":null,\"errorMessage\":null}}", 0, ""),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":0}}", 0, ""),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":\"0\"}}", 0, ""),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":1}}", 1, ""),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":\"1\"}}", 1, ""),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":1e2}}", 100, ""),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":\"1e2\"}}", 100, ""),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":1.0}}", 1, ""),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":\"1.0\"}}", 1, ""),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":9007199254740993}}", 9007199254740993L, ""),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":\"9007199254740993\"}}", 9007199254740993L, ""),
                new ResponseCase("{\"partialSuccess\":{\"errorMessage\":\"receiver warning\"}}", 0, "receiver warning"),
                new ResponseCase("""
                        {"future":{"nested":[null,true,1,"ignored"]},
                         "partialSuccess":{"future":{"nested":[1,2,3]},"rejectedDataPoints":"0"}}
                        """, 0, ""),
                new ResponseCase("""
                        {"future":[{"nested":"ignored"}],
                         "partialSuccess":{"unknown":false,"rejectedDataPoints":"2","errorMessage":"receiver warning"}}
                        """, 2, "receiver warning"),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":1,\"errorMessage\":null}}", 1, ""),
                new ResponseCase("""
                        {"partialSuccess":{"rejectedDataPoints":null,"errorMessage":"receiver warning"}}
                        """, 0, "receiver warning"));
        assertResponses(responses);
    }

    @Test
    void invalidResponsesWarnWithoutRetrying() {
        assertResponses(List.of(
                new ResponseCase(""),
                new ResponseCase("null"),
                new ResponseCase("[]"),
                new ResponseCase("42"),
                new ResponseCase("{"),
                new ResponseCase("{} {}"),
                new ResponseCase("{} trailing"),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":9223372036854775808}}"),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":\"9223372036854775808\"}}"),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":-9223372036854775809}}"),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":1.5}}"),
                new ResponseCase("{\"partialSuccess\":{\"rejectedDataPoints\":\"1.5\"}}")));
    }

    private static void assertResponses(List<ResponseCase> responses) {
        var warnings = new ConcurrentLinkedQueue<LogRecord>();
        Logger logger = Logger.getLogger(OtlpSession.class.getName());
        Level previousLevel = logger.getLevel();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        try {
            for (var expected : responses) {
                warnings.clear();
                var requestCount = new AtomicInteger();
                var server = WebServer.builder()
                        .port(0)
                        .shutdownHook(false)
                        .routing(routing -> routing.post("/v1/metrics", (request, reply) -> {
                            request.content().as(byte[].class);
                            requestCount.incrementAndGet();
                            reply.header(HeaderNames.CONTENT_TYPE, "application/json");
                            reply.send(expected.body());
                        }))
                        .build().start();
                try {
                    var publisher = OtlpPublisher.builder()
                            .endpoint(URI.create("http://localhost:" + server.port() + "/v1/metrics"))
                            .interval(Duration.ofDays(1))
                            .timeout(Duration.ofSeconds(5))
                            .build();
                    var factory = HelidonMetricsFactory.builder()
                            .metricsConfig(MetricsConfig.builder().addPublisher(publisher))
                            .build();
                    try {
                        factory.globalRegistry().getOrCreate(factory.counterBuilder("response.counter")).increment();
                    } finally {
                        factory.close();
                    }
                    assertThat("A successful response never retries", requestCount.get(), is(1));
                    boolean warns = expected.invalid() || expected.rejected() > 0 || !expected.message().isEmpty();
                    assertThat("Warnings for collector response " + expected.body(), warnings.size(), is(warns ? 1 : 0));
                    if (expected.invalid()) {
                        assertThat("The response is diagnosed as invalid JSON", warnings.element().getMessage(),
                                   containsString("invalid JSON"));
                    } else if (warns) {
                        LogRecord warning = warnings.element();
                        assertThat("The response is decoded as partial success", warning.getMessage(),
                                   containsString("reported partial success"));
                        assertThat("The warning retains exact rejected count and message",
                                   List.of(warning.getParameters()), contains(expected.rejected(), expected.message()));
                    }
                } finally {
                    server.stop();
                }
            }
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            handler.close();
        }
    }

    private record ResponseCase(String body, long rejected, String message, boolean invalid) {
        private ResponseCase(String body, long rejected, String message) {
            this(body, rejected, message, false);
        }

        private ResponseCase(String body) {
            this(body, 0, "", true);
        }
    }
}
