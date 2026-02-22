/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotations to manage (and enforce) lifecycle and stability of Helidon APIs.
 * <p>
 * All public types that do not have any {@code Api} annotation are considered production features, and will follow
 * full deprecation process if we decide to change/remove them.
 * <p>
 * These features will be backward compatible within the same major version of Helidon (except for required bugfixes,
 * where backward compatibility cannot be maintained to fix the issue).
 * <p>
 * The possible next lifecycle phases (of GA features):
 * <ul>
 *     <li>{@link java.lang.Deprecated} - a feature may be marked deprecated, and will be removed in a future major
 *              version of Helidon</li>
 * </ul>
 */
public final class Api {
    /**
     * Suppression constant to ignore any use of Helidon APIs that are not production.
     */
    public static final String SUPPRESS_ALL = "helidon:api";
    /**
     * Suppression constant to ignore any use of Helidon preview APIs.
     */
    public static final String SUPPRESS_PREVIEW = "helidon:api:preview";
    /**
     * Suppression constant to ignore any use of Helidon incubating APIs.
     */
    public static final String SUPPRESS_INCUBATING = "helidon:api:incubating";
    /**
     * Suppression constant to ignore any use of Helidon private APIs.
     */
    public static final String SUPPRESS_PRIVATE = "helidon:api:private";

    private Api() {
    }

    /**
     * A preview API (class, method, or constructor).
     * <p>
     * Preview API is considered production ready, though under development.
     * We may change this API, including backward incompatible changes between minor releases, but we will not remove
     * this API without proper deprecation process.
     * <p>
     * Mutually exclusive with {@link io.helidon.common.Api.Incubating} and {@link io.helidon.common.Api.Private}.
     * <p>
     * The possible next lifecycle phases:
     * <ul>
     *     <li>"GA" - production feature once we finalize the APIs</li>
     *     <li>{@link java.lang.Deprecated} - the feature will be removed in next major version of Helidon</li>
     * </ul>
     */
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Preview {
    }

    /**
     * An incubating API (class, method, or constructor).
     * <p>
     * Incubating API is considered as investigation of possibilities, and may be changed including backward incompatible
     * changes or even removal in between any version of Helidon.
     * Incubating APIs are NOT production ready, and may be removed at the discretion of Helidon team.
     * <p>
     * We welcome feedback for incubating features. These are included to be played around with,
     * and to be improved.
     * <p>
     * Mutually exclusive with {@link io.helidon.common.Api.Preview} and {@link io.helidon.common.Api.Private}.
     * <p>
     * The possible next lifecycle phases:
     * <ul>
     *     <li>"GA" - may become a production feature</li>
     *     <li>{@link io.helidon.common.Api.Preview} - may become a preview feature, when we decide it will stay in Helidon</li>
     *     <li>Removal - we may remove the feature if it does not bring the expected benefits</li>
     * </ul>
     */
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Incubating {
    }

    /**
     * A private API.
     * <p>
     * Private APIs are only intended for Helidon itself, and may not be used outside Helidon code base.
     * These APIs may change as we see fit, including removal between any versions of Helidon.
     * <p>
     * Mutually exclusive with {@link io.helidon.common.Api.Preview} and {@link io.helidon.common.Api.Incubating}.
     */
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Private {
    }
}
