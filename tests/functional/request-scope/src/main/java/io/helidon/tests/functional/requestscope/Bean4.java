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

import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;

@ApplicationScoped
public class Bean4 {

    private AtomicLong counter = new AtomicLong(0);

    @Retry(jitter = 1000L, delay = 3)
    @Fallback(fallbackMethod = "testFallback")
    public String test(String testParam) throws Exception {
        throw new RuntimeException("called failed");
    }

    public String testFallback(String testParam) throws Exception {
        return testParam;
    }
}
