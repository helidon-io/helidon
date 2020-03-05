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

package io.helidon.config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ConfigChangeListener {
    private CountDownLatch cdl = new CountDownLatch(1);
    private volatile Config updatedConfig;

    public void onChange(Config config) {
        updatedConfig = config;
        cdl.countDown();
    }

    /**
     * Get the last obtained changed config.
     *
     * @param millisToWait milliseconds to wait for the event to happen
     * @param expectedAwait expected result of the count down latch timeout (true for success, false for timeout)
     * @return config instance or null if none was received
     */
    public Config get(long millisToWait, boolean expectedAwait) {
        try {
            boolean result = cdl.await(millisToWait, TimeUnit.MILLISECONDS);
            assertThat(result, is(expectedAwait));
        } catch (InterruptedException e) {
            // ignored
        }
        return updatedConfig;
    }

    public void reset() {
        cdl = new CountDownLatch(1);
        updatedConfig = null;
    }
}
