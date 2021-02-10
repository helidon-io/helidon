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
 */
package io.helidon.tests.functional.requestscope;

import java.util.concurrent.atomic.AtomicLong;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;

@ApplicationScoped
public class Bean2 {

    private AtomicLong counter = new AtomicLong(0);

    @Inject
    private TenantContext tenantContext;

    @CircuitBreaker(successThreshold = 2, requestVolumeThreshold = 4)
    @Fallback(fallbackMethod = "testFallback")
    public String test() {
        maybeFail();
        return tenantContext.getTenantId();
    }

    public String testFallback() {
        return tenantContext.getTenantId();
    }

    private void maybeFail() {
        final long invocationNumber = counter.getAndIncrement();
        if (invocationNumber % 4 > 1) {     // alternate 2 successful and 2 failing invocations
            throw new RuntimeException("Service failed.");
        }
    }
}
