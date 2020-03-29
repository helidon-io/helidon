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

/**
 * Class TimedTest.
 */
public class TimedTest {

    private final int ASSERT_LOOPS = 10;
    private final long SLEEP_TIME = 200;

    protected void assertEventually(Runnable r) throws Exception {
        for (int i = 0; i < ASSERT_LOOPS; i++) {
            try {
                r.run(); return;
            } catch (AssertionError e) {
                if (i == ASSERT_LOOPS - 1) {
                    throw e;
                }
                Thread.sleep(SLEEP_TIME);
            }
        }
    }
}
