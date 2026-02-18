/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.time.ZoneId;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Blueprint
@Prototype.Configured
interface CronConfigBlueprint extends TaskConfigBlueprint, Prototype.Factory<Cron> {

    /**
     * Cron expression for specifying period of execution.
     * <p>
     * <b>Examples:</b>
     * <ul>
     * <li>{@code 0/2 * * * * ? *} - Every 2 seconds</li>
     * <li>{@code 0 45 9 ? * *} - Every day at 9:45</li>
     * <li>{@code 0 15 8 ? * MON-FRI} - Every workday at 8:15</li>
     * </ul>
     *
     * @return cron expression
     */
    @Option.Configured
    @Option.Required
    String expression();

    /**
     * Time zone to use for cron expression evaluation.
     * Defaults to {@link java.time.ZoneId#systemDefault()}.
     * <p>
     * The time zone determines when the cron expression triggers. For example,
     * a cron expression {@code 0 0 9 * * ?} (every day at 9:00 AM) with zone
     * {@code America/New_York} will trigger at 9:00 AM Eastern Time, regardless
     * of the system's default time zone.
     *
     * @return time zone for cron expression evaluation
     */
    @Option.Configured
    @Option.DefaultCode("@java.time.ZoneId@.systemDefault()")
    ZoneId zone();

    /**
     * Allow concurrent execution if previous task didn't finish before next execution.
     * Default value is {@code true}.
     *
     * @return true for allow concurrent execution.
     */
    @Option.Configured("concurrent")
    @Option.DefaultBoolean(true)
    boolean concurrentExecution();

    /**
     * Task to be scheduled for execution.
     *
     * @return scheduled for execution
     */
    ScheduledConsumer<CronInvocation> task();
}
