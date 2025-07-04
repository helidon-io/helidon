/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Blueprint(decorator = TaskConfigDecorator.class)
@Prototype.Configured
interface TaskConfigBlueprint {

    /**
     * Custom {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} used for executing scheduled task.
     *
     * @return custom ScheduledExecutorService
     */
    ScheduledExecutorService executor();

    /**
     * Task manager that will manage the created task.
     *
     * @return task manager, by default taken from the global service registry
     */
    @Option.RegistryService
    TaskManager taskManager();

    /**
     * Identification of the started task. This can be used to later look up the instance, for example to cancel it.
     *
     * @return task id, if not provided, a unique id will be generated
     */
    Optional<String> id();
}
