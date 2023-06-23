/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.openapi;

import java.util.stream.Stream;

import io.helidon.common.testing.junit5.OptionalMatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;

class TestStaticContent {

    record StaticContentTestValue(String path,
                                  OpenApiFeature.OpenAPIMediaType openAPIMediaType,
                                  String expectedContent) {}


    @ParameterizedTest
    @MethodSource("staticContentTestValues")
    void testStaticContent(StaticContentTestValue testValue) {
        OpenApiFeature feature = StaticFileOnlyOpenApiFeatureImpl.builder()
                .staticFile(testValue.path)
                .build();

        assertThat("YAML static content",
                   feature.staticContent(),
                   OptionalMatcher.optionalPresent());

        assertThat("YAML static content value",
                   feature.staticContent().get().content(),
                   containsString(testValue.expectedContent));

        assertThat("Content", feature.openApiContent(testValue.openAPIMediaType),
                   containsString(testValue.expectedContent));
    }

    static Stream<StaticContentTestValue> staticContentTestValues() {
        return Stream.of(new StaticContentTestValue("openapi-greeting.yml",
                                                    OpenApiFeature.OpenAPIMediaType.YAML,
                                                    "Sets the greeting prefix"),
                         new StaticContentTestValue("petstore.json",
                                                    OpenApiFeature.OpenAPIMediaType.JSON,
                                                    "This is a sample server Petstore server."),
                         new StaticContentTestValue("petstore.yaml",
                                                    OpenApiFeature.OpenAPIMediaType.YAML,
                                                    "A link to the next page of responses"));
    }
}
