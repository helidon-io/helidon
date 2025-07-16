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
package io.helidon.data;

import io.helidon.builder.api.Prototype;

/**
 * Request pageable query result as page with page number and size.
 * <p>
 * {@link PageRequest} argument of the repository interface query method sets
 * parameters of the page (represented by {@link Slice} or {@link Page} interfaces)
 * to be returned by the query.
 * <p>
 * Repository interface method must always be of {@link Slice} or {@link Page} type when
 * it contains {@link PageRequest} argument.
 */
@Prototype.Blueprint
@Prototype.CustomMethods(PageRequestSupport.class)
interface PageRequestBlueprint {

    /**
     * Page number.
     * <p>
     * Page number starts from {@code 0}.
     *
     * @return page number
     */
    int page();

    /**
     * Page size.
     * <p>
     * Valid {@code size} values are:<ul>
     * <li>{@code -1} when no pagination is defined</li>
     * <li>{@code size > 0} for requested page size</li></ul>
     *
     * @return page size
     */
    int size();

}
