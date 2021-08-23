/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.openapi;

import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.common.http.Http;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.openapi.OpenAPISupport;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test that MP OpenAPI support works when retrieving the OpenAPI document
 * from the server's /openapi endpoint.
 */
@HelidonTest
@AddBean(TestApp.class)
@AddBean(TestApp3.class)
public class BasicServerTest {

    private static Map<String, Object> yaml;

    @Inject
    WebTarget webTarget;

    private static Map<String, Object> retrieveYaml(WebTarget webTarget) {
        try (Response response = webTarget
                .path(OpenAPISupport.DEFAULT_WEB_CONTEXT)
                .request(OpenAPISupport.DEFAULT_RESPONSE_MEDIA_TYPE.toString())
                .get()) {
            assertThat("Fetch of OpenAPI document from server status", response.getStatus(),
                    is(equalTo(Http.Status.OK_200.code())));
            String yamlText = response.readEntity(String.class);
            return new Yaml().load(yamlText);
        }
    }

    private static Map<String, Object> yaml(WebTarget webTarget) {
        if (yaml == null) {
            yaml = retrieveYaml(webTarget);
        }
        return yaml;
    }

    private Map<String, Object> yaml() {
        return yaml(webTarget);
    }

    public BasicServerTest() {
    }

    /**
     * Make sure that the annotations in the test app were found and properly
     * incorporated into the OpenAPI document.
     *
     * @throws Exception in case of errors reading the HTTP response
     */
    @Test
    public void simpleTest() throws Exception {
        checkPathValue("paths./testapp/go.get.summary", TestApp.GO_SUMMARY);
    }

    @Test
    public void testMultipleApps() {
        checkPathValue("paths./testapp3/go3.get.summary", TestApp3.GO_SUMMARY);
    }

    private void checkPathValue(String pathExpression, String expected) {
        String result = TestUtil.fromYaml(yaml(), pathExpression, String.class);
        assertThat(pathExpression, result, is(equalTo(expected)));
    }
}
