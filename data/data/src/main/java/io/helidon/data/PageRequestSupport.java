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

// PageRequestBlueprint custom methods
class PageRequestSupport {

    private PageRequestSupport() {
        throw new UnsupportedOperationException("No instances of PageRequestSupport are allowed");
    }

    /**
     * Create new instance of pageable query result request with no order definitions.
     *
     * @param page page number
     * @param size requested page size
     * @return pageable query result request
     */
    @Prototype.FactoryMethod
    static PageRequest create(int page, int size) {
        return PageRequest.builder()
                .page(page)
                .size(size)
                .build();
    }

    /**
     * Create new instance of pageable query result request with default size of {@code 10}
     * and no order definitions.
     *
     * @param page page number
     * @return pageable query result request
     */
    @Prototype.FactoryMethod
    static PageRequest create(int page) {
        return PageRequest.builder()
                .page(page)
                .build();
    }

    /**
     * Offset of current page in the requested collection.
     *
     * @return collection offset or {@code 0} when no pagination is defined
     */
    @Prototype.PrototypeMethod
    static int offset(PageRequest pageRequest) {
        return pageRequest.size() > 0
                ? pageRequest.page() * pageRequest.size()
                : 0;
    }

}
