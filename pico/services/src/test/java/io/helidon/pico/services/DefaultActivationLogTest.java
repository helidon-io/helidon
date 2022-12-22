/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.services;

import io.helidon.pico.ActivationPhase;
import io.helidon.pico.DefaultActivationLogEntry;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class DefaultActivationLogTest {

    private static final System.Logger LOGGER = System.getLogger(DefaultActivationLogTest.class.getName());

    @Test
    void testRetainedLog() {
        DefaultActivationLog log = DefaultActivationLog.createRetainedLog(LOGGER);
        log.level(System.Logger.Level.INFO);
        log.record(DefaultActivationLogEntry.builder().targetActivationPhase(ActivationPhase.ACTIVE).build());

        assertThat(log.toQuery(), optionalPresent());
        assertThat(log.toQuery().get().fullActivationLog().size(), equalTo(1));
        assertThat(log.reset(true), equalTo(Boolean.TRUE));
        assertThat(log.reset(true), equalTo(Boolean.FALSE));
    }

    @Test
    void unretainedLog() {
        DefaultActivationLog log = DefaultActivationLog.createUnretainedLog(LOGGER);
        log.level(System.Logger.Level.INFO);
        log.record(DefaultActivationLogEntry.builder().targetActivationPhase(ActivationPhase.ACTIVE).build());

        assertThat(log.toQuery(), optionalEmpty());
    }

}
