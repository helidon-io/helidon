/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

import javax.inject.Inject;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test overrides using method overrides.
 */
@AddBean(RetryBean.class)
@AddConfig(key = "io.helidon.microprofile.faulttolerance.RetryBean/retryOne/Retry/maxRetries", value = "0")
@AddConfig(key = "io.helidon.microprofile.faulttolerance.RetryBean/retryWithFallback/Retry/maxRetries", value = "3")
class ConfigMethodTest extends FaultToleranceTest {

    @Inject
    private RetryBean bean;

    @Test
    void testRetryOverrideMethod() {
        bean.retry();       // passes due to class annotation
    }

    @Test
    void testRetryWithFallbackOverrideMethod() {
        assertThat(bean.retryWithFallback(), is("success"));      // passes no fallback
    }
}
