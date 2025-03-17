/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.scheduling;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.scheduling.CronInvocation;
import io.helidon.scheduling.FixedRateInvocation;
import io.helidon.scheduling.Schedule;
import io.helidon.service.registry.Service;

@Service.Singleton
class ScheduledTask {
    static final AtomicInteger COUNTER = new AtomicInteger();

    CompletableFuture<Boolean> cronNoParam = new CompletableFuture<>();
    CompletableFuture<CronInvocation> cronParam = new CompletableFuture<>();
    CompletableFuture<Boolean> fixedNoParam = new CompletableFuture<>();
    CompletableFuture<FixedRateInvocation> fixedParam = new CompletableFuture<>();

    // every 1 second
    @Schedule.Cron("0/1 * * * * ?")
    void scheduled() {
        COUNTER.incrementAndGet();

        if (!cronNoParam.isDone()) {
            cronNoParam.complete(true);
        }
    }

    @Schedule.Cron(value = "0/5 * * * * ?", configKey = "override.cron")
    void scheduled(CronInvocation invocation) {
        if (!cronParam.isDone()) {
            cronParam.complete(invocation);
        }
    }

    @Schedule.FixedRate("PT1S")
    void fixedRate() {
        if (!fixedNoParam.isDone()) {
            fixedNoParam.complete(true);
        }
    }

    @Schedule.FixedRate(value = "PT5S", delayBy = "PT1S", configKey = "override.fixed")
    void fixedRate(FixedRateInvocation invocation) {
        if (!fixedParam.isDone()) {
            fixedParam.complete(invocation);
        }
    }
}
