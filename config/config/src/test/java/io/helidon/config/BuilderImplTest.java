/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.test.infra.RestoreSystemPropertiesExt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.config.ConfigSourceTest.TEST_ENV_VAR_NAME;
import static io.helidon.config.ConfigSourceTest.TEST_ENV_VAR_VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests part of {@link BuilderImpl}.
 *
 * @see BuilderImplMappersTest
 * @see BuilderImplParsersTest
 */
@ExtendWith(RestoreSystemPropertiesExt.class)
public class BuilderImplTest {

    private static final String TEST_SYS_PROP_NAME = "this_is_my_property-BuilderImplTest";
    private static final String TEST_SYS_PROP_VALUE = "This Is My SYS PROPS Value.";

    @Test
    public void testBuildDefault() {
        Config.Builder builder = Config.builder(ConfigSources.empty());
        BuilderImpl spyBuilder = spy((BuilderImpl) builder);
        spyBuilder
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .disableValueResolving()
                .build();

        verify(spyBuilder).createProvider(notNull(), //ConfigMapperManager
                                          eq(ConfigSourcesRuntime.empty()), //ConfigSource
                                          eq(OverrideSourceRuntime.empty()), //OverrideSource
                                          eq(List.of()), //filterProviders
                                          eq(true), //cachingEnabled
                                          notNull(), //changesExecutor
                                          eq(true), //keyResolving
                                          isNull() //aliasGenerator
        );
    }

    @Test
    public void testBuildCustomChanges() {
        Executor myExecutor = Runnable::run;
        Config.Builder builder = Config.builder()
                .sources(ConfigSources.empty())
                .disableValueResolving()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .changesExecutor(myExecutor);
        BuilderImpl spyBuilder = spy((BuilderImpl) builder);
        spyBuilder.build();

        verify(spyBuilder).createProvider(notNull(), //ConfigMapperManager
                                          eq(ConfigSourcesRuntime.empty()), //ConfigSource
                                          eq(OverrideSourceRuntime.empty()), //OverrideSource
                                          eq(List.of()), //filterProviders
                                          eq(true), //cachingEnabled
                                          eq(myExecutor), //changesExecutor
                                          eq(true), //keyResolving
                                          isNull() //aliasGenerator
        );
    }

    @Test
    public void testBuildDisableKeyResolving() {
        Executor myExecutor = Runnable::run;
        Config.Builder builder = Config.builder()
                .sources(ConfigSources.empty())
                .disableKeyResolving()
                .disableValueResolving()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .changesExecutor(myExecutor);
        BuilderImpl spyBuilder = spy((BuilderImpl) builder);
        spyBuilder.build();

        verify(spyBuilder).createProvider(notNull(), //ConfigMapperManager
                                          eq(ConfigSourcesRuntime.empty()), //ConfigSource
                                          eq(OverrideSourceRuntime.empty()), //OverrideSource
                                          eq(List.of()), //filterProviders
                                          eq(true), //cachingEnabled
                                          eq(myExecutor), //changesExecutor
                                          eq(false), //keyResolving
                                          isNull() //aliasGenerator
        );
    }

    @Test
    public void testBuildWithDefaultStrategy() {
        String expected = "This value should override the environment variable";
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);
        // system properties now have priority over environment variables
        System.setProperty(TEST_ENV_VAR_NAME, expected);

        Config config = Config.builder()
                .sources(sources())
                .build();

        assertThat(config.get("prop1").asString().get(), is("source-1"));
        assertThat(config.get("prop2").asString().get(), is("source-2"));
        assertThat(config.get("prop3").asString().get(), is("source-3"));
        assertThat(config.get(TEST_SYS_PROP_NAME).asString().get(), is(TEST_SYS_PROP_VALUE));
        assertThat(config.get(TEST_ENV_VAR_NAME).asString().get(), is(expected));

        // once we do the replacement, we hit the environment variable only (as there is no replacement for other sources)
        String envVarName = TEST_ENV_VAR_NAME.toLowerCase().replace("_", ".");
        assertThat(config.get(envVarName).asString().get(), is(TEST_ENV_VAR_VALUE));
    }

    @Test
    public void testBuildDisableEnvVars() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        Config config = Config.builder()
                .sources(sources())
                .disableEnvironmentVariablesSource()
                .build();

        assertThat(config.get("prop1").asString().get(), is("source-1"));
        assertThat(config.get("prop2").asString().get(), is("source-2"));
        assertThat(config.get("prop3").asString().get(), is("source-3"));
        assertThat(config.get(TEST_SYS_PROP_NAME).asString().get(), is(TEST_SYS_PROP_VALUE));
        assertThat(config.get(TEST_ENV_VAR_NAME).type(), is(Config.Type.MISSING));
    }

    @Test
    public void testBuildDisableSysProps() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        Config config = Config.builder()
                .sources(sources())
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("prop1").asString().get(), is("source-1"));
        assertThat(config.get("prop2").asString().get(), is("source-2"));
        assertThat(config.get("prop3").asString().get(), is("source-3"));
        assertThat(config.get(TEST_SYS_PROP_NAME).type(), is(Config.Type.MISSING));
        assertThat(config.get(TEST_ENV_VAR_NAME).asString().get(), is(TEST_ENV_VAR_VALUE));
    }

    @Test
    public void testBuildDisableSysPropsAndEnvVars() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        Config config = Config.builder()
                .sources(sources())
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        assertThat(config.get("prop1").asString().get(), is("source-1"));
        assertThat(config.get("prop2").asString().get(), is("source-2"));
        assertThat(config.get("prop3").asString().get(), is("source-3"));
        assertThat(config.get(TEST_SYS_PROP_NAME).type(), is(Config.Type.MISSING));
        assertThat(config.get(TEST_ENV_VAR_NAME).type(), is(Config.Type.MISSING));
    }

    static List<Supplier<? extends ConfigSource>> sources() {
        return List.of(
                ConfigSources.create(ConfigNode.ObjectNode.builder()
                                             .addValue("prop1", "source-1")
                                             .build()),
                ConfigSources.create(ConfigNode.ObjectNode.builder()
                                             .addValue("prop1", "source-2")
                                             .addValue("prop2", "source-2")
                                             .build()),
                ConfigSources.create(ConfigNode.ObjectNode.builder()
                                             .addValue("prop1", "source-3")
                                             .addValue("prop3", "source-3")
                                             .build()));
    }
}
