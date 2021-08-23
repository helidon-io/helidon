/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.messaging;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

public class ConsumerErrorHandlingTest {
    private static final Logger LOGGER = Logger.getLogger(ConsumerErrorHandlingTest.class.getName());
    private static final Logger messagingLogger = Logger.getLogger(Messaging.class.getName());
    private static final ScheduledExecutorService scheduledExec = Executors.newSingleThreadScheduledExecutor();

    @Test
    void simpleConsumerErrorLogged() {

        final String EXCEPTION_1 = "BOOM_1";
        final String EXCEPTION_2 = "BOOM_2";

        CompletableLogHandler completableLogHandler = new CompletableLogHandler();
        messagingLogger.addHandler(completableLogHandler);
        try {
            Channel<String> channel1 = Channel.create("test-channel-1");
            Channel<String> channel2 = Channel.create("test-channel-2");

            Single<LogRecord> boom1Single = completableLogHandler
                    .futureOf(log -> log.getThrown().getMessage().equals(EXCEPTION_1));
            Single<LogRecord> boom2Single = completableLogHandler
                    .futureOf(log -> log.getThrown().getMessage().equals(EXCEPTION_2));

            Messaging.builder()

                    .publisher(channel1, Multi.error(new Exception(EXCEPTION_1)))
                    .listener(channel1, System.out::println)

                    .publisher(channel2, Multi.just("TEST").map(Message::of))
                    .listener(channel2, x -> {
                        throw new RuntimeException(EXCEPTION_2);
                    })

                    .build()
                    .start();

            boom1Single.timeout(800, TimeUnit.MILLISECONDS, scheduledExec)
                    .onError(t -> LOGGER.log(Level.SEVERE, EXCEPTION_1 + " wasn't logged in time."))
                    .await();
            boom2Single.timeout(800, TimeUnit.MILLISECONDS, scheduledExec)
                    .onError(t -> LOGGER.log(Level.SEVERE, EXCEPTION_2 + " wasn't logged in time."))
                    .await();
        } finally {
            messagingLogger.removeHandler(completableLogHandler);
        }
    }

    private static class CompletableLogHandler extends Handler {

        Map<Predicate<LogRecord>, CompletableFuture<LogRecord>> predicateSingleMap = new HashMap<>();

        Single<LogRecord> futureOf(Predicate<LogRecord> predicate) {
            return Single.create(predicateSingleMap.computeIfAbsent(predicate, p -> new CompletableFuture<>()));
        }

        @Override
        public void publish(LogRecord record) {
            predicateSingleMap.forEach((p, cf) -> {
                if (p.test(record)) {
                    cf.complete(record);
                }
            });
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() throws SecurityException {

        }
    }
}
