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

package io.helidon.pico.builder.runtime.tools;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static io.helidon.pico.builder.runtime.tools.BeanUtils.isBooleanType;
import static io.helidon.pico.builder.runtime.tools.BeanUtils.isValidMethodType;
import static io.helidon.pico.builder.runtime.tools.BeanUtils.validateAndParseMethodName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeanUtilsTest {

    @Test
    public void testIsBooleanType() {
        assertTrue(isBooleanType(boolean.class));
        assertTrue(isBooleanType(Boolean.class));
        assertFalse(isBooleanType(String.class));
        assertFalse(isBooleanType(""));
    }

    @Test
    public void testIsValidMethodType() {
        assertTrue(isValidMethodType(boolean.class.getName()));
        assertTrue(isValidMethodType(String.class.getName()));
        assertTrue(isValidMethodType(Object.class.getName()));
        assertFalse(isValidMethodType(null));
        assertFalse(isValidMethodType(""));
        assertFalse(isValidMethodType(Void.class.getName()));
        assertFalse(isValidMethodType(void.class.getName()));
    }

    @Test
    public void testValidateAndParseMethodName() {
        AtomicReference<List<String>> attrName = new AtomicReference<>(Collections.emptyList());
        Supplier<String> messageSupplier = () -> "test message";

        RuntimeException e = assertThrows(RuntimeException.class,
                      () -> validateAndParseMethodName("x", "", true, attrName));
        assertEquals("invalid return type: x", e.getMessage());
        assertNull(attrName.get());

        assertTrue(validateAndParseMethodName("isAlpha", Boolean.class.getName(), false, attrName));
        assertEquals("[alpha, isAlpha]", String.valueOf(attrName.get()));

        assertTrue(validateAndParseMethodName("isAlpha", boolean.class.getName(), false, attrName));
        assertEquals("[alpha, isAlpha]", String.valueOf(attrName.get()));

        assertTrue(validateAndParseMethodName("getAlpha", boolean.class.getName(), false, attrName));
        assertEquals("[alpha]", String.valueOf(attrName.get()));

        assertTrue(validateAndParseMethodName("getAlpha", Boolean.class.getName(), false, attrName));
        assertEquals("[alpha]", String.valueOf(attrName.get()));

        assertTrue(validateAndParseMethodName("getAlpha", String.class.getName(), false, attrName));
        assertEquals("[alpha]", String.valueOf(attrName.get()));

        assertTrue(validateAndParseMethodName("getAlpha", Object.class.getName(), false, attrName));
        assertEquals("[alpha]", String.valueOf(attrName.get()));

        assertTrue(validateAndParseMethodName("isAlphaNumeric", boolean.class.getName(), false, attrName));
        assertEquals("[alphaNumeric, isAlphaNumeric]", String.valueOf(attrName.get()));

        assertTrue(validateAndParseMethodName("isAlphaNumeric", Boolean.class.getName(), false, attrName));
        assertEquals("[alphaNumeric, isAlphaNumeric]", String.valueOf(attrName.get()));

        assertTrue(validateAndParseMethodName("getAlphaNumeric", boolean.class.getName(), false, attrName));
        assertEquals("[alphaNumeric]", String.valueOf(attrName.get()));

        assertTrue(validateAndParseMethodName("getAlphaNumeric", Boolean.class.getName(), false, attrName));
        assertEquals("[alphaNumeric]", String.valueOf(attrName.get()));

        assertTrue(validateAndParseMethodName("getAlphaNumeric", String.class.getName(), false, attrName));
        assertEquals("[alphaNumeric]", String.valueOf(attrName.get()));

        assertTrue(validateAndParseMethodName("isX", boolean.class.getName(), false, attrName));
        assertEquals("[x, isX]", String.valueOf(attrName.get()));

        assertTrue(validateAndParseMethodName("getX", boolean.class.getName(), false, attrName));
        assertEquals("[x]", String.valueOf(attrName.get()));

        // negative cases ...
        assertFalse(validateAndParseMethodName("isAlphaNumeric", String.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("is_AlphaNumeric", boolean.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("is", boolean.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("is", Boolean.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("get", boolean.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("get", Boolean.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("get1AlphaNumeric", boolean.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("getalphaNumeric", boolean.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("isalphaNumeric", boolean.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("is9AlphaNumeric", boolean.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("isAlphaNumeric", void.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("getAlphaNumeric", Void.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("x", Integer.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("IsX", Boolean.class.getName(), false, attrName));
        assertNull(attrName.get());

        assertFalse(validateAndParseMethodName("GetX", Integer.class.getName(), false, attrName));
        assertNull(attrName.get());
    }


}
