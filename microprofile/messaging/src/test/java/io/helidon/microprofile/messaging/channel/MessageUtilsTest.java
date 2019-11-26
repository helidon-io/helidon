/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package io.helidon.microprofile.messaging.channel;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MessageUtilsTest {

    @SuppressWarnings("unchecked")
    static Stream<Tuple> testSource() {
        return Stream.of(

                Tuple.of(5L, Long.class),
                Tuple.of(5L, Message.class),
                Tuple.of(5, Integer.class),
                Tuple.of(5, Message.class),
                Tuple.of(Double.parseDouble("50"), Double.class),
                Tuple.of(Double.parseDouble("50"), Message.class),
                Tuple.of(BigInteger.TEN, BigInteger.class),
                Tuple.of(BigInteger.TEN, Message.class),
                Tuple.of("test", String.class),
                Tuple.of("test", Message.class),
                Tuple.of(Message.of("test"), String.class),
                Tuple.of(Message.of("test"), Message.class),
                Tuple.of(Message.of(5L), Long.class),
                Tuple.of(Message.of(5), Integer.class),
                Tuple.of(Message.of(BigInteger.TEN), BigInteger.class),
                Tuple.of(Message.of(BigDecimal.TEN), BigDecimal.class)

        );
    }

    @ParameterizedTest
    @MethodSource("testSource")
    void wrapperTest(Tuple tuple) throws ExecutionException, InterruptedException {
        assertExpectedType(tuple.value, tuple.type);
    }

    private static void assertExpectedType(Object value, Class<?> type) throws ExecutionException, InterruptedException {
        Object unwrapped = MessageUtils.unwrap(value, type);
        assertTrue(type.isAssignableFrom(unwrapped.getClass()),
                String.format("Expected value of type %s got %s instead", type.getSimpleName(), value.getClass().getSimpleName()));
    }

    private static class Tuple {
        private Object value;
        private Class<?> type;

        private Tuple(Object value, Class<?> clazz) {
            this.value = value;
            this.type = clazz;
        }

        static Tuple of(Object o, Class<?> clazz) {
            return new Tuple(o, clazz);
        }

        public Object getValue() {
            return value;
        }

        public Class<?> getType() {
            return type;
        }

        @Override
        public String toString() {
            return value.getClass().getSimpleName() + " -> " + type.getSimpleName();
        }
    }
}