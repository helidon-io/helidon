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

package io.helidon.builder.test.testsubjects.hash;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.builder.api.GeneratedBuilder;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratedHashTest {
    @Test
    void primitiveAndCollisionNamesMatchObjectsOracle() {
        for (int iteration = 0; iteration < 64; iteration++) {
            float floatValue = switch (iteration % 8) {
                case 0 -> 0.0f;
                case 1 -> -0.0f;
                case 2 -> Float.NaN;
                case 3 -> Float.intBitsToFloat(0x7fc00001);
                case 4 -> Float.NEGATIVE_INFINITY;
                case 5 -> Float.POSITIVE_INFINITY;
                case 6 -> Float.MIN_VALUE;
                default -> Float.MAX_VALUE;
            };
            double doubleValue = switch (iteration % 8) {
                case 0 -> -0.0d;
                case 1 -> 0.0d;
                case 2 -> Double.NaN;
                case 3 -> Double.longBitsToDouble(0x7ff8000000000001L);
                case 4 -> Double.NEGATIVE_INFINITY;
                case 5 -> Double.POSITIVE_INFINITY;
                case 6 -> Double.MIN_VALUE;
                default -> Double.MAX_VALUE;
            };
            var value = Primitives.builder()
                    .booleanValue(iteration % 2 == 0)
                    .byteValue(iteration % 2 == 0 ? Byte.MIN_VALUE : Byte.MAX_VALUE)
                    .shortValue(iteration % 2 == 0 ? Short.MIN_VALUE : Short.MAX_VALUE)
                    .charValue(iteration % 2 == 0 ? Character.MIN_VALUE : Character.MAX_VALUE)
                    .intValue(iteration % 2 == 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE)
                    .longValue(iteration % 2 == 0 ? Long.MIN_VALUE : Long.MAX_VALUE)
                    .floatValue(floatValue)
                    .doubleValue(doubleValue)
                    .result(iteration)
                    .hash(iteration + 1)
                    .superHash(iteration + 2)
                    .build();
            int expected = Objects.hash(value.booleanValue(), value.byteValue(), value.shortValue(), value.charValue(),
                                        value.intValue(), value.longValue(), value.floatValue(), value.doubleValue(),
                                        value.result(), value.hash(), value.superHash());
            assertThat("iteration " + iteration, value.hashCode(), is(expected));
        }
    }

    @Test
    void absentReferencesAndWrappersRemainNullSafe() {
        var value = Primitives.builder().build();
        int expected = Objects.hash(value.booleanValue(), value.byteValue(), value.shortValue(), value.charValue(),
                                    value.intValue(), value.longValue(), value.floatValue(), value.doubleValue(),
                                    value.result(), value.hash(), value.superHash());
        assertThat(value.hashCode(), is(expected));
        var refs = Refs.builder().ignored("excluded").build();
        assertThat(refs.hashCode(), is(Objects.hash(refs.first(), refs.second(), refs.third())));
    }

    @Test
    void orderedCallbacksAndNestedMutationRemainObservable() {
        List<String> calls = new ArrayList<>();
        Probe first = new Probe("first", calls, 7);
        Probe second = new Probe("second", calls, 11);
        Probe third = new Probe("third", calls, 13);
        first.action = () -> second.value = 31;
        var value = Refs.builder().first(first).second(second).third(third).ignored("excluded").build();
        int expected = Objects.hash(first, second, third);
        assertThat(calls, is(List.of("first", "second", "third")));
        calls.clear();
        second.value = 11;
        assertThat(value.hashCode(), is(expected));
        assertThat(calls, is(List.of("first", "second", "third")));
        calls.clear();
        first.action = () -> { };
        second.value = 47;
        int changed = Objects.hash(first, second, third);
        calls.clear();
        assertThat(value.hashCode(), is(changed));
        assertThat(calls, is(List.of("first", "second", "third")));
    }

    @Test
    void originalThrowableAndFirstCallbackStopArePreserved() {
        List<String> calls = new ArrayList<>();
        Probe first = new Probe("first", calls, 7);
        Probe second = new Probe("second", calls, 11);
        Probe third = new Probe("third", calls, 13);
        RuntimeException failure = new RuntimeException("marker");
        second.action = () -> { throw failure; };
        var value = Refs.builder().first(first).second(second).third(third).ignored("excluded").build();
        assertThat(assertThrows(RuntimeException.class, value::hashCode), sameInstance(failure));
        assertThat(calls, is(List.of("first", "second")));
    }

    @Test
    void superclassBeforeOwnAndEmptyChildArePreserved() {
        List<String> calls = new ArrayList<>();
        Probe base = new Probe("base", calls, 17);
        Probe child = new Probe("child", calls, 19);
        var value = Child.builder().base(base).child(child).build();
        int expected = 31 * Objects.hash(base) + Objects.hash(child);
        calls.clear();
        assertThat(value.hashCode(), is(expected));
        assertThat(calls, is(List.of("base", "child")));
        var emptyChild = EmptyChild.builder().base(base).build();
        int inherited = Objects.hash(base);
        calls.clear();
        assertThat(emptyChild.hashCode(), is(inherited));
        assertThat(calls, is(List.of("base")));
        RuntimeException failure = new RuntimeException("super marker");
        base.action = () -> { throw failure; };
        calls.clear();
        assertThat(assertThrows(RuntimeException.class, value::hashCode), sameInstance(failure));
        assertThat(calls, is(List.of("base")));
    }

    @Test
    void optionalCharTailsStaySeparateAndTrailing() {
        List<String> calls = new ArrayList<>();
        Probe first = new Probe("first", calls, 7);
        Probe second = new Probe("second", calls, 11);
        var value = OptionalChars.builder().first(first).middle(new char[] {'a', 'b'})
                .second(second).tail(new char[] {'c'}).build();
        int expected = Objects.hash(first, second)
                + 31 * GeneratedBuilder.EqualityUtil.optionalCharArrayHash(value.middle())
                + 31 * GeneratedBuilder.EqualityUtil.optionalCharArrayHash(value.tail());
        calls.clear();
        assertThat(value.hashCode(), is(expected));
        assertThat(calls, is(List.of("first", "second")));
        var empty = OptionalOnly.builder().build();
        assertThat(empty.hashCode(),
                   is(Objects.hash() + 31 * GeneratedBuilder.EqualityUtil.optionalCharArrayHash(Optional.empty())));
        var present = OptionalOnly.builder().chars(new char[] {'a'}).build();
        assertThat(present.hashCode(),
                   is(Objects.hash() + 31 * GeneratedBuilder.EqualityUtil.optionalCharArrayHash(present.chars())));
        var collision = OptionalResult.builder().result(new char[] {'r'}).build();
        assertThat(collision.hashCode(), is(Objects.hash()
                + 31 * GeneratedBuilder.EqualityUtil.optionalCharArrayHash(collision.result())));
        var inheritedCollision = OptionalSuper.builder().base(first).superHash(new char[] {'s'}).build();
        int inheritedExpected = 31 * Objects.hash(first) + Objects.hash()
                + 31 * GeneratedBuilder.EqualityUtil.optionalCharArrayHash(inheritedCollision.superHash());
        calls.clear();
        assertThat(inheritedCollision.hashCode(), is(inheritedExpected));
        assertThat(calls, is(List.of("first")));
    }

    @Test
    void arrayContainingFallbackMatchesOriginalVarargsBinding() {
        var single = SingleReferenceArray.builder().values(new String[] {"a", null, "b"}).build();
        assertThat(single.hashCode(), is(Objects.hash((Object[]) single.values())));
        var absent = SingleReferenceArray.builder().build();
        assertThat(absent.hashCode(), is(Objects.hash((Object[]) absent.values())));
        var primitive = SinglePrimitiveArray.builder().values(new int[] {1, 2}).build();
        assertThat(primitive.hashCode(), is(Objects.hash(primitive.values())));
        var arrays = Arrays.builder().refs(new Object[] {"a"}).bytes(new byte[] {1}).chars(new char[] {'x'})
                .last(new Object[] {"not flattened"}).build();
        assertThat(arrays.hashCode(), is(Objects.hash(arrays.refs(), arrays.bytes(), arrays.chars(), arrays.last())));
    }

    @Test
    void exclusionsEmptyAndGetterNoninvocationArePreserved() {
        assertThat(Empty.builder().build().hashCode(), is(1));
        List<String> calls = new ArrayList<>();
        Probe ignored = new Probe("ignored", calls, 23);
        assertThat(Excluded.builder().ignored(ignored).build().hashCode(), is(1));
        assertThat(calls, is(List.of()));
        var builder = Refs.builder().first("a").second("b").third("c").ignored(ignored);
        var value = new GetterOverride(builder);
        assertThat(value.hashCode(), is(Objects.hash("a", "b", "c")));
        assertThat(calls, is(List.of()));
    }

    @Test
    void supplierGetIsNeverInvokedByHash() {
        List<String> calls = new ArrayList<>();
        HashSupplier first = new HashSupplier("first", calls, 7);
        HashSupplier second = new HashSupplier("second", calls, 11);
        var value = Suppliers.builder().first(first).second(second).build();
        int expected = Objects.hash(value.first(), value.second());
        calls.clear();
        assertThat(value.hashCode(), is(expected));
        assertThat(calls, is(List.of()));
    }

    private static final class Probe {
        private final String name;
        private final List<String> calls;
        private int value;
        private Runnable action = () -> { };

        private Probe(String name, List<String> calls, int value) {
            this.name = name;
            this.calls = calls;
            this.value = value;
        }

        @Override
        public int hashCode() {
            calls.add(name);
            action.run();
            return value;
        }
    }

    private static final class GetterOverride extends Refs.BuilderBase.RefsImpl {
        private GetterOverride(Refs.Builder builder) {
            super(builder);
        }

        @Override
        public Object first() {
            throw new AssertionError("Hash called getter");
        }
    }

    private static final class HashSupplier implements Supplier<Integer> {
        private final String name;
        private final List<String> calls;
        private final int value;

        private HashSupplier(String name, List<String> calls, int value) {
            this.name = name;
            this.calls = calls;
            this.value = value;
        }

        @Override
        public Integer get() {
            throw new AssertionError("Hash called supplier get");
        }

        @Override
        public int hashCode() {
            calls.add(name);
            return value;
        }
    }
}
