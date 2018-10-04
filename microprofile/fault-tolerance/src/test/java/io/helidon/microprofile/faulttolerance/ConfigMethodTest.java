/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This test class should run in its own VM to avoid clashes with system
 * properties. Our Config provider caches system properties so removing a
 * system property does not prevent other tests to be affected by it.
 */
public class ConfigMethodTest extends FaultToleranceTest {

    static {
        System.setProperty(RetryBean.class.getName() + "/retryOne/Retry/maxRetries", "0");
        System.setProperty(RetryBean.class.getName() + "/retryWithFallback/Retry/maxRetries", "3");
    }

    @Test
    public void testRetryOverrideMethod() {
        RetryBean bean = newBean(RetryBean.class);
        bean.retry();   // passes due to class annotation
    }

    @Test
    public void testRetryWithFallbackOverrideMethod() {
        RetryBean bean = newBean(RetryBean.class);
        assertEquals("success", bean.retryWithFallback());      // passes no fallback
    }
}
