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

package io.helidon.declarative.codegen.scheduling;

import io.helidon.common.types.TypeName;

final class SchedulingTypes {
    static final TypeName TASK = TypeName.create("io.helidon.scheduling.Task");
    static final TypeName FIXED_RATE = TypeName.create("io.helidon.scheduling.FixedRate");
    static final TypeName FIXED_RATE_ANNOTATION = TypeName.create("io.helidon.scheduling.Schedule.FixedRate");
    static final TypeName FIXED_RATE_INVOCATION = TypeName.create("io.helidon.scheduling.FixedRateInvocation");
    static final TypeName FIXED_RATE_DELAY_TYPE = TypeName.create("io.helidon.scheduling.FixedRate.DelayType");

    static final TypeName CRON = TypeName.create("io.helidon.scheduling.Cron");
    static final TypeName CRON_ANNOTATION = TypeName.create("io.helidon.scheduling.Schedule.Cron");
    static final TypeName CRON_INVOCATION = TypeName.create("io.helidon.scheduling.CronInvocation");
    private SchedulingTypes() {
    }
}
