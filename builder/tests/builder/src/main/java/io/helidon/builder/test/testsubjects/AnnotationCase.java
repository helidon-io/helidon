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

package io.helidon.builder.test.testsubjects;

/**
 * Demonstrates how an annotation can be extended, and then used as the basis for a builder.
 *
 * @see AnnotationCaseExt
 */
public @interface AnnotationCase {

    /**
     * Also demonstrates how default values are handled on the generated builder.
     *
     * @return "hello"
     * @see DefaultAnnotationCaseExt (generated code)
     */
    String value() default "hello";

    /**
     * Demonstrates how string array defaults work on the generated builder.
     *
     * @return "a", "b", "c"
     * @see DefaultAnnotationCaseExt (generated code)
     */
    String[] strArr() default {"a", "b", "c"};

}
