/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import io.helidon.metrics.KeyPerformanceIndicatorMetricsSettings.Builder;

@Deprecated
class KeyPerformanceIndicatorMetricsSettingsCompatibility {

    static Builder builder() {
        return (Builder) Proxy.newProxyInstance(KeyPerformanceIndicatorMetricsSettingsCompatibility.class.getClassLoader(),
                                                new Class<?>[] {Builder.class},
                                                new BuilderCompatibilityInvocationHandler());
    }

    private KeyPerformanceIndicatorMetricsSettingsCompatibility() {
    }

    private static class CompatibilityInvocationHandler implements InvocationHandler {

        static KeyPerformanceIndicatorMetricsSettings create(
                io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings.Builder builder) {
            return (KeyPerformanceIndicatorMetricsSettings) Proxy.newProxyInstance(
                    KeyPerformanceIndicatorMetricsSettings.class.getClassLoader(),
                    new Class<?>[] {KeyPerformanceIndicatorMetricsSettings.class},
                    new CompatibilityInvocationHandler(builder));
        }

        private final io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings delegate;

        private CompatibilityInvocationHandler(io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings.Builder builder) {
            delegate = builder.build();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return method.invoke(delegate, args);
        }
    }

    private static class BuilderCompatibilityInvocationHandler implements InvocationHandler {

        private final io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings.Builder builderDelegate =
                io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings.builder();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("build")) {
                return CompatibilityInvocationHandler.create(builderDelegate);
            }
            Object result = method.getReturnType().isAssignableFrom(Builder.class)
                    ? proxy
                    : method.invoke(builderDelegate, args);
            return result;
        }
    }
}
