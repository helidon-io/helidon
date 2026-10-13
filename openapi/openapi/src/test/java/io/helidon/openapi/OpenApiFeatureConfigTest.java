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

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
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

        assertThat(builder.buildPrototype().enabled(), is(enabled));

        builder.isEnabled(!enabled);
        assertThat(builder.buildPrototype().enabled(), is(!enabled));
        assertThat(builder.buildPrototype().isEnabled(), is(!enabled));

        builder.enabled(enabled);
        assertThat(builder.buildPrototype().enabled(), is(enabled));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void configurationAndCopiesPreserveEnablement(boolean enabled) {
        var config = Config.just(ConfigSources.create(Map.of("enabled", Boolean.toString(enabled))));
        var builder = OpenApiFeatureConfig.builder().config(config);
        var prototype = builder.buildPrototype();

        assertThat(prototype.enabled(), is(enabled));
        assertThat(OpenApiFeatureConfig.builder(prototype).buildPrototype().enabled(), is(enabled));

        builder.enabled(!enabled);
        assertThat(builder.buildPrototype().enabled(), is(!enabled));
        builder.config(config);
        assertThat(builder.buildPrototype().enabled(), is(enabled));
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
        assertThat(copy.openApiVersionDiscoverServices(), is(discover));
        assertThat(copy.servicesDiscoverServices(), is(!discover));
        assertThat(copy.managerDiscoverServices(), is(discover));
        assertThat(copy.buildPrototype().enabled(), is(enabled));
        assertThat(copy.openApiVersionDiscoverServices(), is(discover));
        assertThat(copy.servicesDiscoverServices(), is(!discover));
        assertThat(copy.managerDiscoverServices(), is(discover));
    }

    @Test
    void builderCopyPreservesLegacyGetterOverride() {
        var config = new LegacyBuilder().buildPrototype();

        assertThat(config.isEnabled(), is(false));
        assertThat(config.enabled(), is(false));
        assertThat(config.openApiVersion().isEmpty(), is(true));
        assertThat(config.services().isEmpty(), is(true));
        assertThat(config.manager().isEmpty(), is(true));
    }

    private static final class LegacyBuilder extends OpenApiFeatureConfig.BuilderBase<LegacyBuilder, OpenApiFeatureConfig> {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public OpenApiFeatureConfig buildPrototype() {
            return OpenApiFeatureConfig.builder().from(this).buildPrototype();
        }
    }
}
