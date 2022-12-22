/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.config.services.impl;

import java.util.Optional;

import io.helidon.pico.config.services.StringValueParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StringValueParsersTest {

    @Test
    public void testIt() {
        StringValueParser parser = new DefaultStringValueParsers();

        assertEquals(Optional.empty(), parser.parse(null, int.class));
        assertEquals(Optional.of(8080), parser.parse("8080", int.class));
        assertEquals(Optional.of(0), parser.parse("0", Integer.class));
        assertThrows(RuntimeException.class, () -> parser.parse("abc", Integer.class));

        assertEquals(Optional.empty(), parser.parse(null, long.class));
        assertEquals(Optional.of(8080L), parser.parse("8080", long.class));
        assertEquals(Optional.of(0L), parser.parse("0", Long.class));
        assertThrows(RuntimeException.class, () -> parser.parse("abc", Long.class));

        assertEquals(Optional.empty(), parser.parse(null, float.class));
        assertEquals(Optional.of(8080.1f), parser.parse("8080.1", float.class));
        assertEquals(Optional.of(0.1f), parser.parse("0.1", Float.class));
        assertThrows(RuntimeException.class, () -> parser.parse("abc", Float.class));

        assertEquals(Optional.empty(), parser.parse(null, double.class));
        assertEquals(Optional.of(8080.1d), parser.parse("8080.1", double.class));
        assertEquals(Optional.of(0.1d), parser.parse("0.1", Double.class));
        assertThrows(RuntimeException.class, () -> parser.parse("abc", Double.class));

        assertEquals(Optional.empty(), parser.parse(null, boolean.class));
        assertEquals(Optional.of(true), parser.parse("true", boolean.class));
        assertEquals(Optional.of(false), parser.parse("false", Boolean.class));
        assertEquals(Optional.of(false), parser.parse("abc", Boolean.class));

        assertEquals(Optional.empty(), parser.parse(null, String.class));
        assertEquals(Optional.of("true"), parser.parse("true", String.class));
        assertEquals(Optional.of("false"), parser.parse("false", String.class));

        assertEquals(Optional.empty(), parser.parse(null, char[].class));
        assertEquals("true", new String(parser.parse("true", char[].class).get()));
        assertEquals("false", new String(parser.parse("false", char[].class).get()));
    }

}
