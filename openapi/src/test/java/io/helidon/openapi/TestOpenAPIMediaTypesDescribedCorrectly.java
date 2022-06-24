/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

/**
 * Makes sure that the file types which the OpenAPIMediaType enums report are correctly captured in a hard-coded
 * constant used in an annotation.
 */
class TestOpenAPIMediaTypesDescribedCorrectly {

    private static final Set<String> FILE_TYPES_DEFINED_BY_ENUM = Arrays.stream(OpenAPISupport.OpenAPIMediaType.values())
            .flatMap(mediaType -> mediaType.matchingTypes().stream())
            .collect(Collectors.toSet());

    private static final Set<String> FILE_TYPES_DESCRIBED = Arrays.stream(
                    OpenAPISupport.OpenAPIMediaType.TYPE_LIST.split("\\|"))
            .collect(Collectors.toSet());

    @Test
    void makeSureAllTypesAreDescribed() {
        Set<String> reportedNotDescribed = new HashSet<>(FILE_TYPES_DEFINED_BY_ENUM);
        reportedNotDescribed.removeAll(FILE_TYPES_DESCRIBED);
        assertThat("File types defined by the enum values but not described in the hard-coded constant",
                   reportedNotDescribed,
                   empty());
    }

    @Test
    void makeSureOnlyTypesAreDescribed() {
        Set<String> describedTypesNotReported = new HashSet<>(FILE_TYPES_DESCRIBED);
        describedTypesNotReported.removeAll(FILE_TYPES_DEFINED_BY_ENUM);
        assertThat("File types described in the hard-coded constant but not reported by any enum value",
                   describedTypesNotReported,
                   empty());
    }
}
