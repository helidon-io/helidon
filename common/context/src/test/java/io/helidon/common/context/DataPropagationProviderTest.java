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
package io.helidon.common.context;

import io.helidon.common.context.spi.DataPropagationProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

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

        /**
         * Deprecated method should be called.
         */
        @Override
        public void clearData() {
            called = true;
        }
    }

    @Test
    void testDeprecation() {
        MyDataPropagationTest dpt = new MyDataPropagationTest();
        dpt.clearData("foo");       // should call deprecated method
        assertThat(called, is(true));
    }
}
