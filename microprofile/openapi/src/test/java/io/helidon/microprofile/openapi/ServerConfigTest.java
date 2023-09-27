/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi;

import java.util.Map;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddConfig(key = "openapi.web-context", value = "/alt-openapi")
@AddBean(TestApp.class)
class ServerConfigTest {

    private static final String APPLICATION_OPENAPI_YAML = MediaTypes.APPLICATION_OPENAPI_YAML.text();

    @Inject
    private WebTarget webTarget;

    @Test
    public void testAlternatePath() {
        Map<String, Object> document = document();
        String summary = TestUtil.query(document, "paths./testapp/go.get.summary", String.class);
        assertThat(summary, is(TestApp.GO_SUMMARY));
    }

    private Map<String, Object> document() {
        try (Response response = webTarget.path("/alt-openapi").request(APPLICATION_OPENAPI_YAML).get()) {
            assertThat(response.getStatus(), is(200));
            String yamlText = response.readEntity(String.class);
            return new Yaml().load(yamlText);
        }
    }
}
