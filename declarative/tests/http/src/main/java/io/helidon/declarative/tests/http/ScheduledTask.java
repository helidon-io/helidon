/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.http;

import io.helidon.scheduling.FixedRateInvocation;

import io.helidon.scheduling.Schedule;
import io.helidon.service.registry.Service;

@Service.Singleton
class ScheduledTask {
    // every 30 seconds
    @Schedule.Cron("0/30 * * * * ?")
    void scheduled() {
        System.out.println("Scheduled cron");
    }

    @Schedule.FixedRate("PT10S")
    void fixedRate(FixedRateInvocation invocation) {
        System.out.println("Scheduled fixed rate. Iteration: " + invocation.iteration());
    }
}
