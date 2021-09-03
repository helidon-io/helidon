/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import java.time.temporal.ChronoUnit;

import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

/**
 * A bean with timeout methods.
 */
class TimeoutNoRetryBean {

    @Timeout(value=1000, unit=ChronoUnit.MILLIS)
    void forceTimeoutSleep() throws InterruptedException, TimeoutException {
        FaultToleranceTest.printStatus("TimeoutNoRetryBean::forceTimeout()", "failure");
        Thread.sleep(2000);
    }

    @Timeout(value=1000, unit=ChronoUnit.MILLIS)
    void forceTimeoutLoop() throws TimeoutException {
        FaultToleranceTest.printStatus("TimeoutNoRetryBean::forceTimeoutLoop()", "failure");
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 2000) {
            // busy loop
        }
    }
}
