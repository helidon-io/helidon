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

package io.helidon.microprofile.testing.junit5;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit5 extension to support pinned threads validation.
 */
class HelidonPinnedThreadValidationJunitExtension implements BeforeAllCallback, AfterAllCallback {

    private RecordingStream recordingStream;
    private boolean pinnedThreadValidation;
    private PinningException pinningException;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        pinnedThreadValidation = testClass.getAnnotation(PinnedThreadValidation.class) != null;
        if (pinnedThreadValidation) {
            recordingStream = new RecordingStream();
            recordingStream.enable("jdk.VirtualThreadPinned").withStackTrace();
            recordingStream.onEvent("jdk.VirtualThreadPinned", this::record);
            recordingStream.startAsync();
        }
    }

    void record(RecordedEvent event) {
        PinningException e = new PinningException(event);
        if (pinningException == null) {
            pinningException = e;
        } else {
            pinningException.addSuppressed(e);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (pinnedThreadValidation) {
            try {
                // Flush ending events
                recordingStream.stop();
                if (pinningException != null) {
                    throw pinningException;
                }
            } finally {
                recordingStream.close();
            }
        }
    }

    private static class PinningException extends AssertionError {
        private final RecordedEvent recordedEvent;

        PinningException(RecordedEvent recordedEvent) {
            this.recordedEvent = recordedEvent;
            if (recordedEvent.getStackTrace() != null) {
                StackTraceElement[] stackTraceElements = recordedEvent.getStackTrace().getFrames().stream()
                        .map(f -> new StackTraceElement(f.getMethod().getType().getName(),
                                                        f.getMethod().getName(),
                                                        f.getMethod().getType().getName() + ".java",
                                                        f.getLineNumber()))
                        .toArray(StackTraceElement[]::new);
                super.setStackTrace(stackTraceElements);
            }
        }

        @Override
        public String getMessage() {
            return "Pinned virtual threads were detected:\n"
                    + recordedEvent.toString();
        }
    }
}
