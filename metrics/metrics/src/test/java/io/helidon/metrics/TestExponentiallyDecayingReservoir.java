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
package io.helidon.metrics;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class TestExponentiallyDecayingReservoir {

    @BeforeAll
    static void startExecutor() {
        ExponentiallyDecayingReservoir.init();
    }

    @AfterAll
    static void stopExecutor() {
        ExponentiallyDecayingReservoir.onServerShutdown();
    }

    @Test
    void checkCurrentTimeInSeconds() throws InterruptedException {
        ExponentiallyDecayingReservoir edr = new ExponentiallyDecayingReservoir(Clock.system());
        long startTime = edr.currentTimeInSeconds();
        Thread.sleep(1100);
        assertThat("Difference in current time across a short delay", edr.currentTimeInSeconds() - startTime,
                is(greaterThan(0L)));
    }
}
