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
    public void configBuilderDefault() {
        ExperimentalConfiguration config = new ExperimentalConfiguration.Builder().build();
        assertFalse(config.enableHttp2());
        assertEquals(ExperimentalConfiguration.DEFAULT_MAX_CONTENT_LENGTH, config.http2MaxContentLength());
    }

    @Test
    public void configBuilder() {
        ExperimentalConfiguration.Builder builder = new ExperimentalConfiguration.Builder();
        builder.enableHttp2(true);
        builder.http2MaxContentLength(32 * 1024);
        ExperimentalConfiguration config = builder.build();
        assertTrue(config.enableHttp2());
        assertEquals(32 * 1024, config.http2MaxContentLength());
    }

    @Test
    public void configResource() {
        Config experimental = Config.from(ConfigSources.classpath("experimental/application.yaml"))
                .get("webserver")
                .get("experimental");
        assertTrue(experimental.get("enable-http2").as(Boolean.class));
        assertEquals(64 * 1024, (int) experimental.get("http2-max-content-length").as(Integer.class));
    }
}
