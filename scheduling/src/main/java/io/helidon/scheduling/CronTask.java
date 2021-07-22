/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.scheduling;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import static com.cronutils.model.CronType.QUARTZ;

class CronTask implements Task {

    private static final Logger LOGGER = Logger.getLogger(CronTask.class.getName());

    private final AtomicLong iteration = new AtomicLong(0);
    private final ExecutionTime executionTime;
    private final boolean concurrentExecution;
    private final ScheduledConsumer<CronInvocation> actualTask;
    private final ScheduledExecutorService executorService;
    private final Cron cron;
    private final ReentrantLock scheduleNextLock = new ReentrantLock();
    private ZonedDateTime lastNext = null;

    CronTask(ScheduledExecutorService executorService,
             String cronExpression,
             boolean concurrentExecution,
             ScheduledConsumer<CronInvocation> actualTask) {
        this.executorService = executorService;
        this.concurrentExecution = concurrentExecution;
        this.actualTask = actualTask;

        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);
        CronParser parser = new CronParser(cronDefinition);
        cron = parser.parse(cronExpression);
        executionTime = ExecutionTime.forCron(cron);

        scheduleNext();
    }

    void run() {
        if (concurrentExecution) {
            scheduleNext();
        }
        try {
            long it = iteration.incrementAndGet();
            actualTask.run(new CronInvocation() {
                @Override
                public String cron() {
                    return cron.asString();
                }

                @Override
                public boolean concurrent() {
                    return concurrentExecution;
                }

                @Override
                public long iteration() {
                    return it;
                }

                @Override
                public String description() {
                    return CronTask.this.description();
                }
            });
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, e, () -> "Error when invoking scheduled method.");
        }
        if (!concurrentExecution) {
            scheduleNext();
        }
    }

    @Override
    public String description() {
        return CronDescriptor.instance(Locale.ENGLISH).describe(cron);
    }

    @Override
    public ScheduledExecutorService executor() {
        return this.executorService;
    }

    private void scheduleNext() {
        try {
            scheduleNextLock.lock();

            ZonedDateTime now = ZonedDateTime.now();
            Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(now);
            if (nextExecution.isEmpty()) {
                return;
            }

            ZonedDateTime next = nextExecution.get();

            Optional<Duration> time;
            if (lastNext != null && lastNext.isEqual(next)) {
                lastNext = executionTime.nextExecution(now).orElse(null);
                time = executionTime.timeToNextExecution(next);
            } else {
                lastNext = next;
                time = executionTime.timeToNextExecution(now);
            }

            time.ifPresent(t -> {
                        executorService.schedule(this::run, t.toMillis(), TimeUnit.MILLISECONDS);
                    }
            );

        } finally {
            scheduleNextLock.unlock();
        }
    }
}
