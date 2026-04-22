/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.net.InetAddress.getLocalHost;
import static io.helidon.common.media.type.MediaTypes.APPLICATION_YAML;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

final class TestJsonSerializationFromConfig {

    private Config config;

    TestJsonSerializationFromConfig() {
        super();
    }

    @BeforeEach
    void setup() {
        this.config = yaml("""
                           eureka:
                             instance:
                               name: "My Application"
                           """);
    }

    @Test
    void testSerialization() throws UnknownHostException {
        int actualPort = 8080;
        InstanceInfoConfig iic = InstanceInfoConfig.builder()
            .config(this.config.get("eureka.instance"))
            .build();
        JsonObject json = EurekaRegistrationHttpFeature.json(iic, actualPort, false)
            .objectValue("instance")
            .orElseThrow();
        assertThat(json, not(nullValue()));
        assertThat(json.stringValue("instanceId").orElseThrow(), is(getLocalHost().getHostName() + ":" + actualPort));
        assertThat(json.stringValue("app").orElseThrow(), is("My Application")); // explicitly set
        assertThat(json.stringValue("appGroupName").orElseThrow(), is("unknown"));
        assertThat(json.objectValue("dataCenterInfo").orElseThrow().stringValue("name").orElseThrow(), is("MyOwn"));
        assertThat(json.objectValue("dataCenterInfo").orElseThrow().stringValue("@class").orElseThrow(),
                   is("com.netflix.appinfo.MyDataCenterInfo"));
        assertThat(json.stringValue("ipAddr").orElseThrow(), is(getLocalHost().getHostAddress()));
        assertThat(json.stringValue("hostName").orElseThrow(), is(getLocalHost().getHostName()));
        assertThat(json.objectValue("port").orElseThrow().intValue("$").orElseThrow(), is(actualPort));
        assertThat(json.objectValue("port").orElseThrow().value("@enabled").orElseThrow(), is(JsonBoolean.TRUE));
        assertThat(json.objectValue("securePort").orElseThrow().intValue("$").orElseThrow(), is(443));
        assertThat(json.objectValue("securePort").orElseThrow().value("@enabled").orElseThrow(), is(JsonBoolean.FALSE));
        assertThat(json.stringValue("status").orElseThrow(), is("UP"));
        assertThat(json.objectValue("metadata").orElseThrow(), not(nullValue()));
        assertThat(json.stringValue("sid").orElseThrow(), is("na"));
        assertThat(json.intValue("countryId").orElseThrow(), is(1));
        assertThat(json.objectValue("leaseInfo").orElseThrow().intValue("renewalIntervalInSecs").orElseThrow(), is(30));
        assertThat(json.objectValue("leaseInfo").orElseThrow().intValue("durationInSecs").orElseThrow(), is(90));
    }

    private static Config yaml(String yaml) {
        return io.helidon.config.Config.create(ConfigSources.create(yaml, APPLICATION_YAML));
    }

}
