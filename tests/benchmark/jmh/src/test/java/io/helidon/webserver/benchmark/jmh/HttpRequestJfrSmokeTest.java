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

package io.helidon.webserver.benchmark.jmh;

import java.nio.file.Path;
import java.util.List;

import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class HttpRequestJfrSmokeTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @CsvSource({"HTTP1, OFF", "HTTP1, DISABLED", "HTTP1, ENABLED",
                "HTTP2, OFF", "HTTP2, DISABLED", "HTTP2, ENABLED"})
    void exchangeAndRecordingModes(String protocol, String mode) throws Exception {
        var state = new HttpRequestJfrJmhBenchmark();
        state.protocol = protocol;
        state.recordingMode = mode;
        try {
            state.setup();
            assertThat(state.keepAliveExchange(), is("Hello, World!"));
            assertThat(state.keepAliveExchange(), is("Hello, World!"));
            if (!"OFF".equals(mode)) {
                Path file = directory.resolve(protocol + "-" + mode + ".jfr");
                state.dump(file);
                var events = RecordingFile.readAllEvents(file);
                var requests = events.stream()
                        .filter(event -> event.getEventType().getName()
                                .equals(HttpRequestJfrJmhBenchmark.REQUEST_EVENT))
                        .toList();
                boolean expected = "ENABLED".equals(mode)
                        && Boolean.parseBoolean(System.getProperty("http.request.jfr.expectEvents", "true"));
                assertThat("Request events for " + protocol + "/" + mode,
                           requests.size(), is(expected ? 2 : 0));
                assertThat("Other Helidon events must remain disabled", events.stream()
                        .filter(event -> event.getEventType().getName().startsWith("io.helidon."))
                        .filter(event -> !event.getEventType().getName().equals(HttpRequestJfrJmhBenchmark.REQUEST_EVENT))
                        .toList(), is(List.of()));
            }
        } finally {
            state.tearDown();
        }
    }
}
