/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen.spi;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

/**
 * Service provider interface to provide customization of generated annotation.
 */
public interface GeneratedAnnotationProvider {
    /**
     * Create a generated annotation.
     *
     * @param generator type of the generator (annotation processor)
     * @param trigger type of the class that caused this type to be generated
     * @param generatedType type that is going to be generated
     * @param versionId version of the generator
     * @param comments additional comments, never use null (use empty string so they do not appear in annotation)
     * @return a new annotation to add to the generated type
     */
    Annotation create(TypeName generator,
                      TypeName trigger,
                      TypeName generatedType,
                      String versionId,
                      String comments);
}
