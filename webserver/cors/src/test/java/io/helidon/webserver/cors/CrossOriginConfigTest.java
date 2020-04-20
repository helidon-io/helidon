/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver.cors;

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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

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
}
