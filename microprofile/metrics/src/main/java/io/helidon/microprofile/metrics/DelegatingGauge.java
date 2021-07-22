/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.metrics;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.eclipse.microprofile.metrics.Gauge;

/**
 * Implementation of {@link Gauge} for cataloging in the metrics registry that
 * delegates its {@link #getValue()} method invocations to a discovered bean
 * method annotated with {@code @Gauge}.
 *
 * @param <T> data type reported by the underlying {@code Gauge}
 */
class DelegatingGauge<T /* extends Number */> implements Gauge<T> {
    // TODO uncomment preceding clause once MP metrics enforces restriction

    private final Method method;
    private final Object obj;
    private final Class<T> clazz;

    private DelegatingGauge(Method method, Object obj, Class<T> clazz) {
        this.method = method;
        this.obj = obj;
        this.clazz = clazz;
    }

    /**
     * Creates a new {@code DelegatingGauge} which will defer to the specified
     * method on the object to retrieve the gauge of interest of the indicated type.
     *
     * @param <S>    type of the underlying gauge
     * @param method value-reporting method to be invoked to retrieve the gauge value
     * @param obj    bean instance from which to retrieve the gauge value
     * @param clazz  type of the underlying gauge
     * @return {@code DelegatingGauge}
     */
    public static <S /* extends Number */> DelegatingGauge<S> newInstance(Method method, Object obj,
            Class<S> clazz) {
        // TODO uncomment preceding clause once MP metrics enforces restriction
        return new DelegatingGauge<>(method, obj, clazz);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getValue() {
        try {
            return (T) method.invoke(obj);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }
}
