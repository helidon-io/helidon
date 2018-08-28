/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

/**
 * Provider selection policy type.
 */
enum ProviderSelectionPolicyType {
    /**
     * Choose first provider from the list by default.
     * Choose provider with the name defined when explicit provider requested.
     */
    FIRST,
    /**
     * Can compose multiple providers together to form a single
     * logical provider.
     */
    COMPOSITE,
    /**
     * Explicit class for a custom {@link ProviderSelectionPolicyType}.
     */
    CLASS;

    // to simplify from config
    static ProviderSelectionPolicyType from(String value) {
        return ProviderSelectionPolicyType.valueOf(value);
    }
}
