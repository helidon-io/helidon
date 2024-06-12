/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.testing.junit5;

import io.helidon.microprofile.testing.junit5.HelidonTest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@HelidonTest
class TestPinnedThread {

    @Test
    @Disabled("Enable to verify pinned threads fails")
    void test() throws InterruptedException {
        Thread.ofVirtual().start(() -> {
            synchronized (this) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.interrupted();
                }
            }
        }).join();
    }
}
