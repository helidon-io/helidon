/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging.inner;

import io.helidon.microprofile.messaging.CountableTestBean;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public abstract class AbstractShapeTestBean implements CountableTestBean {

    public static Set<String> TEST_DATA = new HashSet<>(Arrays.asList("teST1", "TEst2", "tESt3"));
    public static Set<Integer> TEST_INT_DATA = new HashSet<>(Arrays.asList(1, 2, 3));

    public static CountDownLatch testLatch = new CountDownLatch(TEST_DATA.size());

    @Override
    public CountDownLatch getTestLatch() {
        return testLatch;
    }
}
