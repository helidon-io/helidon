/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics.tck;

import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Temporary workaround for MP Metrics 2.3 TCK, which uses non-standard array parameters in endpoint definitions.
 * <p>
 *     Note that Jersey invokes the {@code ArrayParamConverter} for only the first value of a given query parameter, even
 *     if the query parameter repeats (as in {@code /path&qp1=hi&qp1=there}). Luckily, the MP metrics optional TCK tests that use
 *     array or var arg parameters do not check the values. So, even though Jersey does not pass us the multiple values, by
 *     providing this array parameter converter we satisfy the TCK.
 * </p>
 * <p>
 *     The MP metrics folks have already worked on PRs to fix this in the master and in 2.3.x so, in time, this won't be needed.
 * </p>
 */
@Provider
class ArrayParamConverterProvider implements ParamConverterProvider {

    private static final Map<Class<?>, Supplier<ArrayParamConverter>> CONVERTER_FACTORIES = initConverterFactories();

    private static Map<Class<?>, Supplier<ArrayParamConverter>> initConverterFactories() {
        // For primitives, we need to create the arrays explicitly.
        Map<Class<?>, Supplier<ArrayParamConverter>> result = new HashMap<>();
        result.put(int.class, () -> new ArrayParamConverter(int.class, Integer::parseInt, () -> new int[1]));
        result.put(byte.class, () -> new ArrayParamConverter(byte.class, Byte::parseByte, () -> new byte[1]));
        result.put(long.class, () -> new ArrayParamConverter(long.class, Long::parseLong, () -> new long[1]));
        result.put(double.class, () -> new ArrayParamConverter(double.class, Double::parseDouble, () -> new double[1]));
        result.put(boolean.class, () -> new ArrayParamConverter(boolean.class, Boolean::parseBoolean, () -> new boolean[1]));

        // For class-based types, we can use {@code Array.newInstance}.
        result.put(Integer.class, () -> new ArrayParamConverter(Integer.class, Integer::valueOf));
        result.put(Byte.class, () -> new ArrayParamConverter(Byte.class, Byte::valueOf));
        result.put(Long.class, () -> new ArrayParamConverter(Long.class, Long::valueOf));
        result.put(Double.class, () -> new ArrayParamConverter(Double.class, Double::valueOf));
        result.put(Boolean.class, () -> new ArrayParamConverter(Boolean.class, Boolean::valueOf));

        result.put(String.class, () -> new ArrayParamConverter(String.class, Function.identity()));

        return result;
    }

    @Override
    public <A> ParamConverter<A> getConverter(Class<A> rawType, Type genericType, Annotation[] annotations) {
        // If this is an array parameter and we recognize the component type, then return one of our converters.
        return rawType.isArray() && CONVERTER_FACTORIES.containsKey(rawType.getComponentType())
            ? ((ParamConverter<A>) CONVERTER_FACTORIES.get(rawType.getComponentType()).get())
            : null;
    }

    private static class ArrayParamConverter implements ParamConverter {

        private final Class<?> type;
        private final Function<String, ?> conversion;
        private final Supplier<Object> arraySupplier;

        /**
         * Creates a new instance for primitives.
         *
         * @param type the primitive type of the parameter
         * @param conversion function that converts a string to the type
         * @param arraySupplier function that creates a new instance of an array of the specified type
         */
        private ArrayParamConverter(Class<?> type, Function<String, ?> conversion, Supplier<Object> arraySupplier) {
            this.type = type;
            this.conversion = conversion;
            this.arraySupplier = arraySupplier;
        }

        /**
         * Creates a new instance for a class-based type (i.e., non-primitive).
         *
         * @param type the type of the parameter
         * @param conversion function that converts a string to the type
         */
        private ArrayParamConverter(Class<?> type, Function<String, ?> conversion) {
            this(type, conversion, () -> Array.newInstance(type, 1));
        }

        @Override
        public Object fromString(String value) {
            // Jersey passes us only one value, so create a new array and populate it with the converted string value.
            Object result = arraySupplier.get(); // Array.newInstance(type, 1);
            Array.set(result, 0, conversion.apply(value));
            return result;
        }

        @Override
        public String toString(Object value) {
            return null;
        }
    }
}
