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

package io.helidon.builder.codegen;

import java.util.List;

import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.DEPRECATED;
import static io.helidon.builder.codegen.Types.OPTION_DEPRECATED;
import static java.util.function.Predicate.not;

/**
 * Deprecation information - combined from {@link java.lang.Deprecated} and {@code Option.Deprecated} annotations.
 * all options are nullable (except for the booleans of course)
 *
 * @param deprecated        whether this option is deprecated
 * @param forRemoval        whether the deprecated method is planned to be removed in next major version
 * @param since             since if defined (version that introduced this deprecation)
 * @param alternativeOption alternative option to be used instead of the deprecated one
 * @param description       description (if no alternative option is defined)
 */
record DeprecationData(boolean deprecated,
                       boolean forRemoval,
                       String since,
                       String alternativeOption,
                       List<String> description) {
    static DeprecationData create(TypedElementInfo element, Javadoc javadoc) {
        boolean deprecated = false;
        boolean forRemoval = false;
        String since = null;
        String alternative = null;
        List<String> description = javadoc.deprecation();

        if (element.hasAnnotation(DEPRECATED)) {
            deprecated = true;
            Annotation annotation = element.annotation(DEPRECATED);
            forRemoval = annotation.booleanValue("forRemoval").orElse(false);
            since = annotation.stringValue("since").filter(not(String::isBlank)).orElse(null);
        }

        if (element.hasAnnotation(OPTION_DEPRECATED)) {
            deprecated = true;
            // alternative overrides description, and it is a required property
            alternative = element.annotation(OPTION_DEPRECATED)
                    .value()
                    .orElse(null);
            description = null;
        }

        return new DeprecationData(deprecated, forRemoval, since, alternative, description);
    }
}
