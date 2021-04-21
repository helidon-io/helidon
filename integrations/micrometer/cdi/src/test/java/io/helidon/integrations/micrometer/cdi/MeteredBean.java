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
 *
 */
package io.helidon.integrations.micrometer.cdi;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;

public class MeteredBean {

    static final String COUNTED = "counted";
    static final String TIMED_1 = "timed-1";
    static final String TIMED_A = "timed-a";
    static final String TIMED_B = "timed-b";
    static final String COUNTED_ONLY_FOR_FAILURE = "counted-only-for-failure";

    @Counted(COUNTED)
    public void count() {
    }

    @Timed(TIMED_1)
    public void timed() {
    }

    @Timed(TIMED_A)
    @Timed(TIMED_B)
    public void timedA() {
    }

    @Counted(value = COUNTED_ONLY_FOR_FAILURE, recordFailuresOnly = true)
    public void failable(boolean shouldFail) {
        if (shouldFail) {
            throw new RuntimeException("shouldFail");
        }
    }
}
