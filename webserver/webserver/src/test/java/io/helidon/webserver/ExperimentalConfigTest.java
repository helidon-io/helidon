/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Class ExperimentalConfigTest.
 */
public class ExperimentalConfigTest {

    @Test
    public void http2BuilderDefaults() {
        Http2Configuration http2 = new Http2Configuration.Builder().build();
        assertThat(http2.enable(), is(false));
        assertThat(http2.maxContentLength(), is(Http2Configuration.DEFAULT_MAX_CONTENT_LENGTH));
    }

    @Test
    public void configBuilder() {
        Http2Configuration.Builder builder = new Http2Configuration.Builder();
        builder.enable(true);
        builder.maxContentLength(32 * 1024);
        Http2Configuration http2 = builder.build();
        assertThat(http2.enable(), is(true));
        assertThat(http2.maxContentLength(), is(32 * 1024));
        ExperimentalConfiguration config = ExperimentalConfiguration.builder().http2(http2).build();
        assertThat(config.http2(), is(http2));
    }

    @Test
    public void configResource() {
        Config http2 = Config.create(ConfigSources.classpath("experimental/application.yaml"))
                .get("webserver")
                .get("experimental")
                .get("http2");
        assertThat(http2.get("enable").asBoolean().get(), is(true));
        assertThat((int) http2.get("max-content-length").asInt().get(), is(16 * 1024));
    }
}
