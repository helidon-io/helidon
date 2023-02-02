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

package io.helidon.pico.config.services;

import java.util.Optional;

import io.helidon.pico.builder.config.spi.StringValueParser;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringValueParsersTest {

    @Test
    void testIt() {
        StringValueParser parser = new DefaultStringValueParser();

        assertThat(parser.parse("8080", int.class), optionalValue(equalTo(8080)));
        assertThat(parser.parse("0", Integer.class), optionalValue(equalTo(0)));
        assertThrows(RuntimeException.class, () -> parser.parse("abc", Integer.class));

        assertThat(parser.parse("8080", long.class), optionalValue(equalTo(8080L)));
        assertThat(parser.parse("1", Long.class), optionalValue(equalTo(1L)));
        assertThrows(RuntimeException.class, () -> parser.parse("abc", Long.class));

        assertThat(parser.parse("8080.1", float.class), optionalValue(equalTo(8080.1f)));
        assertThat(parser.parse("1.1", Float.class), optionalValue(equalTo(1.1f)));
        assertThrows(RuntimeException.class, () -> parser.parse("abc", Float.class));

        assertThat(parser.parse("8080.1", double.class), optionalValue(equalTo(8080.1d)));
        assertThat(parser.parse("1.1", Double.class), optionalValue(equalTo(1.1d)));
        assertThrows(RuntimeException.class, () -> parser.parse("abc", Double.class));

        assertThat(parser.parse("true", boolean.class), optionalValue(equalTo(true)));
        assertThat(parser.parse("true", Boolean.class), optionalValue(equalTo(true)));
        assertThat(parser.parse("false", boolean.class), optionalValue(equalTo(false)));
        assertThat(parser.parse("false", Boolean.class), optionalValue(equalTo(false)));
        assertThat(parser.parse("whatever", boolean.class), optionalValue(equalTo(false)));
        assertThat(parser.parse("whatever", Boolean.class), optionalValue(equalTo(false)));

        assertEquals(Optional.empty(), parser.parse(null, String.class));
        assertThat(parser.parse("true", String.class), optionalValue(equalTo("true")));
        assertThat(parser.parse("false", String.class), optionalValue(equalTo("false")));

        assertThat(new String(parser.parse("true", char[].class).get()), equalTo("true"));
        assertThat(new String(parser.parse("false", char[].class).get()), equalTo("false"));
    }

}
