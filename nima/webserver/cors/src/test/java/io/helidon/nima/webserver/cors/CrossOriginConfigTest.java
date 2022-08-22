/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.nima.webserver.cors;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class CrossOriginConfigTest {

    private final static String YAML_PATH = "/configMapperTest.yaml";

    private static Config testConfig;

    @BeforeAll
    static void loadTestConfig() {
        testConfig = Config.just(ConfigSources.classpath(YAML_PATH));
    }

    @Test
    void testNarrow() {
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
    void testMissing() {
        Assertions.assertThrows(MissingValueException.class, () -> {
            CrossOriginConfig basic = testConfig.get("notThere").as(CrossOriginConfig::create).get();
        });
    }

    @Test
    void testWide() {
        Config node = testConfig.get("wide");
        assertThat(node, is(notNullValue()));
        assertThat(node.exists(), is(true));
        CrossOriginConfig b = node.as(CrossOriginConfig::create).get();

        assertThat(b.isEnabled(), is(false));
        MatcherAssert.assertThat(b.allowOrigins(), Matchers.arrayContaining(CrossOriginConfig.Builder.ALLOW_ALL));
        MatcherAssert.assertThat(b.allowMethods(), Matchers.arrayContaining(CrossOriginConfig.Builder.ALLOW_ALL));
        MatcherAssert.assertThat(b.allowHeaders(), Matchers.arrayContaining(CrossOriginConfig.Builder.ALLOW_ALL));
        assertThat(b.exposeHeaders(), is(emptyArray()));
        assertThat(b.allowCredentials(), is(false));
        MatcherAssert.assertThat(b.maxAgeSeconds(), Matchers.is(CrossOriginConfig.DEFAULT_AGE));
    }

    @Test
    void testJustDisabled() {
        Config node = testConfig.get("just-disabled");
        assertThat(node, is(notNullValue()));
        assertThat(node.exists(), is(true));
        CrossOriginConfig b = node.as(CrossOriginConfig::create).get();

        assertThat(b.isEnabled(), is(false));
    }

    @Test
    void testPaths() {
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
        MatcherAssert.assertThat(b.maxAgeSeconds(), Matchers.is(CrossOriginConfig.DEFAULT_AGE));

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
}
