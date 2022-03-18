/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.reactive.Single;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

@HelidonTest
public class UncaughtExceptionTest {

    private static final Logger incomingMethodLogger = Logger.getLogger(IncomingMethod.class.getName());

    static final Duration TIME_OUT = Duration.ofSeconds(5);
    static CompletableFuture<Void> completed = new CompletableFuture<>();
    static Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel() == Level.SEVERE &&
                    record.getThrown().getMessage().equals("BOOM!")) {
                completed.complete(null);
            }
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() throws SecurityException {

        }
    };

    static {
        incomingMethodLogger.addHandler(handler);
    }

    @AfterAll
    static void afterAll() {
        incomingMethodLogger.removeHandler(handler);
    }

    @Test
    void imperativeProcessor() {
        Single.create(completed, true).await(TIME_OUT);
    }

    @Outgoing("test-channel")
    public PublisherBuilder<Message<String>> generateStream() {
        return ReactiveStreams.of(1, 2, 3, 4)
                .map(String::valueOf)
                .map(Message::of);
    }

    @Incoming("test-channel")
    public CompletionStage<Void> getMessages(Message<String> message) {
        return CompletableFuture.failedFuture(new RuntimeException("BOOM!"));
    }
}
