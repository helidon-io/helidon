/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;

class FixedRateTask implements FixedRate {

    private static final System.Logger LOGGER = System.getLogger(FixedRateTask.class.getName());

    private final AtomicLong iteration = new AtomicLong(0);
    private final ScheduledExecutorService executorService;
    private final long initialDelay;
    private final long delay;
    private final TimeUnit timeUnit;
    private final ScheduledConsumer actualTask;
    private FixedRateConfig config = null;

    FixedRateTask(FixedRateConfig config) {
        this.config = config;

        this.initialDelay = config.initialDelay();
        this.delay = config.delay();
        this.timeUnit = config.timeUnit();
        this.actualTask = config.task();

        if (config.executor() == null) {
            executorService = ScheduledThreadPoolSupplier.builder()
                    .threadNamePrefix("scheduled-")
                    .build()
                    .get();
        } else {
            this.executorService = config.executor();
        }

        switch (config.delayType()) {
        case SINCE_PREVIOUS_START -> executorService.scheduleAtFixedRate(this::run, initialDelay, delay, timeUnit);
        case SINCE_PREVIOUS_END -> executorService.scheduleWithFixedDelay(this::run, initialDelay, delay, timeUnit);
        default -> throw new IllegalStateException("Unexpected delay type " + config.delayType());
        }
    }

    @Override
    public FixedRateConfig prototype() {
        return config;
    }

    @Override
    public String description() {
        String unit = timeUnit.toString().toLowerCase();
        if (initialDelay == 0) {
            return String.format("every %s %s", delay, unit);
        }
        return String.format("every %s %s with initial delay %s %s",
                delay, unit, initialDelay, unit);
    }

    @Override
    public ScheduledExecutorService executor() {
        return this.executorService;
    }

    void run() {
        try {
            long it = iteration.incrementAndGet();
            actualTask.run(new FixedRateInvocation() {
                @Override
                public long initialDelay() {
                    return initialDelay;
                }

                @Override
                public long delay() {
                    return delay;
                }

                @Override
                public TimeUnit timeUnit() {
                    return timeUnit;
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
