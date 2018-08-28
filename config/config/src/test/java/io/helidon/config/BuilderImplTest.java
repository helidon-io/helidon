/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Executor;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.reactive.Flow;
import static io.helidon.config.spi.ConfigSourceTest.TEST_ENV_VAR_NAME;
import static io.helidon.config.spi.ConfigSourceTest.TEST_ENV_VAR_VALUE;
import io.helidon.config.test.infra.RestoreSystemPropertiesExt;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
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
        Config.Builder builder = Config.withSources(ConfigSources.empty());
        BuilderImpl spyBuilder = spy((BuilderImpl) builder);
        spyBuilder
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        verify(spyBuilder).createProvider(notNull(), //ConfigMapperManager
                                          eq(ConfigSources.empty()), //ConfigSource
                                          eq(OverrideSources.empty()), //OverrideSource
                                          eq(CollectionsHelper.listOf()), //filterProviders
                                          eq(true), //cachingEnabled
                                          eq(BuilderImpl.DEFAULT_CHANGES_EXECUTOR), //changesExecutor
                                          eq(Flow.defaultBufferSize()), //changesMaxBuffer
                                          eq(true) //keyResolving
        );
    }

    @Test
    public void testBuildCustomChanges() {
        Executor myExecutor = Runnable::run;
        Config.Builder builder = Config.builder()
                .sources(ConfigSources.empty())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .changesExecutor(myExecutor)
                .changesMaxBuffer(1);
        BuilderImpl spyBuilder = spy((BuilderImpl) builder);
        spyBuilder.build();

        verify(spyBuilder).createProvider(notNull(), //ConfigMapperManager
                                          eq(ConfigSources.empty()), //ConfigSource
                                          eq(OverrideSources.empty()), //OverrideSource
                                          eq(CollectionsHelper.listOf()), //filterProviders
                                          eq(true), //cachingEnabled
                                          eq(myExecutor), //changesExecutor
                                          eq(1), //changesMaxBuffer
                                          eq(true) //keyResolving
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
                .changesExecutor(myExecutor)
                .changesMaxBuffer(1);
        BuilderImpl spyBuilder = spy((BuilderImpl) builder);
        spyBuilder.build();

        verify(spyBuilder).createProvider(notNull(), //ConfigMapperManager
                                          eq(ConfigSources.empty()), //ConfigSource
                                          eq(OverrideSources.empty()), //OverrideSource
                                          eq(CollectionsHelper.listOf()), //filterProviders
                                          eq(true), //cachingEnabled
                                          eq(myExecutor), //changesExecutor
                                          eq(1), //changesMaxBuffer
                                          eq(false) //keyResolving
        );
    }

    @Test
    public void testBuildWithDefaultStrategy() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);
        System.setProperty(TEST_ENV_VAR_NAME, "This value is not used, but from Env Vars, see pom.xml!");

        Config config = Config.builder()
                .sources(CompositeConfigSourceTest.initBuilder().build())
                .build();

        assertThat(config.get("prop1").asString(), is("source-1"));
        assertThat(config.get("prop2").asString(), is("source-2"));
        assertThat(config.get("prop3").asString(), is("source-3"));
        assertThat(config.get(TEST_SYS_PROP_NAME).asString(), is(TEST_SYS_PROP_VALUE));
        assertThat(config.get(TEST_ENV_VAR_NAME).asString(), is(TEST_ENV_VAR_VALUE));
    }

    @Test
    public void testBuildDisableEnvVars() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        Config config = Config.builder()
                .sources(CompositeConfigSourceTest.initBuilder().build())
                .disableEnvironmentVariablesSource()
                .build();

        assertThat(config.get("prop1").asString(), is("source-1"));
        assertThat(config.get("prop2").asString(), is("source-2"));
        assertThat(config.get("prop3").asString(), is("source-3"));
        assertThat(config.get(TEST_SYS_PROP_NAME).asString(), is(TEST_SYS_PROP_VALUE));
        Config c = config.get(TEST_ENV_VAR_NAME);
        System.err.println("BuilderImplTest: envVar type is " + c.type().toString());
        System.err.println("  and value is " + (c.value().isPresent() ? c.value().toString() : "value not present"));
        
        assertThat(config.get(TEST_ENV_VAR_NAME).type(), is(Config.Type.MISSING));
    }

    @Test
    public void testBuildDisableSysProps() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        Config config = Config.builder()
                .sources(CompositeConfigSourceTest.initBuilder().build())
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("prop1").asString(), is("source-1"));
        assertThat(config.get("prop2").asString(), is("source-2"));
        assertThat(config.get("prop3").asString(), is("source-3"));
        assertThat(config.get(TEST_SYS_PROP_NAME).type(), is(Config.Type.MISSING));
        assertThat(config.get(TEST_ENV_VAR_NAME).asString(), is(TEST_ENV_VAR_VALUE));
    }

    @Test
    public void testBuildDisableSysPropsAndEnvVars() {
        System.setProperty(TEST_SYS_PROP_NAME, TEST_SYS_PROP_VALUE);

        Config config = Config.builder()
                .sources(CompositeConfigSourceTest.initBuilder().build())
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        assertThat(config.get("prop1").asString(), is("source-1"));
        assertThat(config.get("prop2").asString(), is("source-2"));
        assertThat(config.get("prop3").asString(), is("source-3"));
        assertThat(config.get(TEST_SYS_PROP_NAME).type(), is(Config.Type.MISSING));
        assertThat(config.get(TEST_ENV_VAR_NAME).type(), is(Config.Type.MISSING));
    }

}
