/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.compatibility.v4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.scheduling.CronInvocation;
import io.helidon.scheduling.FixedRateInvocation;
import io.helidon.scheduling.Scheduling;
import io.helidon.service.registry.Service;

@Service.Singleton
public class LegacyScheduledTask {
    private final CountDownLatch cron = new CountDownLatch(1);
    private final CountDownLatch fixedRate = new CountDownLatch(1);
    private final AtomicInteger executions = new AtomicInteger();

    @Scheduling.Cron("0/1 * * * * ?")
    public void cron(CronInvocation invocation) {
        executions.incrementAndGet();
        cron.countDown();
    }

    @Scheduling.FixedRate("PT1S")
    public void fixedRate(FixedRateInvocation invocation) {
        executions.incrementAndGet();
        fixedRate.countDown();
    }

    public CountDownLatch cronLatch() {
        return cron;
    }

    public CountDownLatch fixedRateLatch() {
        return fixedRate;
    }

    public int executions() {
        return executions.get();
    }
}
