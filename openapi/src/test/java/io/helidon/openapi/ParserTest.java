/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;


class ParserTest {

    private static SnakeYAMLParserHelper<ExpandedTypeDescription> helper = OpenAPISupport.helper();

    @Test
    public void testParserUsingYAML() throws IOException {
        OpenAPI openAPI = parse(helper,"/petstore.yaml", OpenAPISupport.OpenAPIMediaType.YAML);
        assertThat(openAPI.getOpenapi(), is("3.0.0"));
        assertThat(openAPI.getPaths().getPathItem("/pets").getGET().getParameters().get(0).getIn(),
                is(Parameter.In.QUERY));
    }

    @Test
    public void testExtensions() throws IOException {
        OpenAPI openAPI = parse(helper,"/openapi-greeting.yml", OpenAPISupport.OpenAPIMediaType.YAML);
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

        Object xBoolean = openAPI.getExtensions().get("x-boolean");
        assertThat(xBoolean, is(instanceOf(Boolean.class)));
        Boolean b = (Boolean) xBoolean;
        assertThat(b, is(true));

        Object xInt = openAPI.getExtensions().get("x-int");
        assertThat(xInt, is(instanceOf(Integer.class)));
        Integer i = (Integer) xInt;
        assertThat(i, is(117));

        Object xStrings = openAPI.getExtensions().get("x-string-array");
        assertThat(xStrings, is(instanceOf(List.class)));
        List<?> list = (List<?>) xStrings;
        Object first = list.get(0);
        assertThat(first, is(instanceOf(String.class)));
        String f = (String) first;
        assertThat(f, is(equalTo("one")));
    }


    @Test
    void testYamlRef() throws IOException {
        OpenAPI openAPI = parse(helper, "/petstore.yaml", OpenAPISupport.OpenAPIMediaType.YAML);
        Paths paths = openAPI.getPaths();
        String ref = paths.getPathItem("/pets")
                .getGET()
                .getResponses()
                .getAPIResponse("200")
                .getContent()
                .getMediaType("application/json")
                .getSchema()
                .getRef();

        assertThat("ref value", ref, is(equalTo("#/components/schemas/Pets")));
    }

    @Test
    void testJsonRef() throws IOException {
        OpenAPI openAPI = parse(helper, "/petstore.json", OpenAPISupport.OpenAPIMediaType.JSON);
        Paths paths = openAPI.getPaths();
        String ref = paths.getPathItem("/user")
                .getPOST()
                .getRequestBody()
                .getContent()
                .getMediaType("application/json")
                .getSchema()
                .getRef();

                assertThat("ref value", ref, is(equalTo("#/components/schemas/User")));
    }

    @Test
    public void testParserUsingJSON() throws IOException {
        OpenAPI openAPI = parse(helper,"/petstore.json", OpenAPISupport.OpenAPIMediaType.JSON);
        assertThat(openAPI.getOpenapi(), is("3.0.0"));
// TODO - uncomment the following once full $ref support is in place
//        assertThat(openAPI.getPaths().getPathItem("/pet").getPUT().getRequestBody().getDescription(),
//                containsString("needs to be added to the store"));
    }

    static OpenAPI parse(SnakeYAMLParserHelper<ExpandedTypeDescription> helper, String path,
            OpenAPISupport.OpenAPIMediaType mediaType) throws IOException {
        try (InputStream is = ParserTest.class.getResourceAsStream(path)) {
            return OpenAPIParser.parse(helper.types(), is, mediaType);
        }
    }
}