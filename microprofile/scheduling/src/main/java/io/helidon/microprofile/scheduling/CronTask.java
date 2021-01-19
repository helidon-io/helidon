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

package io.helidon.microprofile.scheduling;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

class CronTask implements Task {

    private static final Logger LOGGER = Logger.getLogger(CronTask.class.getName());

    private final ExecutionTime executionTime;
    private final Task.InternalTask actualTask;
    private final ScheduledExecutorService executorService;
    private final Cron cron;

    CronTask(ScheduledExecutorService executorService,
             String cronExpression,
             Task.InternalTask actualTask) {
        this.executorService = executorService;
        this.actualTask = actualTask;

        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);
        CronParser parser = new CronParser(cronDefinition);
        cron = parser.parse(cronExpression);
        executionTime = ExecutionTime.forCron(cron);

        scheduleNext();
    }

    @Override
    public void run() {
        try {
            actualTask.run();
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, e, () -> "Error when invoking scheduled method.");
        }
        scheduleNext();
    }

    @Override
    public String description() {
        return CronDescriptor.instance(Locale.ENGLISH).describe(cron);
    }

    private void scheduleNext() {
        ZonedDateTime now = ZonedDateTime.now();
        Optional<Duration> time = executionTime.timeToNextExecution(now);
        time.ifPresent(t ->
                executorService.schedule(this, t.toMillis(), TimeUnit.MILLISECONDS)
        );
    }
}
