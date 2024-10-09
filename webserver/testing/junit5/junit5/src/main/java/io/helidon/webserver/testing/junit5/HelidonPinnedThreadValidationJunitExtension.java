/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5;

import java.util.ArrayList;
import java.util.List;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * JUnit5 extension to support pinned threads validation.
 */
class HelidonPinnedThreadValidationJunitExtension implements BeforeAllCallback, AfterAllCallback {

    private List<EventWrapper> jfrVTPinned;
    private RecordingStream recordingStream;
    private boolean pinnedThreadValidation;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        pinnedThreadValidation = testClass.getAnnotation(PinnedThreadValidation.class) != null;
        if (pinnedThreadValidation) {
            jfrVTPinned = new ArrayList<>();
            recordingStream = new RecordingStream();
            recordingStream.enable("jdk.VirtualThreadPinned").withStackTrace();
            recordingStream.onEvent("jdk.VirtualThreadPinned", event -> {
                jfrVTPinned.add(new EventWrapper(event));
            });
            recordingStream.startAsync();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (pinnedThreadValidation) {
            try {
                // Flush ending events
                recordingStream.stop();
                if (!jfrVTPinned.isEmpty()) {
                    fail("Some pinned virtual threads were detected:\n" + jfrVTPinned);
                }
            } finally {
                recordingStream.close();
            }
        }
    }

    private static class EventWrapper {

        private final RecordedEvent recordedEvent;

        private EventWrapper(RecordedEvent recordedEvent) {
            this.recordedEvent = recordedEvent;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(recordedEvent.toString());
            if (recordedEvent.getStackTrace() != null) {
                builder.append("full-stackTrace = [");
                List<RecordedFrame> frames = recordedEvent.getStackTrace().getFrames();
                for (RecordedFrame frame : frames) {
                    builder.append("\n\t").append(frame.getMethod().getType().getName())
                    .append("#").append(frame.getMethod().getName())
                    .append("(").append(frame.getLineNumber()).append(")");
                }
                builder.append("\n]");
            }
            return builder.toString();
        }
    }
}
