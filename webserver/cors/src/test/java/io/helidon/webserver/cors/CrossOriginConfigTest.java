/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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
package io.helidon.webserver.cors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.webserver.cors.CrossOriginConfig.Builder.ALLOW_ALL;
import static io.helidon.webserver.cors.CrossOriginConfig.DEFAULT_AGE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

public class CrossOriginConfigTest {

    private final static String YAML_PATH = "/configMapperTest.yaml";

    private static Config testConfig;

    @BeforeAll
    public static void loadTestConfig() {
        testConfig = TestUtil.minimalConfig(ConfigSources.classpath(YAML_PATH));
    }

    @Test
    public void testNarrow() {
        Config node = testConfig.get("narrow");
        assertThat(node, is(notNullValue()));
        assertThat(node.exists(), is(true));
        CrossOriginConfig c = node.as(CrossOriginConfig::create).get();

        assertThat(c.isEnabled(), is(true));
        assertThat(c.allowOrigins(), arrayContaining("http://foo.bar", "http://bar.foo"));
        assertThat(c.allowMethods(), arrayContaining("DELETE", "PUT"));
        assertThat(c.allowHeaders(), arrayContaining("X-bar", "X-foo"));
        assertThat(c.exposeHeaders(), is(emptyArray()));
        assertThat(c.allowCredentials(), is(true));
        assertThat(c.maxAgeSeconds(), is(-1L));
    }

    @Test
    public void testMissing() {
        Assertions.assertThrows(MissingValueException.class, () -> {
                CrossOriginConfig basic = testConfig.get("notThere").as(CrossOriginConfig::create).get();
        });
    }

    @Test
    public void testWide() {
        Config node = testConfig.get("wide");
        assertThat(node, is(notNullValue()));
        assertThat(node.exists(), is(true));
        CrossOriginConfig b = node.as(CrossOriginConfig::create).get();

        assertThat(b.isEnabled(), is(false));
        assertThat(b.allowOrigins(), arrayContaining(ALLOW_ALL));
        assertThat(b.allowMethods(), arrayContaining(ALLOW_ALL));
        assertThat(b.allowHeaders(), arrayContaining(ALLOW_ALL));
        assertThat(b.exposeHeaders(), is(emptyArray()));
        assertThat(b.allowCredentials(), is(false));
        assertThat(b.maxAgeSeconds(), is(DEFAULT_AGE));
    }

    @Test
    public void testJustDisabled() {
        Config node = testConfig.get("just-disabled");
        assertThat(node, is(notNullValue()));
        assertThat(node.exists(), is(true));
        CrossOriginConfig b = node.as(CrossOriginConfig::create).get();

        assertThat(b.isEnabled(), is(false));
    }

    @Test
    public void testPaths() {
        Config node = testConfig.get("cors-setup");
        assertThat(node, is(notNullValue()));
        assertThat(node.exists(), is(true));
        MappedCrossOriginConfig m = node.as(MappedCrossOriginConfig::create).get();

        assertThat(m.isEnabled(), is(true));

        CrossOriginConfig b = m.get("/cors1");
        assertThat(b, notNullValue());
        assertThat(b.isEnabled(), is(true));
        assertThat(b.allowOrigins(), arrayContaining("*"));
        assertThat(b.allowMethods(), arrayContaining("*"));
        assertThat(b.allowHeaders(), arrayContaining("*"));
        assertThat(b.allowCredentials(), is(false));
        assertThat(b.maxAgeSeconds(), is(DEFAULT_AGE));

        b = m.get("/cors2");
        assertThat(b, notNullValue());
        assertThat(b.isEnabled(), is(true));
        assertThat(b.allowOrigins(), arrayContaining("http://foo.bar", "http://bar.foo"));
        assertThat(b.allowMethods(), arrayContaining("DELETE", "PUT"));
        assertThat(b.allowHeaders(), arrayContaining("X-bar", "X-foo"));
        assertThat(b.allowCredentials(), is(true));
        assertThat(b.maxAgeSeconds(), is(-1L));

        assertThat(m.get("/cors3"), nullValue());
    }

    @Test
    void testWildcardCredentialsRejected() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                                                                     () -> CrossOriginConfig.builder()
                                                                             .allowCredentials(true)
                                                                             .build());

        assertThat(exception.getMessage(), is("CORS cannot allow credentials with wildcard origins"));
    }

    @Test
    void testWildcardCredentialsConfigRejected() {
        Config config = Config.create(ConfigSources.create(Map.of("allow-credentials", "true")));

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                                                                     () -> CrossOriginConfig.create(config));

        assertThat(exception.getMessage(), is("CORS cannot allow credentials with wildcard origins"));
    }

    @Test
    void testDisabledWildcardCredentialsAllowed() {
        CrossOriginConfig config = CrossOriginConfig.builder()
                .enabled(false)
                .allowCredentials(true)
                .build();

        assertThat(config.isEnabled(), is(false));
        assertThat(config.allowCredentials(), is(true));
        assertThat(config.allowOrigins(), arrayContaining(CrossOriginConfig.Builder.ALLOW_ALL));
    }

    @Test
    void testMappedWildcardCredentialsRejected() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                                                                     () -> MappedCrossOriginConfig.builder()
                                                                             .put("/test",
                                                                                  CrossOriginConfig.builder()
                                                                                          .allowCredentials(true))
                                                                             .build());

        assertThat(exception.getMessage(), is("CORS cannot allow credentials with wildcard origins"));
    }

    @Test
    void testMappedConfigWildcardCredentialsRejected() {
        Config config = Config.create(ConfigSources.create(Map.of(
                "paths.0.path-pattern", "/test",
                "paths.0.allow-credentials", "true")));
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                                                                     () -> MappedCrossOriginConfig.create(config));

        assertThat(exception.getMessage(), is("CORS cannot allow credentials with wildcard origins"));
    }

    @Test
    void testDisabledMappedWildcardCredentialsAllowed() {
        MappedCrossOriginConfig.Builder builder = MappedCrossOriginConfig.builder()
                .enabled(false)
                .put("/test", CrossOriginConfig.builder()
                        .allowCredentials(true));
        MappedCrossOriginConfig config = builder.build();

        assertThat(config.isEnabled(), is(false));
        assertThat(config.get("/test").allowCredentials(), is(true));
        assertThat(config.get("/test").allowOrigins(), arrayContaining(CrossOriginConfig.Builder.ALLOW_ALL));

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                                                                     () -> builder.enabled(true).build());

        assertThat(exception.getMessage(), is("CORS cannot allow credentials with wildcard origins"));
    }

    @Test
    void testDisabledMappedConfigWildcardCredentialsAllowed() {
        Config config = Config.create(ConfigSources.create(Map.of(
                "enabled", "false",
                "paths.0.path-pattern", "/test",
                "paths.0.allow-credentials", "true")));
        MappedCrossOriginConfig crossOriginConfig = MappedCrossOriginConfig.create(config);

        assertThat(crossOriginConfig.isEnabled(), is(false));
        assertThat(crossOriginConfig.get("/test").allowCredentials(), is(true));
        assertThat(crossOriginConfig.get("/test").allowOrigins(), arrayContaining(CrossOriginConfig.Builder.ALLOW_ALL));
    }

    @Test
    void testDisabledAggregatorWildcardCredentialsAllowed() {
        Aggregator.Builder builder = Aggregator.builder()
                .enabled(false)
                .allowCredentials(true);
        Aggregator aggregator = builder.build();

        assertThat(aggregator.isEnabled(), is(false));
        assertThat(aggregator.isActive(), is(false));

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                                                                     () -> builder.enabled(true).build());

        assertThat(exception.getMessage(), is("CORS cannot allow credentials with wildcard origins"));
    }

    @Test
    void testDisabledMappedConfigWildcardCredentialsAggregatorAllowed() {
        Config config = Config.create(ConfigSources.create(Map.of(
                "enabled", "false",
                "paths.0.path-pattern", "/test",
                "paths.0.allow-credentials", "true")));
        Aggregator.Builder builder = Aggregator.builder()
                .mappedConfig(config);
        Aggregator aggregator = builder.build();

        assertThat(aggregator.isEnabled(), is(false));
        assertThat(aggregator.isActive(), is(false));

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                                                                     () -> builder.enabled(true).build());

        assertThat(exception.getMessage(), is("CORS cannot allow credentials with wildcard origins"));
    }

    @Test
    void testApiDisabledBeforeSimpleConfigWildcardCredentialsAllowed() {
        Config config = Config.create(ConfigSources.create(Map.of("allow-credentials", "true")));
        Aggregator aggregator = Aggregator.builder()
                .enabled(false)
                .config(config)
                .build();

        assertThat(aggregator.isEnabled(), is(false));
        assertThat(aggregator.isActive(), is(false));
    }

    @Test
    void testApiDisabledAfterSimpleConfigWildcardCredentialsAllowed() {
        Config config = Config.create(ConfigSources.create(Map.of("allow-credentials", "true")));
        Aggregator aggregator = Aggregator.builder()
                .config(config)
                .enabled(false)
                .build();

        assertThat(aggregator.isEnabled(), is(false));
        assertThat(aggregator.isActive(), is(false));
    }

    @Test
    void testApiDisabledBeforeMappedConfigWildcardCredentialsAllowed() {
        Config config = Config.create(ConfigSources.create(Map.of(
                "paths.0.path-pattern", "/test",
                "paths.0.allow-credentials", "true")));
        Aggregator aggregator = Aggregator.builder()
                .enabled(false)
                .mappedConfig(config)
                .build();

        assertThat(aggregator.isEnabled(), is(false));
        assertThat(aggregator.isActive(), is(false));
    }

    @Test
    void testApiDisabledAfterMappedConfigWildcardCredentialsAllowed() {
        Config config = Config.create(ConfigSources.create(Map.of(
                "paths.0.path-pattern", "/test",
                "paths.0.allow-credentials", "true")));
        Aggregator aggregator = Aggregator.builder()
                .mappedConfig(config)
                .enabled(false)
                .build();

        assertThat(aggregator.isEnabled(), is(false));
        assertThat(aggregator.isActive(), is(false));
    }

    @Test
    void testExplicitConfigEnabledStillWins() {
        Config config = Config.create(ConfigSources.create(Map.of("enabled", "true")));
        Aggregator aggregator = Aggregator.builder()
                .enabled(false)
                .mappedConfig(config)
                .build();

        assertThat(aggregator.isEnabled(), is(true));
        assertThat(aggregator.isActive(), is(true));
    }

    @Test
    void testSimpleConfigExplicitlyEnabledWildcardCredentialsRejected() {
        Config config = Config.create(ConfigSources.create(Map.of(
                "enabled", "true",
                "allow-credentials", "true")));
        Aggregator.Builder builder = Aggregator.builder()
                .enabled(false)
                .config(config);

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, builder::build);

        assertThat(exception.getMessage(), is("CORS cannot allow credentials with wildcard origins"));
    }

    @Test
    void testMappedConfigExplicitlyEnabledWildcardCredentialsRejected() {
        Config config = Config.create(ConfigSources.create(Map.of(
                "enabled", "true",
                "paths.0.path-pattern", "/test",
                "paths.0.allow-credentials", "true")));
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                                                                     () -> Aggregator.builder()
                                                                             .enabled(false)
                                                                             .mappedConfig(config)
                                                                             .build());

        assertThat(exception.getMessage(), is("CORS cannot allow credentials with wildcard origins"));
    }

    @Test
    void testOrdering() {
        Config node = testConfig.get("order-check");
        assertThat(node, is(notNullValue()));
        assertThat(node.exists(), is(true));
        MappedCrossOriginConfig m = node.as(MappedCrossOriginConfig::create).get();

        assertThat(m.isEnabled(), is(true));

        //Make sure path elements are in the right order.
        List<String> pathsInOrder = new ArrayList<>();
        List<CrossOriginConfig> crossOriginConfigs = new ArrayList<>();
        m.forEach((path, crossOrginConfig) -> {
            pathsInOrder.add(path);
            crossOriginConfigs.add(crossOrginConfig);
        });

        // Make sure ordering from config is what we expect.
        assertThat("Paths configured", pathsInOrder.size(), is(3));
        assertThat("First path", pathsInOrder.get(0), startsWith("/authorize"));
        assertThat("Second path", pathsInOrder.get(1), startsWith("/callback"));
        assertThat("Third path", pathsInOrder.get(2), is("{^(?!((authorize)|(callback))).*$}"));

        // Make sure the aggregator retains the correct order.
        Aggregator agg = Aggregator.builder().mappedConfig(node).build();

        Optional<CrossOriginConfig> crossOriginConfigOpt = agg.lookupCrossOrigin("/authorize", "GET", Optional::empty);
        assertThat("Match found for /authorize", crossOriginConfigOpt.isPresent(), is(true));
        assertThat("Match for /authorize", crossOriginConfigOpt.get().pathPattern(), is(crossOriginConfigs.get(0).pathPattern()));

        crossOriginConfigOpt = agg.lookupCrossOrigin("/authorize/else", "GET", Optional::empty);
        assertThat("Match found for /authorize/else", crossOriginConfigOpt.isPresent(), is(true));
        assertThat("Match for /authorize/else",
                   crossOriginConfigOpt.get().pathPattern(),
                   is(crossOriginConfigs.get(0).pathPattern()));

        crossOriginConfigOpt = agg.lookupCrossOrigin("/callback", "PUT", Optional::empty);
        assertThat("Match found for /callback", crossOriginConfigOpt.isPresent(), is(true));
        assertThat("Match for /callback", crossOriginConfigOpt.get().pathPattern(), is(crossOriginConfigs.get(1).pathPattern()));

        crossOriginConfigOpt = agg.lookupCrossOrigin("/callback/other", "PUT", Optional::empty);
        assertThat("Match found for /callback/other", crossOriginConfigOpt.isPresent(), is(true));
        assertThat("Match for /callback/other", crossOriginConfigOpt.get().pathPattern(), is(crossOriginConfigs.get(1).pathPattern()));
    }
}
