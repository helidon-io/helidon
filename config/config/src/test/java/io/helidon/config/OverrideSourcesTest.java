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
 */

package io.helidon.config;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import io.helidon.config.spi.OverrideSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

/**
 * Tests {@link OverrideSources}.
 */
public class OverrideSourcesTest {

    private static final String WILDCARDS = "*.*.audit.host";

    @Test
    public void testEmptyIsAlwaysTheSameInstance() {
        assertThat(OverrideSources.empty(), sameInstance(OverrideSources.empty()));
    }

    @Test
    public void testFromWildcards() {
        OverrideSource overrideSource = OverrideSources.create(Map.of(WILDCARDS, "localhost"));

        assertThat(overrideSource.load()
                           .get()
                           .data()
                           .data()
                           .stream()
                           .findFirst()
                           .get()
                           .getKey()
                           .test(Config.Key.create("prod.tenant1.audit.host")), is(true));
    }

    @Test
    public void testUrlBuilder() throws MalformedURLException {
        UrlOverrideSource.Builder builder = OverrideSources.url(new URL("http://localhost"));
        assertThat(builder.build(), instanceOf(UrlOverrideSource.class));
    }

}
