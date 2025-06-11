/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.codegen.query;

/**
 * Criteria condition operator parameters.
 */
public interface CriteriaParameters {
    /**
     * Parameters count.
     *
     * @return number of parameters
     */
    int count();

    /**
     * Get parameter with provided {@code index}.
     * Values of {@code index} start from {@code 0}.
     *
     * @param index parameter index
     * @return the parameter with provided {@code index}
     */
    CharSequence get(int index);

}
