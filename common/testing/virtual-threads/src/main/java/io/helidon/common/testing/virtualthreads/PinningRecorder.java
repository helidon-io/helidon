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

package io.helidon.common.testing.virtualthreads;

import java.time.Duration;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

/**
 * Record pinned thread events and throw exception when detected.
 */
public class PinningRecorder implements AutoCloseable {

    /**
     * Default threshold for considering carrier thread blocking as pinning.
     */
    public static final long DEFAULT_THRESHOLD = 20;
    private static final String JFR_EVENT_VIRTUAL_THREAD_PINNED = "jdk.VirtualThreadPinned";
    private final RecordingStream recordingStream = new RecordingStream();
    private volatile PinningAssertionError pinningAssertionError;

    private PinningRecorder() {
        //noop
    }

    /**
     * Create new pinning JFR event recorder.
     *
     * @return new pinning recorder
     */
    public static PinningRecorder create() {
        return new PinningRecorder();
    }

    /**
     * Start async recording of {@code jdk.VirtualThreadPinned} JFR event.
     *
     * @param threshold time threshold for carrier thread blocking to be considered as pinning
     */
    public void record(Duration threshold) {
        recordingStream.enable(JFR_EVENT_VIRTUAL_THREAD_PINNED)
                .withThreshold(threshold)
                .withStackTrace();
        recordingStream.onEvent(JFR_EVENT_VIRTUAL_THREAD_PINNED, this::record);
        recordingStream.startAsync();
    }

    @Override
    public void close() {
        try {
            // Flush ending events
            recordingStream.stop();
        } finally {
            recordingStream.close();
        }
        checkAndThrow();
    }

    void checkAndThrow() {
        if (pinningAssertionError != null) {
            throw pinningAssertionError;
        }
    }

    private void record(RecordedEvent event) {
        PinningAssertionError e = new PinningAssertionError(event);
        if (pinningAssertionError == null) {
            pinningAssertionError = e;
        } else {
            pinningAssertionError.addSuppressed(e);
        }
    }
}
