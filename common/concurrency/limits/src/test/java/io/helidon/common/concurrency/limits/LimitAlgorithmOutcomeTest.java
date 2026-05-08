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

package io.helidon.common.concurrency.limits;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class LimitAlgorithmOutcomeTest {
    @Test
    void deferredRejectionPreservesWaitEndTime() {
        LimitAlgorithm.Outcome.Deferred deferred =
                (LimitAlgorithm.Outcome.Deferred) LimitAlgorithm.Outcome.deferredRejection("socket", "fixed", 11, 29);

        assertThat(deferred.disposition(), is(LimitAlgorithm.Outcome.Disposition.REJECTED));
        assertThat(deferred.timing(), is(LimitAlgorithm.Outcome.Timing.DEFERRED));
        assertThat(deferred.waitStartNanoTime(), is(11L));
        assertThat(deferred.waitEndNanoTime(), is(29L));
    }
}
