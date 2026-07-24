/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.openapi;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.helidon.common.types.Annotation;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class OpenApiSourceGeneratorTest {

    @Test
    void repeatableAnnotationsIncludesContainerAndDirectValues() {
        Annotation inheritedOkResponse = response("200");
        Annotation localCreatedResponse = response("201");
        Annotation inheritedResponses = Annotation.builder()
                .typeName(OpenApiCodegenTypes.OPENAPI_RESPONSES_ANNOTATION)
                .property("value", List.of(inheritedOkResponse))
                .build();
        Set<Annotation> annotations = new LinkedHashSet<>();
        annotations.add(inheritedResponses);
        annotations.add(localCreatedResponse);

        List<Annotation> responses = OpenApiSourceGenerator.repeatableAnnotations(
                annotations,
                OpenApiCodegenTypes.OPENAPI_RESPONSES_ANNOTATION,
                OpenApiCodegenTypes.OPENAPI_RESPONSE_ANNOTATION);

        assertThat(responses.size(), is(2));
        assertThat(responses.get(0), is(inheritedOkResponse));
        assertThat(responses.get(1), is(localCreatedResponse));
    }

    private static Annotation response(String status) {
        return Annotation.builder()
                .typeName(OpenApiCodegenTypes.OPENAPI_RESPONSE_ANNOTATION)
                .property("status", status)
                .property("description", "response " + status)
                .build();
    }
}
