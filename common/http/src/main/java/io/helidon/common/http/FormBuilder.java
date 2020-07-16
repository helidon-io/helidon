/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.common.http;

import io.helidon.common.Builder;

/**
 * Form builder interface.
 */
public interface FormBuilder<B, T> extends Builder<T> {

    /**
     * Add a new values to specific content disposition name.
     *
     * @param name param name
     * @param values param values
     * @return updated builder instance
     */
    B add(String name, String... values);

}
