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

package io.helidon.tests.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

final class ProcessProfilerSupport {
    private ProcessProfilerSupport() {
    }

    static Component component(RecordedEvent event) {
        var stackTrace = event.getStackTrace();
        if (stackTrace == null) {
            return Component.OTHER;
        }
        for (var frame : stackTrace.getFrames()) {
            String className = frame.getMethod().getType().getName();
            if (className.contains("JmhBenchmark")
                    || className.contains("JmhRunner")
                    || className.startsWith("io.helidon.tests.benchmark.")) {
                return Component.HARNESS;
            }
            if (className.startsWith("io.helidon.webclient.")) {
                return Component.WEBCLIENT;
            }
            if (className.startsWith("io.helidon.webserver.")) {
                return Component.WEBSERVER;
            }
            if (className.startsWith("io.helidon.http.http3.")) {
                return Component.HTTP3;
            }
            if (className.startsWith("io.helidon.quic.")) {
                return Component.QUIC;
            }
        }
        return Component.OTHER;
    }

    static void cleanup(Recording recording, Path recordingPath, Throwable primaryFailure) {
        Throwable cleanupFailure = null;
        if (recording != null) {
            try {
                recording.close();
            } catch (RuntimeException | Error e) {
                cleanupFailure = e;
            }
        }
        if (recordingPath != null) {
            try {
                Files.deleteIfExists(recordingPath);
            } catch (IOException e) {
                var wrapped = new UncheckedIOException("Could not delete process-profiler recording", e);
                if (cleanupFailure == null) {
                    cleanupFailure = wrapped;
                } else {
                    cleanupFailure.addSuppressed(wrapped);
                }
            }
        }
        if (primaryFailure != null) {
            if (cleanupFailure != null && cleanupFailure != primaryFailure) {
                primaryFailure.addSuppressed(cleanupFailure);
            }
            return;
        }
        if (cleanupFailure instanceof Error error) {
            throw error;
        }
        if (cleanupFailure instanceof RuntimeException exception) {
            throw exception;
        }
    }

    enum Component {
        WEBCLIENT("webclient"),
        WEBSERVER("webserver"),
        HTTP3("http3"),
        QUIC("quic"),
        HARNESS("harness"),
        OTHER("other");

        private final String label;

        Component(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }
}
