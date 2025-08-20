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

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.scheduling.CronInvocation;
import io.helidon.scheduling.FixedRateInvocation;
import io.helidon.scheduling.Scheduling;
import io.helidon.service.registry.Service;

@Weight(Weighted.DEFAULT_WEIGHT - 10)
@Service.Singleton
class ScheduledTask2 {
    static final AtomicInteger COUNTER = new AtomicInteger();

    CompletableFuture<Boolean> cronNoParam = new CompletableFuture<>();
    CompletableFuture<CronInvocation> cronParam = new CompletableFuture<>();
    CompletableFuture<Boolean> fixedNoParam = new CompletableFuture<>();
    CompletableFuture<FixedRateInvocation> fixedParam = new CompletableFuture<>();

    // every 1 second
    @Scheduling.Cron("0/1 * * * * ?")
    void scheduled() {
        COUNTER.incrementAndGet();

        if (!cronNoParam.isDone()) {
            cronNoParam.complete(true);
        }
    }

    @Scheduling.Cron(value = "${overrides.cron:0/5 * * * * ?}")
    void scheduled(CronInvocation invocation) {
        if (!cronParam.isDone()) {
            cronParam.complete(invocation);
        }
    }

    @Scheduling.FixedRate("PT1S")
    void fixedRate() {
        if (!fixedNoParam.isDone()) {
            fixedNoParam.complete(true);
        }
    }

    @Scheduling.FixedRate(value = "${overrides.fixed.interval:PT5S}", delayBy = "${overrides.fixed.delay-by:PT1S}")
    void fixedRate(FixedRateInvocation invocation) {
        if (!fixedParam.isDone()) {
            fixedParam.complete(invocation);
        }
    }
}
