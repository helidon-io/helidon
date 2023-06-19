/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.microprofile;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.eclipse.microprofile.metrics.Snapshot;

public class MpTimer extends MpMetric<Timer> implements org.eclipse.microprofile.metrics.Timer {

    /**
     * Creates a new instance.
     *
     * @param delegate meter which actually records data
     */
    MpTimer(Timer delegate, MeterRegistry meterRegistry) {
        super(delegate, meterRegistry);
    }

    @Override
    public void update(Duration duration) {
        delegate().record(duration);
    }

    @Override
    public <T> T time(Callable<T> callable) throws Exception {
        return delegate().recordCallable(callable);
    }

    @Override
    public void time(Runnable runnable) {
        delegate().record(runnable);
    }

    @Override
    public Context time() {
        return new Context();
    }

    @Override
    public Duration getElapsedTime() {
        return Duration.ofNanos((long) delegate().totalTime(TimeUnit.NANOSECONDS));
    }

    @Override
    public long getCount() {
        return delegate().count();
    }

    @Override
    public Snapshot getSnapshot() {
        return new MpSnapshot(delegate().takeSnapshot());
    }

    public class Context implements org.eclipse.microprofile.metrics.Timer.Context {

        private final Timer.Sample sample = Timer.start(meterRegistry());

        @Override
        public long stop() {
            return sample.stop(delegate());
        }

        @Override
        public void close() {
            stop();
        }
    }
}
