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

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.servers.Server;
import org.eclipse.microprofile.openapi.models.servers.ServerVariable;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static io.helidon.microprofile.openapi.TestUtil.resource;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link OpenApiParser}.
 */
class OpenApiParserTest {

    @Test
    void testParserUsingYAML() {
        OpenAPI openAPI = parse("/petstore.yaml");
        assertThat(openAPI.getOpenapi(), is("3.0.0"));
        assertThat(openAPI.getPaths().getPathItem("/pets").getGET().getParameters().get(0).getIn(),
                   is(Parameter.In.QUERY));
    }

    @Test
    void testExtensions() {
        OpenAPI openAPI = parse("/openapi-greeting.yml");
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
    void testYamlRef() {
        OpenAPI openAPI = parse("/petstore.yaml");
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
    void testJsonRef() {
        OpenAPI openAPI = parse("/petstore.json");
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
    void testParserUsingJSON() {
        OpenAPI openAPI = parse("/petstore.json");
        assertThat(openAPI.getOpenapi(), is("3.0.0"));

        // TODO - uncomment the following once full $ref support is in place
        //        assertThat(openAPI.getPaths().getPathItem("/pet").getPUT().getRequestBody().getDescription(),
        //                containsString("needs to be added to the store"));
    }

    @Test
    @SuppressWarnings("HttpUrlsUsage")
    void testComplicatedPetstoreDocument() {
        OpenAPI openAPI = parse("/petstore-with-fake-endpoints-models.yaml");
        assertThat(openAPI.getOpenapi(), is("3.0.0"));
        assertThat("Default for server variable 'port'",
                   openAPI.getPaths()
                           .getPathItem("/pet")
                           .getServers()
                           .stream()
                           .filter(server -> server.getUrl().equals("http://{server}.swagger.io:{port}/v2"))
                           .map(Server::getVariables)
                           .map(map -> map.get("server"))
                           .map(ServerVariable::getDefaultValue)
                           .findFirst(),
                   optionalValue(is("petstore")));
    }

    private static OpenAPI parse(String path) {
        String document = resource(path);
        return OpenApiParser.parse(OpenApiHelper.types(), OpenAPI.class, new StringReader(document));
    }
}
