/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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
package io.helidon.common.context;

import io.helidon.common.context.spi.DataPropagationProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Verifies backward compatibility of SPI after method deprecation.
 */
class DataPropagationProviderTest {

    private boolean called = false;

    class MyDataPropagationTest implements DataPropagationProvider<Object> {

        @Override
        public Object data() {
            return null;
        }

        @Override
        public void propagateData(Object data) {
        }

        @Override
        public void clearData(Object data) {
            called = true;
        }
    }

    @Test
    void testDeprecation() {
        MyDataPropagationTest dpt = new MyDataPropagationTest();
        dpt.clearData("foo");
        assertThat(called, is(true));
    }
}
