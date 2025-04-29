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

package io.helidon.common.features.metadata;

/**
 * Supported Feature states.
 */
public enum FeatureStatus {
    /**
     * Incubating features may be changed including backward incompatible changes in between any version of Helidon.
     * Incubating features are NOT production ready features, and may be removed at discretion of Helidon team.
     */
    INCUBATING,
    /**
     * Preview feature may be changed including backward incompatible changes in between minor versions of Helidon.
     * Preview features are considered production ready features.
     */
    PREVIEW,
    /**
     * Production ready feature honoring semantic versioning.
     */
    PRODUCTION,
    /**
     * Deprecated feature, will be removed in a future major version of Helidon (if previous state was
     * {@link #PRODUCTION} or {@link #PREVIEW}, or next minor version of Helidon (if previous state was
     * {@link #INCUBATING}).
     */
    DEPRECATED
}
