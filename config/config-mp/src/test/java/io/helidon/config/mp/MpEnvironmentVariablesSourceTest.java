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
package io.helidon.config.mp;

import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class MpEnvironmentVariablesSourceTest {

    private static final int MAX_GROWTH = 10;

    @Test
    void testCacheMaxGrowth() {
        MpEnvironmentVariablesSource source = new MpEnvironmentVariablesSource(MAX_GROWTH);
        assertThat(source.cache().size(), is(0));
        IntStream.range(0, 5 * MAX_GROWTH).forEach(i -> {
            String random = UUID.randomUUID().toString().toLowerCase();
            source.getValue(random);        // should cache and discard oldest after MAX_GROWTH
        });
        assertThat(source.cache().size(), is(MAX_GROWTH));
    }
}
