/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import java.util.LinkedHashMap;
import java.util.function.Supplier;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.OverrideSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the resolving of key token on config sources like:
 * <pre>
 *     region = region-eu
 *     $region.proxy = http://proxy-eu.company.com
 *
 * </pre>
 */
public class KeyTokenResolvingTest {

    @Test
    public void testResolveTokenConfig() {
        Config config = Config.builder()
                .sources(ConfigSources.create(ConfigNode.ObjectNode.builder()
                                                    .addValue("ad", "ad1")
                                                    .addValue("$region.$ad.url", "http://localhost:8080")
                                                    .addValue("region", "region-eu1")
                                                    .build()))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        assertThat(config.asMap().get().entrySet(), hasSize(3));

        assertThat(config.get("ad").asString().get(), is("ad1"));
        assertThat(config.get("region").asString().get(), is("region-eu1"));
        assertThat(config.get("region-eu1.ad1.url").asString().get(), is("http://localhost:8080"));
        assertThat(config.get("$region").exists(), is(false));
        assertThat(config.get("$region.$ad").exists(), is(false));
    }

    @Test
    public void testDisableResolveTokenConfig() {
        Config config = Config.builder()
                .sources(ConfigSources.create(ConfigNode.ObjectNode.builder()
                                                    .addValue("ad", "ad1")
                                                    .addValue("$region.$ad.url", "http://localhost:8080")
                                                    .addValue("region", "region-eu1")
                                                    .build()))
                .disableKeyResolving()
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        assertThat(config.asMap().get().entrySet(), hasSize(3));

        assertThat(config.get("ad").asString().get(), is("ad1"));
        assertThat(config.get("region").asString().get(), is("region-eu1"));
        assertThat(config.get("$region").exists(), is(true));
        assertThat(config.get("$region.$ad").exists(), is(true));
        assertThat(config.get("$region.$ad.url").asString().get(), is("http://localhost:8080"));
    }

    @Test
    public void testResolveTokenConfig2() {
        Config config = Config.builder()
                .sources(ConfigSources.create(ConfigNode.ObjectNode.builder()
                                                    .addValue("env.ad", "ad1")
                                                    .addValue("env.region", "region-eu1")
                                                    .addValue("${env.region}.${env.ad}.url", "http://localhost:8080")
                                                    .build()))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        assertThat(config.asMap().get().entrySet(), hasSize(3));

        assertThat(config.get("env.ad").asString().get(), is("ad1"));
        assertThat(config.get("env.region").asString().get(), is("region-eu1"));
        assertThat(config.get("region-eu1.ad1.url").asString().get(), is("http://localhost:8080"));
        assertThat(config.get("$region").exists(), is(false));
        assertThat(config.get("$region.$ad").exists(), is(false));
    }

    @Test
    public void testResolveTokenConfig3() {
        Config config = Config.builder()
                .sources(ConfigSources.create(ConfigNode.ObjectNode.builder()
                                                    .addValue("env.ad", "ad1")
                                                    .addValue("env.region", "region-eu1")
                                                    .addObject("${env.region}", ConfigNode.ObjectNode.builder()
                                                            .addObject("${env.ad}", ConfigNode.ObjectNode.builder()
                                                                    .addValue("url", "http://localhost:8080")
                                                                    .build())
                                                            .build())
                                                    .build()))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        assertThat(config.asMap().get().entrySet(), hasSize(3));

        assertThat(config.get("env.ad").asString().get(), is("ad1"));
        assertThat(config.get("env.region").asString().get(), is("region-eu1"));
        assertThat(config.get("region-eu1.ad1.url").asString().get(), is("http://localhost:8080"));
        assertThat(config.get("$region").exists(), is(false));
        assertThat(config.get("$region.$ad").exists(), is(false));
    }

    @Test
    public void testResolveTokenConfig4() {
        Supplier<OverrideSource> overrideSource = OverrideSources.create(new LinkedHashMap<String,String>() {
                                                                           {
                                                                               put("prod.inventory.logging.level", "WARN");
                                                                               put("test.*.logging.level", "FINE");
                                                                               put("*.*.logging.level", "ERROR");
                                                                           }
                                                                       }
        );

        // configuration with a source that declares the environment is 'test'
        Config testConfig = Config.builder()
                .sources(ConfigSources.create(ConfigNode.ObjectNode.builder()
                                                    // the only difference in config source
                                                    .addValue("env", "test")
                                                    .addValue("component", "inventory")
                                                    .addValue("$env.$component.logging.level", "INFO")
                                                    .build()))
                .overrides(overrideSource)
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        // configuration with a source that declares the environment is 'prod'
        Config prodConfig = Config.builder()
                .sources(ConfigSources.create(ConfigNode.ObjectNode.builder()
                                                    // the only difference in config source
                                                    .addValue("env", "prod")
                                                    .addValue("component", "inventory")
                                                    .addValue("$env.$component.logging.level", "INFO")
                                                    .build()))
                .overrides(overrideSource)
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        assertThat(testConfig.asMap().get().entrySet(), hasSize(3));

        assertThat(testConfig.get("test.inventory.logging.level").asString().get(), is("FINE"));
        assertThat(prodConfig.get("prod.inventory.logging.level").asString().get(), is("WARN"));
    }

    @Test
    public void testResolveChainedTokensConfig() {
        ConfigException ex = assertThrows(ConfigException.class, () -> {
            Config config = Config.builder()
                    .sources(ConfigSources.create(ConfigNode.ObjectNode.builder()
                                                        .addValue("region", "")
                                                        .addValue("$region", "missing")
                                                        .build()))
                    .disableSystemPropertiesSource()
                    .disableEnvironmentVariablesSource()
                    .build();

            config.traverse().forEach(System.out::println);
        });
        assertThat(ex.getMessage(), startsWith("Missing value in token 'region' definition"));
    }

    @Test
    public void testResolveTokenMissingValue() {
        ConfigException ex = assertThrows(ConfigException.class, () -> {
            Config.builder()
                .sources(ConfigSources.create(ConfigNode.ObjectNode.builder()
                                                    .addValue("$region.$ad.url", "http://localhost:8080")
                                                    .addValue("region", "region-eu1")
                                                    .build()))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        });
        assertThat(ex.getMessage(), startsWith("Missing token 'ad' to resolve"));

    }

    @Test
    public void testResolveTokenReferenceToReference() {
        ConfigException ex = assertThrows(ConfigException.class, () -> {
            Config config = Config.builder()
                .sources(ConfigSources.create(ConfigNode.ObjectNode.builder()
                                                    .addValue("env.region", "eu")
                                                    .addValue("$region.url", "http://localhost:8080")
                                                    .addValue("region", "${env.region}")
                                                    .build()))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

            config.traverse().forEach(System.out::println);
        });
        assertThat(ex.getMessage(), startsWith("Key token 'region' references to a reference in value. A recursive references is not allowed"));

        

    }

    @Test
    public void testResolveTokenWithDottedValue() {

        Config config = Config.builder()
                .sources(ConfigSources.create(ConfigNode.ObjectNode.builder()
                                                    .addValue("domain", "oracle.com")
                                                    .addValue("$domain.sso", "on")
                                                    .addValue(Config.Key.escapeName("seznam.cz") + ".sso", "off")
                                                    .build()))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        config.traverse().forEach(System.out::println);

        assertThat(config.get("oracle").exists(), is(false));
        assertThat(config.get("oracle~1com").exists(), is(true));
        assertThat(config.get("oracle~1com").type(), is(Config.Type.OBJECT));
        assertThat(config.get("oracle~1com.sso").asString().get(), is("on"));

        assertThat(config.get("seznam").exists(), is(false));
        assertThat(config.get("seznam~1cz").exists(), is(true));
        assertThat(config.get("seznam~1cz").type(), is(Config.Type.OBJECT));
        assertThat(config.get("seznam~1cz.sso").asString().get(), is("off"));

    }

}
