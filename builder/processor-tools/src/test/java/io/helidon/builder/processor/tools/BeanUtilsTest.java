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

package io.helidon.builder.processor.tools;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.testing.junit5.OptionalMatcher;

import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.helidon.builder.processor.tools.BeanUtils.isBooleanType;
import static io.helidon.builder.processor.tools.BeanUtils.isValidMethodType;
import static io.helidon.builder.processor.tools.BeanUtils.validateAndParseMethodName;
import static org.hamcrest.MatcherAssert.assertThat;

class BeanUtilsTest {

    @Test
    void testIsBooleanType() {
        assertThat(isBooleanType(boolean.class), Is.is(true));
        assertThat(isBooleanType(Boolean.class), Is.is(true));
        assertThat(isBooleanType(String.class), Is.is(false));
        assertThat(isBooleanType(""), Is.is(false));
    }

    @Test
    void testIsValidMethodType() {
        assertThat(isValidMethodType(boolean.class.getName()), Is.is(true));
        assertThat(isValidMethodType(String.class.getName()), Is.is(true));
        assertThat(isValidMethodType(Collection.class.getName()), Is.is(true));
        assertThat(isValidMethodType(Map.class.getName()), Is.is(true));
        assertThat(isValidMethodType(Set.class.getName()), Is.is(true));
        assertThat(isValidMethodType(List.class.getName()), Is.is(true));
        assertThat(isValidMethodType(Object.class.getName()), Is.is(true));
        assertThat(isValidMethodType(""), Is.is(false));
        assertThat(isValidMethodType(void.class.getName()), Is.is(false));
        assertThat(isValidMethodType(Void.class.getName()), Is.is(false));
    }

    @Test
    void testValidateAndParseMethodName() {
        AtomicReference<Optional<List<String>>> attrName = new AtomicReference<>();

        RuntimeException e = Assertions.assertThrows(RuntimeException.class,
                                                     () -> validateAndParseMethodName("x", "", true, attrName));
        assertThat(e.getMessage(), Is.is("invalid return type: x"));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("isAlpha", Boolean.class.getName(), false, attrName), Is.is(true));
        assertThat(attrName.get(), OptionalMatcher.optionalValue(Matchers.contains("alpha", "isAlpha")));

        assertThat(validateAndParseMethodName("isAlpha", boolean.class.getName(), false, attrName), Is.is(true));
        assertThat(attrName.get(), OptionalMatcher.optionalValue(Matchers.contains("alpha", "isAlpha")));

        assertThat(validateAndParseMethodName("getAlpha", boolean.class.getName(), false, attrName), Is.is(true));
        assertThat(attrName.get(), OptionalMatcher.optionalValue(Matchers.contains("alpha")));

        assertThat(validateAndParseMethodName("getAlpha", Boolean.class.getName(), false, attrName), Is.is(true));
        assertThat(attrName.get(), OptionalMatcher.optionalValue(Matchers.contains("alpha")));

        assertThat(validateAndParseMethodName("getAlpha", String.class.getName(), false, attrName), Is.is(true));
        assertThat(attrName.get(), OptionalMatcher.optionalValue(Matchers.contains("alpha")));

        assertThat(validateAndParseMethodName("getAlpha", Object.class.getName(), false, attrName), Is.is(true));
        assertThat(attrName.get(), OptionalMatcher.optionalValue(Matchers.contains("alpha")));

        assertThat(validateAndParseMethodName("isAlphaNumeric", boolean.class.getName(), false, attrName), Is.is(true));
        assertThat(attrName.get(), OptionalMatcher.optionalValue(Matchers.contains("alphaNumeric", "isAlphaNumeric")));

        assertThat(validateAndParseMethodName("getAlphaNumeric", boolean.class.getName(), false, attrName), Is.is(true));
        assertThat(attrName.get(), OptionalMatcher.optionalValue(Matchers.contains("alphaNumeric")));

        assertThat(validateAndParseMethodName("isX", boolean.class.getName(), false, attrName), Is.is(true));
        assertThat(attrName.get(), OptionalMatcher.optionalValue(Matchers.contains("x", "isX")));

        assertThat(validateAndParseMethodName("getX", boolean.class.getName(), false, attrName), Is.is(true));
        assertThat(attrName.get(), OptionalMatcher.optionalValue(Matchers.contains("x")));

        // negative cases ...
        assertThat(validateAndParseMethodName("isX", String.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("is_AlphaNumeric", boolean.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("is", boolean.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("is", Boolean.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("get", boolean.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("get", Boolean.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("get1AlphaNumeric", Boolean.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("getalphaNumeric", boolean.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("isalphaNumeric", boolean.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("is9alphaNumeric", boolean.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("isAlphaNumeric", void.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("getAlphaNumeric", Void.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("x", boolean.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("IsX", boolean.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());

        assertThat(validateAndParseMethodName("GetX", boolean.class.getName(), false, attrName), Is.is(false));
        assertThat(attrName.get(), OptionalMatcher.optionalEmpty());
    }

}
