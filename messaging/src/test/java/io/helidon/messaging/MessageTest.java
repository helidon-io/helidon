/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.messaging;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageTest {
    @Test
    void exposesImmutableSingleValuedPortableHeaders() {
        Message.Builder<String> builder = Message.builder("payload")
                .header("trace", "first")
                .header("Trace", "case-sensitive")
                .header("trace", "last");

        Message<String> message = builder.build();
        builder.header("trace", "after-build");

        assertThat(message.headers(), is(Map.of("trace", "last", "Trace", "case-sensitive")));
        assertThat(message.header("trace").orElseThrow(), is("last"));
        assertThat(message.header("Trace").orElseThrow(), is("case-sensitive"));
        assertThrows(UnsupportedOperationException.class, () -> message.headers().put("new", "value"));
    }
}
