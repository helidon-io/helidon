/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive.valve;

import java.util.concurrent.ForkJoinPool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloseableSupportTest {

    @Test
    void singleThread() {
        CloseableSupport cs = new CloseableSupport();
        assertFalse(cs.closed());
        cs.close();
        assertTrue(cs.closed());
    }

    @Test
    void otherThread() throws Exception {
        CloseableSupport cs = new CloseableSupport();
        assertFalse(cs.closed());
        ForkJoinPool.commonPool().submit(cs::close).get();
        assertTrue(cs.closed());
    }
}
