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
import java.util.Map;

import io.helidon.common.config.Config;
import io.helidon.config.ConfigSources;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.net.InetAddress.getLocalHost;
import static io.helidon.common.media.type.MediaTypes.APPLICATION_YAML;
import static jakarta.json.Json.createBuilderFactory;
import static jakarta.json.JsonValue.FALSE;
import static jakarta.json.JsonValue.TRUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

final class TestJsonSerializationFromConfig {

    private static JsonBuilderFactory jbf = createBuilderFactory(Map.of());
    
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
        JsonObject json = EurekaRegistrationHttpFeature.json(iic, actualPort, false);
        json = json.getJsonObject("instance");
        assertThat(json, not(nullValue()));
        assertThat(json.getString("instanceId"), is(getLocalHost().getHostName() + ":" + actualPort));
        assertThat(json.getString("app"), is("My Application")); // explicitly set
        assertThat(json.getString("appGroupName"), is("unknown"));
        assertThat(((JsonString)json.getValue("/dataCenterInfo/name")).getString(), is("MyOwn"));
        assertThat(((JsonString)json.getValue("/dataCenterInfo/@class")).getString(), is("com.netflix.appinfo.MyDataCenterInfo"));
        assertThat(json.getString("ipAddr"), is(getLocalHost().getHostAddress()));
        assertThat(json.getString("hostName"), is(getLocalHost().getHostName()));
        assertThat(((JsonNumber)json.getValue("/port/$")).intValueExact(), is(actualPort));
        assertThat(json.getValue("/port/@enabled"), is(TRUE));
        assertThat(((JsonNumber)json.getValue("/securePort/$")).intValueExact(), is(443));
        assertThat(json.getValue("/securePort/@enabled"), is(FALSE));
        assertThat(json.getString("status"), is("UP"));
        assertThat(json.getJsonObject("metadata"), not(nullValue()));
        assertThat(json.getString("sid"), is("na"));
        assertThat(json.getInt("countryId"), is(1));
        assertThat(((JsonNumber)json.getValue("/leaseInfo/renewalIntervalInSecs")).intValueExact(), is(30));
        assertThat(((JsonNumber)json.getValue("/leaseInfo/durationInSecs")).intValueExact(), is(90));
    }

    private static final Config yaml(String yaml) {
        return io.helidon.config.Config.create(ConfigSources.create(yaml, APPLICATION_YAML));
    }

}
