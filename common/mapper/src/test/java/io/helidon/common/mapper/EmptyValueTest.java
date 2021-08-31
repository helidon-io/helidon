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

package io.helidon.common.mapper;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class EmptyValueTest {
    @Test
    void testAsOptionalString() {
        String name = "name";
        EmptyValue<String> value = new EmptyValue<>(name);

        assertThat(value.name(), is(name));
        assertThat(value.asOptional(), is(Optional.empty()));
    }
    @Test
    void testAsMapperToString() {
        String name = "name";
        EmptyValue<String> value = new EmptyValue<>(name);

        Value<String> asString = value.as(String::valueOf);
        assertThat(asString.name(), is(name));
        assertThat(asString.asOptional(), is(Optional.empty()));
    }
}
