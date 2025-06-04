/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

package io.helidon.scheduling;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class FixedRateTask implements FixedRate {

    private static final System.Logger LOGGER = System.getLogger(FixedRateTask.class.getName());

    private final AtomicLong iteration = new AtomicLong(0);
    private final ScheduledExecutorService executorService;
    private final Duration initialDelay;
    private final Duration interval;
    private final ScheduledConsumer<FixedRateInvocation> actualTask;
    private final ScheduledFuture<?> future;
    private final FixedRateConfig config;

    FixedRateTask(FixedRateConfig config) {
        this.config = config;

        this.initialDelay = config.delayBy();
        this.interval = config.interval();
        this.actualTask = config.task();

        this.executorService = config.executor();

        this.future = switch (config.delayType()) {
            case SINCE_PREVIOUS_START -> executorService.scheduleAtFixedRate(this::run,
                                                                             initialDelay.toMillis(),
                                                                             interval.toMillis(),
                                                                             TimeUnit.MILLISECONDS);
            case SINCE_PREVIOUS_END -> executorService.scheduleWithFixedDelay(this::run,
                                                                              initialDelay.toMillis(),
                                                                              interval.toMillis(),
                                                                              TimeUnit.MILLISECONDS);
        };
    }

    @Override
    public FixedRateConfig prototype() {
        return config;
    }

    @Override
    public String description() {
        if (initialDelay.isZero()) {
            return String.format("every %s", interval);
        }
        return String.format("every %s with initial delay of %s ",
                             interval, initialDelay);
    }

    @Override
    public ScheduledExecutorService executor() {
        return this.executorService;
    }

    @Override
    public void close() {
        future.cancel(false);
    }

    void run() {
        try {
            long it = iteration.incrementAndGet();
            actualTask.run(new FixedRateInvocation() {
                @Override
                public Duration delayBy() {
                    return initialDelay;
                }

                @Override
                public Duration interval() {
                    return interval;
                }

                @Override
                public long initialDelay() {
                    return initialDelay.toMillis();
                }

                @Override
                public long delay() {
                    return interval.toMillis();
                }

                @Override
                public TimeUnit timeUnit() {
                    return TimeUnit.MILLISECONDS;
                }

                @Override
                public long iteration() {
                    return it;
                }

                @Override
                public String description() {
                    return FixedRateTask.this.description();
                }
            });
        } catch (Throwable e) {
            LOGGER.log(Level.ERROR, () -> "Error when invoking scheduled method.", e);
        }
    }
}
