/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.common.testing;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.MapMatcher.mapEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

class MapMatcherTest {

    @Test
    void testIsMapEqual() {
        assertThat(Map.of("foo", "bar"), is(mapEqualTo(Map.of("foo", "bar"))));
        assertThat(Map.of("bar", "foo"), is(not(mapEqualTo(Map.of("foo", "bar")))));

        assertThat(Map.of("foo", Map.of("bar", Map.of("bob", "alice"))),
                   is(mapEqualTo(Map.of("foo", Map.of("bar", Map.of("bob", "alice"))))));

        assertThat(Map.of("foo", Map.of("bar", Map.of("bob", "alice"))),
                   is(not(mapEqualTo(Map.of("foo", Map.of("bar", Map.of("bob", "not-alice")))))));

        assertThat(Map.of("foo", "bar", "bob", "alice"), is(mapEqualTo(Map.of("bob", "alice", "foo", "bar"))));
    }
}
