/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Class ExperimentalConfigTest.
 */
public class ExperimentalConfigTest {

    @Test
    public void http2BuilderDefaults() {
        Http2Configuration http2 = new Http2Configuration.Builder().build();
        assertFalse(http2.enable());
        assertEquals(Http2Configuration.DEFAULT_MAX_CONTENT_LENGTH, http2.maxContentLength());
    }

    @Test
    public void configBuilder() {
        Http2Configuration.Builder builder = new Http2Configuration.Builder();
        builder.enable(true);
        builder.maxContentLength(32 * 1024);
        Http2Configuration http2 = builder.build();
        assertTrue(http2.enable());
        assertEquals(32 * 1024, http2.maxContentLength());
        ExperimentalConfiguration config = new ExperimentalConfiguration.Builder().http2(http2).build();
        assertEquals(http2, config.http2());
    }

    @Test
    public void configResource() {
        Config http2 = Config.from(ConfigSources.classpath("experimental/application.yaml"))
                .get("webserver")
                .get("experimental")
                .get("http2");
        assertTrue(http2.get("enable").as(Boolean.class));
        assertEquals(16 * 1024, (int) http2.get("max-content-length").as(Integer.class));
    }
}
