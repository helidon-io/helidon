/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.integrations.eureka;

import java.net.UnknownHostException;
import java.util.logging.Logger;

import io.helidon.common.config.Config;
import io.helidon.config.ConfigSources;

import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.net.InetAddress.getLocalHost;
import static io.helidon.common.media.type.MediaTypes.APPLICATION_YAML;
import static io.helidon.integrations.eureka.EurekaRegistrationFeature.json;
import static jakarta.json.JsonValue.FALSE;
import static jakarta.json.JsonValue.TRUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestAgainstEurekaServer {

    private Config config;

    TestAgainstEurekaServer() {
        super();
    }

    @BeforeEach
    void setup() {
        this.config = yaml("""
                           eureka:
                             client:
                               registration:
                                 base-uri: "http://localhost:8761/eureka"
                             instance:
                               name: "My Application"
                           """);
    }

    @Test
    @SuppressWarnings("deprecation")
    void testAgainstEurekaServer() throws InterruptedException {
        assumeTrue(System.getProperty("runTestAgainstEurekaServer", "false").equals("true"));
        EurekaRegistrationFeature f = new EurekaRegistrationFeature();
        f.afterStart(this.config, 8762, false);
        Thread.sleep(40000); // 40 seconds
        f.afterStop();
    }

    private static final Config yaml(String yaml) {
        return io.helidon.config.Config.create(ConfigSources.create(yaml, APPLICATION_YAML));
    }

}
