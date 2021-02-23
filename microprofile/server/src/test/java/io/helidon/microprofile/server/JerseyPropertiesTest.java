/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.microprofile.server;

import javax.inject.Inject;

import io.helidon.config.Config;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.webserver.jersey.JerseySupport;
import org.junit.jupiter.api.Test;

import static org.glassfish.jersey.client.ClientProperties.IGNORE_EXCEPTION_RESPONSE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test that it is possible to override {@code IGNORE_EXCEPTION_RESPONSE} in
 * Jersey using config. See {@link io.helidon.webserver.jersey.JerseySupport}
 * for more information.
 */
@HelidonTest
@AddConfig(key = IGNORE_EXCEPTION_RESPONSE, value = "false")
class JerseyPropertiesTest {

    @Inject
    Config config;

    @Test
    void testIgnoreExceptionResponseOverride() {
        JerseySupport jerseySupport = JerseySupport.builder().config(config).build();
        assertNotNull(jerseySupport);
        assertThat(System.getProperty(IGNORE_EXCEPTION_RESPONSE), is("false"));
    }
}
