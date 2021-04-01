/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault;

import java.util.List;

/**
 * All engines provide an implementation of this interface with specific methods for these engines.
 *
 * @see io.helidon.integrations.vault.Engine
 */
public interface Secrets {
    /**
     * List available secrets.
     * This method will return an empty list if no data found.
     *
     * @return secrets available
     */
    default List<String> list() {
        return list(ListSecrets.Request.create())
                .entity()
                .map(ListSecrets.Response::paths)
                .orElseGet(List::of);
    }

    /**
     * List available secrets on a path. To get root list, use empty string for path.
     * This method will return an empty list if no data found.
     *
     * @param path path to find secrets in
     * @return secrets available
     */
    default List<String> list(String path) {
        return list(ListSecrets.Request.create(path))
                .entity()
                .map(ListSecrets.Response::paths)
                .orElseGet(List::of);
    }

    /**
     * List available secrets.
     *
     * @param request request
     * @return future with response
     */
    VaultOptionalResponse<ListSecrets.Response> list(ListSecrets.Request request);
}
