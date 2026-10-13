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

package io.helidon.openapi;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class OpenApiFeatureConfigTest {
    @Test
    void enabledByDefault() {
        var config = OpenApiFeatureConfig.create();
        assertThat(config.enabled(), is(true));
        assertThat(config.isEnabled(), is(true));
        assertThat(OpenApiFeatureConfig.create(Config.empty()).enabled(), is(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void setterAliasesShareState(boolean enabled) {
        var builder = OpenApiFeatureConfig.builder();
        assertThat(builder.enabled(enabled), sameInstance(builder));
        assertThat(builder.isEnabled(), is(enabled));
        assertThat(builder.buildPrototype().enabled(), is(enabled));
        assertThat(builder.buildPrototype().isEnabled(), is(enabled));

        builder.isEnabled(!enabled);
        assertThat(builder.buildPrototype().enabled(), is(!enabled));
        assertThat(builder.buildPrototype().isEnabled(), is(!enabled));

        builder.enabled(enabled);
        assertThat(builder.buildPrototype().enabled(), is(enabled));
        assertThat(builder.buildPrototype().isEnabled(), is(enabled));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void configurationAndCopiesPreserveEnablement(boolean enabled) {
        var config = Config.just(ConfigSources.create(Map.of("enabled", Boolean.toString(enabled))));
        var builder = OpenApiFeatureConfig.builder().config(config);
        var prototype = builder.buildPrototype();

        assertThat(prototype.enabled(), is(enabled));
        assertThat(prototype.isEnabled(), is(enabled));
        assertThat(OpenApiFeatureConfig.builder(prototype).buildPrototype().enabled(), is(enabled));
        assertThat(OpenApiFeatureConfig.builder().from(builder).buildPrototype().isEnabled(), is(enabled));

        builder.enabled(!enabled);
        assertThat(builder.buildPrototype().enabled(), is(!enabled));
        builder.config(config);
        assertThat(builder.buildPrototype().enabled(), is(enabled));
    }

    @Test
    void legacyMethodsAreDeprecated() throws NoSuchMethodException {
        var methods = List.of(OpenApiFeatureConfig.class.getMethod("isEnabled"),
                              OpenApiFeatureConfig.BuilderBase.class.getMethod("isEnabled"),
                              OpenApiFeatureConfig.BuilderBase.class.getMethod("isEnabled", boolean.class));
        for (var method : methods) {
            var deprecated = method.getAnnotation(Deprecated.class);
            assertThat(method.toString(), deprecated, notNullValue());
            assertThat(method.toString(), deprecated.since(), is("28.0.0"));
            assertThat(method.toString(), deprecated.forRemoval(), is(true));
        }
        assertThat(OpenApiFeatureConfig.class.getMethod("enabled").getAnnotation(Deprecated.class), nullValue());
        assertThat(OpenApiFeatureConfig.BuilderBase.class.getMethod("enabled", boolean.class).getAnnotation(Deprecated.class),
                   nullValue());
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void builderCopyPreservesEnablementAndDiscovery(boolean enabled, boolean discover) {
        var source = OpenApiFeatureConfig.builder().enabled(false);
        if (enabled) {
            source.enabled(true);
        }
        source.openApiVersionDiscoverServices(discover)
                .servicesDiscoverServices(!discover)
                .managerDiscoverServices(discover);

        var copy = OpenApiFeatureConfig.builder().enabled(!enabled).from(source);
        assertThat(copy.isEnabled(), is(enabled));
        assertThat(copy.openApiVersionDiscoverServices(), is(discover));
        assertThat(copy.servicesDiscoverServices(), is(!discover));
        assertThat(copy.managerDiscoverServices(), is(discover));
        assertThat(copy.buildPrototype().enabled(), is(enabled));
    }

    @Test
    void legacyJvmSignaturesRemainCallable() throws Throwable {
        var lookup = MethodHandles.publicLookup();
        var setter = lookup.findVirtual(OpenApiFeatureConfig.BuilderBase.class,
                                        "isEnabled",
                                        MethodType.methodType(OpenApiFeatureConfig.BuilderBase.class, boolean.class));
        var builderGetter = lookup.findVirtual(OpenApiFeatureConfig.BuilderBase.class,
                                               "isEnabled",
                                               MethodType.methodType(boolean.class));
        var getter = lookup.findVirtual(OpenApiFeatureConfig.class, "isEnabled", MethodType.methodType(boolean.class));
        OpenApiFeatureConfig.BuilderBase<?, ?> builder = OpenApiFeatureConfig.builder();

        assertThat((OpenApiFeatureConfig.BuilderBase<?, ?>) setter.invokeExact(builder, false), sameInstance(builder));
        assertThat((boolean) builderGetter.invokeExact(builder), is(false));
        OpenApiFeatureConfig config = builder.buildPrototype();
        assertThat((boolean) getter.invokeExact(config), is(false));
        assertThat(config.enabled(), is(false));
        // Adding a default preserves implementations compiled before enabled() existed.
        assertThat(OpenApiFeatureConfig.class.getMethod("enabled").isDefault(), is(true));
    }

    @Test
    void conventionalSetterPreservesLegacyBuilderOverrides() {
        var builder = new LegacyBuilder();
        assertThat(builder.enabled(false), sameInstance(builder));
        assertThat(builder.setterCalls, is(1));
        assertThat(builder.isEnabled(), is(false));
        assertThat(builder.buildPrototype().enabled(), is(false));
    }

    private static final class LegacyBuilder extends OpenApiFeatureConfig.BuilderBase<LegacyBuilder, OpenApiFeatureConfig> {
        private int setterCalls;

        @Override
        public LegacyBuilder isEnabled(boolean enabled) {
            setterCalls++;
            return super.isEnabled(enabled);
        }

        @Override
        public OpenApiFeatureConfig buildPrototype() {
            return OpenApiFeatureConfig.builder().from(this).buildPrototype();
        }
    }
}
