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

package io.helidon.config;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class MetaConfigFinderTest {
    @Test
    void testConcurrentDiscoveryLogsEachUnsupportedSourceOnce() throws Exception {
        Path file = Files.createTempFile(Path.of(""), "application.", ".unsupported");
        String name = file.getFileName().toString();
        String suffix = name.substring("application.".length());
        URL resource = file.toUri().toURL();
        var loader = new ClassLoader(null) {
            @Override
            protected URL findResource(String requested) {
                return name.equals(requested) ? resource : null;
            }
        };
        var messages = new CopyOnWriteArrayList<String>();
        Logger logger = Logger.getLogger(MetaConfigFinder.class.getName());
        Level originalLevel = logger.getLevel();
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().equals(Level.WARNING) && record.getMessage().contains(name)) {
                    messages.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        var ready = new CountDownLatch(32);
        var start = new CountDownLatch(1);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        logger.addHandler(handler);
        logger.setLevel(Level.WARNING);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                results.add(executor.submit(() -> {
                    ClassLoader original = Thread.currentThread().getContextClassLoader();
                    try {
                        Thread.currentThread().setContextClassLoader(loader);
                        ready.countDown();
                        assertThat("Concurrent discovery was released", start.await(10, TimeUnit.SECONDS), is(true));
                        for (int attempt = 0; attempt < 10; attempt++) {
                            assertThat(MetaConfigFinder.findConfigSource(_ -> false, List.of(suffix)).isEmpty(), is(true));
                        }
                        return null;
                    } finally {
                        Thread.currentThread().setContextClassLoader(original);
                    }
                }));
            }
            try {
                assertThat("All discovery tasks are ready", ready.await(10, TimeUnit.SECONDS), is(true));
            } finally {
                start.countDown();
            }
            for (Future<?> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
            assertThat(messages, containsInAnyOrder(
                    "Configuration file " + name + " is on file system, yet there is no parser configured for it",
                    "Configuration file " + name + " is on classpath, yet there is no parser configured for it"));
        } finally {
            start.countDown();
            // A stuck discovery task must not prevent Future.get from reporting its timeout.
            executor.shutdownNow();
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
            Files.deleteIfExists(file);
        }
    }
}
