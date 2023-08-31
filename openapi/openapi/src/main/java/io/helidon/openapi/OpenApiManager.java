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

import io.helidon.common.config.NamedService;

/**
 * OpenApi manager.
 *
 * @param <T> model type
 */
public interface OpenApiManager<T> extends NamedService {

    /**
     * Load the model.
     *
     * @param content initial static content, may be empty
     * @return in-memory model
     */
    T load(String content);

    /**
     * Format the model.
     *
     * @param model  model
     * @param format desired format
     * @return formatted content
     */
    String format(T model, OpenApiFormat format);
}
