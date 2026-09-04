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
package io.helidon.metrics.api;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;
import io.helidon.metrics.spi.MetricsProgrammaticConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("removal")
class ScopeApiDeprecationTest {

    @Test
    void legacyFormatterProviderHandlesOnlyEmptyTagSelection() {
        AtomicInteger invocationCount = new AtomicInteger();
        MeterRegistryFormatterProvider provider = (_, _, _, _, _, _) -> {
            invocationCount.incrementAndGet();
            return Optional.empty();
        };
        MeterRegistry meterRegistry = new NoOpMetricsFactory().globalRegistry();
        MetricsConfig metricsConfig = MetricsConfig.create();

        assertThat(provider.formatter(MediaTypes.TEXT_PLAIN,
                                      metricsConfig,
                                      meterRegistry,
                                      Map.of(),
                                      List.of()),
                   is(Optional.empty()));
        assertThat(invocationCount.get(), is(1));

        assertThat(provider.formatter(MediaTypes.TEXT_PLAIN,
                                      metricsConfig,
                                      meterRegistry,
                                      Map.of("tag", List.of("value")),
                                      List.of()),
                   is(Optional.empty()));
        assertThat(invocationCount.get(), is(1));
    }

    @Test
    void scopeFreeEnablementCallsLegacyScopedImplementation() throws ReflectiveOperationException {
        AtomicBoolean legacyMethodInvoked = new AtomicBoolean();
        AtomicReference<Optional<?>> receivedScope = new AtomicReference<>();
        Method legacyMethod = MeterRegistry.class.getMethod("isMeterEnabled", String.class, Map.class, Optional.class);
        MeterRegistry meterRegistry = (MeterRegistry) Proxy.newProxyInstance(
                MeterRegistry.class.getClassLoader(),
                new Class<?>[] {MeterRegistry.class},
                (proxy, method, arguments) -> {
                    if (method.equals(legacyMethod)) {
                        legacyMethodInvoked.set(true);
                        receivedScope.set((Optional<?>) arguments[2]);
                        return false;
                    }
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, arguments);
                    }
                    throw new AssertionError("Unexpected method invocation: " + method);
                });

        assertThat(meterRegistry.isMeterEnabled("test", Map.of()), is(false));
        assertThat(legacyMethodInvoked.get(), is(true));
        assertThat(receivedScope.get(), is(Optional.empty()));
    }

    @Test
    void scopedRemovalValidatesDelegatedArgumentsAtBoundary() {
        MeterRegistry meterRegistry = (MeterRegistry) Proxy.newProxyInstance(
                MeterRegistry.class.getClassLoader(),
                new Class<?>[] {MeterRegistry.class},
                (proxy, method, arguments) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, arguments);
                    }
                    if (method.getName().equals("remove")) {
                        return Optional.empty();
                    }
                    throw new AssertionError("Unexpected method invocation: " + method);
                });

        assertThrows(NullPointerException.class, () -> meterRegistry.remove((Meter.Id) null, "scope"));
        assertThrows(NullPointerException.class, () -> meterRegistry.remove((String) null, List.of(), "scope"));
        assertThrows(NullPointerException.class, () -> meterRegistry.remove("meter", null, "scope"));
    }

    @Test
    void scopeApisAreMarkedForRemoval() throws ReflectiveOperationException {
        assertDeprecated(Meter.class.getMethod("scope"));
        assertDeprecated(Meter.Builder.class.getMethod("scope", String.class));
        assertDeprecated(Meter.Builder.class.getMethod("scope"));
        assertDeprecated(Meter.Scope.class);
        assertDeprecated(Meter.Scope.class.getField("APPLICATION"));
        assertDeprecated(Meter.Scope.class.getField("BASE"));
        assertDeprecated(Meter.Scope.class.getField("VENDOR"));
        assertDeprecated(Meter.Scope.class.getField("BUILT_IN_SCOPES"));
        assertDeprecated(Meter.Scope.class.getField("DEFAULT"));

        assertDeprecated(MeterRegistry.class.getMethod("meters", Iterable.class));
        assertDeprecated(MeterRegistry.class.getMethod("scopes"));
        assertDeprecated(MeterRegistry.class.getMethod("isMeterEnabled", String.class, Map.class, Optional.class));
        assertDeprecated(MeterRegistry.class.getMethod("remove", Meter.Id.class, String.class));
        assertDeprecated(MeterRegistry.class.getMethod("remove", String.class, Iterable.class, String.class));

        assertDeprecated(Metrics.Counted.class.getMethod("scope"));
        assertDeprecated(Metrics.Timed.class.getMethod("scope"));
        assertDeprecated(Metrics.Gauge.class.getMethod("scope"));

        assertDeprecated(MetricsConfig.class.getMethod("scoping"));
        assertDeprecated(MetricsConfig.class.getMethod("isScopeEnabled", String.class));
        assertDeprecated(MetricsConfig.class.getMethod("isMeterEnabled", String.class, String.class));
        assertDeprecated(ScopeConfig.class);
        assertDeprecated(ScopeConfig.class.getMethod("name"));
        assertDeprecated(ScopeConfig.class.getMethod("enabled"));
        assertDeprecated(ScopeConfig.class.getMethod("include"));
        assertDeprecated(ScopeConfig.class.getMethod("exclude"));
        assertDeprecated(ScopeConfig.class.getMethod("isMeterEnabled", String.class));
        assertDeprecated(ScopingConfig.class);
        assertDeprecated(ScopingConfig.class.getMethod("defaultValue"));
        assertDeprecated(ScopingConfig.class.getMethod("tagName"));
        assertDeprecated(ScopingConfig.class.getMethod("scopes"));

        assertDeprecated(MetricsProgrammaticConfig.class.getMethod("scopeDefaultValue"));
        assertDeprecated(MetricsProgrammaticConfig.class.getMethod("scopeTagName"));
        assertDeprecated(SystemTagsManager.class.getMethod("scopeTag", Optional.class));
        assertDeprecated(SystemTagsManager.class.getMethod("withScopeTag", Iterable.class, String.class));
        assertDeprecated(SystemTagsManager.class.getMethod("withScopeTag", Iterable.class, Optional.class));
        assertDeprecated(SystemTagsManager.class.getMethod("withoutSystemOrScopeTags", Iterable.class));
        assertDeprecated(SystemTagsManager.class.getMethod("assignScope", String.class, Function.class));
        assertDeprecated(SystemTagsManager.class.getMethod("effectiveScope", Optional.class));
        assertDeprecated(SystemTagsManager.class.getMethod("effectiveScope", Optional.class, Iterable.class));
        assertDeprecated(SystemTagsManager.class.getMethod("scopeTagName"));

        assertDeprecated(MeterRegistryFormatterProvider.class.getMethod("formatter",
                                                                         MediaType.class,
                                                                         MetricsConfig.class,
                                                                         MeterRegistry.class,
                                                                         Optional.class,
                                                                         Iterable.class,
                                                                         Iterable.class));
    }

    private static void assertDeprecated(AnnotatedElement element) {
        Deprecated deprecated = element.getAnnotation(Deprecated.class);
        assertThat("Deprecated annotation on " + element, deprecated, notNullValue());
        assertThat("forRemoval on " + element, deprecated.forRemoval(), is(true));
        assertThat("since on " + element, deprecated.since(), is("27.0.0"));
    }
}
