/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.openapi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;


class ParserTest {

    @Test
    public void testParserUsingYAML() throws IOException {
        OpenAPI openAPI = parse("/petstore.yaml", OpenAPISupport.OpenAPIMediaType.YAML);
        assertThat(openAPI.getOpenapi(), is("3.0.0"));
        assertThat(openAPI.getPaths().getPathItem("/pets").getGET().getParameters().get(0).getIn(),
                is(Parameter.In.QUERY));
    }

    @Test
    public void testExtensions() throws IOException {
        OpenAPI openAPI = parse("/openapi-greeting.yml", OpenAPISupport.OpenAPIMediaType.YAML);
        Object xMyPersonalMap = openAPI.getExtensions().get("x-my-personal-map");
        assertThat(xMyPersonalMap, is(instanceOf(Map.class)));
        Map<?,?> map = (Map<?,?>) xMyPersonalMap;
        Object owner = map.get("owner");
        Object value1 = map.get("value-1");
        assertThat(value1, is(instanceOf(Double.class)));
        Double d = (Double) value1;
        assertThat(d, equalTo(2.3));

        assertThat(owner, is(instanceOf(Map.class)));
        map = (Map<?,?>) owner;
        assertThat(map.get("first"), equalTo("Me"));
        assertThat(map.get("last"), equalTo("Myself"));
    }

    @Test
    public void testParserUsingJSON() throws IOException {
        OpenAPI openAPI = parse("/petstore.json", OpenAPISupport.OpenAPIMediaType.JSON);
        assertThat(openAPI.getOpenapi(), is("3.0.0"));
// TODO - uncomment the following once full $ref support is in place
//        assertThat(openAPI.getPaths().getPathItem("/pet").getPUT().getRequestBody().getDescription(),
//                containsString("needs to be added to the store"));
    }

    static OpenAPI parse(String path, OpenAPISupport.OpenAPIMediaType mediaType) throws IOException {
        try (InputStream is = ParserTest.class.getResourceAsStream(path)) {
            return Parser.parse(is, mediaType);
        }
    }
}
