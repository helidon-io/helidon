/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.tracing;

import java.math.BigInteger;
import java.util.Map;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link Tag}.
 */
class TagTest {
    private static MockTracer tracer;

    @BeforeAll
    static void initClass() {
        tracer = new MockTracer();
    }

    @Test
    void testStringTag() {
        String key = "theKey";
        String value = "someValue";

        Tag<String> theTag = Tag.create(key, value);

        testValue(theTag, key, value);

        testEqualAndHashCode(theTag,
                             Tag.create(key, value),
                             Tag.create(key, "otherValue"),
                             Tag.create("otherKey", value));

        testToString(theTag);

        testApply(theTag);
    }

    @Test
    void testBooleanTag() {
        String key = "theKey";
        boolean value = true;

        Tag<Boolean> theTag = Tag.create(key, value);

        testValue(theTag, key, value);

        testEqualAndHashCode(theTag,
                             Tag.create(key, value),
                             Tag.create(key, false),
                             Tag.create("otherKey", value));

        testToString(theTag);

        testApply(theTag);
    }

    @Test
    void testIntTag() {
        String key = "theKey";
        Integer value = 42;

        Tag<Number> theTag = Tag.create(key, value);

        testValue(theTag, key, value);

        testEqualAndHashCode(theTag,
                             Tag.create(key, value),
                             Tag.create(key, 47),
                             Tag.create("otherKey", value));

        testToString(theTag);

        testApply(theTag);
    }

    @Test
    void testBigIntTag() {
        String key = "theKey";
        BigInteger value = new BigInteger("74447851266545852235469");

        Tag<Number> theTag = Tag.create(key, value);

        testValue(theTag, key, value);

        testEqualAndHashCode(theTag,
                             Tag.create(key, value),
                             Tag.create(key, new BigInteger("147")),
                             Tag.create("otherKey", value));

        testToString(theTag);

        testApply(theTag);
    }

    private void testApply(Tag<?> theTag) {
        MockSpan span = tracer.buildSpan("operation").start();

        theTag.apply(span);

        Map<String, Object> tags = span.tags();

        assertThat(tags, is(Map.of(theTag.key(), theTag.value())));
    }

    private <T> void testValue(Tag<T> theTag, String key, T value) {
        assertThat(theTag.key(), is(key));
        assertThat(theTag.value(), is(value));
    }

    void testToString(Tag<?> theTag) {
        assertThat(theTag.toString(), containsString(theTag.key()));
        assertThat(theTag.toString(), containsString(String.valueOf(theTag.value())));
    }

    void testEqualAndHashCode(Tag<?> theTag, Tag<?> equalTag, Tag<?> sameKey, Tag<?> sameValue) {
        assertThat(theTag, is(equalTag));
        assertThat(theTag, not(sameKey));
        assertThat(theTag, not(sameValue));

        assertThat(theTag.hashCode(), is(equalTag.hashCode()));
        assertThat(theTag.hashCode(), not(sameKey.hashCode()));
        assertThat(theTag.hashCode(), not(sameValue.hashCode()));
    }
}