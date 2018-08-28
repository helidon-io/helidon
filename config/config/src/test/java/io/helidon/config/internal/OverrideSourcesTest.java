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

package io.helidon.config.internal;

import java.net.MalformedURLException;
import java.net.URL;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.config.OverrideSources;
import io.helidon.config.spi.OverrideSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import org.junit.jupiter.api.Test;

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
        OverrideSource overrideSource = OverrideSources.from(CollectionsHelper.mapOf(WILDCARDS, "localhost"));
        assertThat(overrideSource.load()
                           .get()
                           .data()
                           .stream()
                           .findFirst()
                           .get()
                           .getKey()
                           .test(Config.Key.of("prod.tenant1.audit.host")), is(true));
    }

    @Test
    public void testUrlBuilder() throws MalformedURLException {
        UrlOverrideSource.UrlBuilder builder = (UrlOverrideSource.UrlBuilder) OverrideSources.url(new URL("http://localhost"));
        assertThat(builder.build(), instanceOf(UrlOverrideSource.class));
    }

}
