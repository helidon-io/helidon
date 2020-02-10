/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config.changes;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;

/**
 *
 */
public class ScheduledPollingStrategy implements PollingStrategy {
    /*
     * This class will trigger checks in a periodic manner.
     * The actual check if the source has changed is done elsewhere, this is just responsible for telling us
     * "check now".
     * The feedback is an information whether the change happened or not.
     */
    private final ScheduledExecutorService executor = Executors
            .newScheduledThreadPool(1, Thread::new);

    private final AtomicReference<Object> lastStamp = new AtomicReference<>();
    private ConfigSource.PollableSource<Object> source;
    private Polled polled;

    @Override
    public void start(Polled polled) {
        this.polled = polled;
        this.executor.scheduleAtFixedRate(this::trigger, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        executor.shutdownNow();
    }

    private void trigger() {
        // ignore the result for now
        polled.poll(Instant.now());
    }
}
