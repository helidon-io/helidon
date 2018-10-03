/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Class MethodAntnTest.
 */
public class MethodAntnTest {

    @Test
    public void testParseArray() {
        assertArrayEquals(
            MethodAntn.parseThrowableArray("{ java.lang.Throwable, " +
                                           "  java.lang.Exception, " +
                                           "  java.lang.RuntimeException }"),
            new Class[]{Throwable.class, Exception.class, RuntimeException.class});
    }

    @Test
    public void testParseArrayClassSuffix() {
        assertArrayEquals(
            MethodAntn.parseThrowableArray("{ java.lang.Throwable.class, " +
                                           "  java.lang.Exception.class, " +
                                           "  java.lang.RuntimeException.class }"),
            new Class[]{Throwable.class, Exception.class, RuntimeException.class});
    }

    @Test
    public void testParseArrayEmpty() {
        assertArrayEquals(
            MethodAntn.parseThrowableArray("{ }"),
            new Class[]{});
    }

    @Test
    public void testParseArrayError() {
        assertThrows(RuntimeException.class,
                     () -> MethodAntn.parseThrowableArray("{ Foo.class }"));
        assertThrows(RuntimeException.class,
                     () -> MethodAntn.parseThrowableArray("[ Foo.class ]"));
    }
}
