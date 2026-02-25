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
 * Details on stability annotations:
 * <ul>
 *     <li>{@link io.helidon.common.Api.Internal} - internal API, do not use from outside Helidon</li>
 *     <li>{@link io.helidon.common.Api.Incubating} - used for new APIs where we want to test approaches, and gather
 *      feedback from early adopters; these APIs may be changed, or even removed in any version of Helidon</li>
 *      <li>{@link io.helidon.common.Api.Preview} - used for APIs that we will include in Helidon, but still expect
 *      API changes based on feedback from users; these APIs may be changed in any version of Helidon</li>
 *      <li>{@link io.helidon.common.Api.Stable} - APIs that are intended for every customer, and that will be backward
 *      compatible within a major version of Helidon</li>
 *      <li>{@link io.helidon.common.Api.Deprecated} - APIs that will be removed, probably in the next major release
 *      of Helidon</li>
 * </ul>
 *
 * Possible transitions:
 * <ul>
 *     <li>internal - we can transition to any state we see fit, including removal, at any time</li>
 *     <li>incubating - can transition to preview, stable, or can be removed in any version of Helidon</li>
 *     <li>preview - can transition to stable or deprecated in any version of Helidon</li>
 *     <li>stable - can transition to deprecated in any version of Helidon</li>
 *     <li>deprecated - stable, or can be removed in next major version of Helidon</li>
 * </ul>
 *
 * In case you decide to use on of the APIs that are not stable (i.e. to provide early feedback to us, or with
 * the knowledge you may need to change sources when using the next minor/dot/patch release of Helidon), you can
 * add {@link java.lang.SuppressWarnings} with the constants provided in this class, such as {@link #SUPPRESS_PREVIEW},
 * or you can suppress all warnings/errors with {@link #SUPPRESS_ALL}.
 */
@Api.Internal
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
     * Suppression constant to ignore any use of Helidon internal APIs.
     */
    public static final String SUPPRESS_INTERNAL = "helidon:api:internal";
    /**
     * Suppression constant to ignore any use of Helidon deprecated APIs.
     * You can also use {@code deprecated} as if the annotation was {@link java.lang.Deprecated}.
     */
    public static final String SUPPRESS_DEPRECATED = "helidon:api:deprecated";

    private Api() {
    }

    /**
     * API may add this annotation to mark the first version that contains it.
     * It is recommended to include it with {@link io.helidon.common.Api.Deprecated} and {@link io.helidon.common.Api.Stable}.
     */
    public @interface Since {
        /**
         * Version of Helidon this API was introduced in.
         *
         * @return version
         */
        String value();
    }

    /**
     * A preview API (class, method, or constructor).
     * <p>
     * Preview API is considered production ready, though under development.
     * We may change this API, including backward incompatible changes between minor releases, but we will not remove
     * this API without proper deprecation process.
     * <p>
     * Mutually exclusive with other API stability annotations.
     * <p>
     * Possible next lifecycle phases:
     * <ul>
     *     <li>{@link io.helidon.common.Api.Stable} - production feature once we finalize the APIs</li>
     *     <li>{@link io.helidon.common.Api.Deprecated} - the feature will be removed in next major version of Helidon</li>
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
     * Mutually exclusive with other API stability annotations.
     * <p>
     * Possible next lifecycle phases:
     * <ul>
     *     <li>{@link io.helidon.common.Api.Stable} - may become a production feature</li>
     *     <li>{@link io.helidon.common.Api.Preview} - may become a preview feature, when we decide it will stay in Helidon</li>
     *     <li>{@link io.helidon.common.Api.Deprecated} - we may decide to deprecate rather than directly remove</li>
     *     <li>Removal - we may remove the feature if it does not bring the expected benefits</li>
     * </ul>
     */
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Incubating {
    }

    /**
     * An internal API.
     * <p>
     * Internal APIs are only intended for Helidon itself, and may not be used outside Helidon code base.
     * These APIs may change as we see fit, including removal between any versions of Helidon.
     * <p>
     * Mutually exclusive with other API stability annotations.
     */
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Internal {
    }

    /**
     * A deprecated API.
     * <p>
     * Deprecated APIs will be removed in the next major version of Helidon (or later).
     * <p>
     * Mutually exclusive with other API stability annotations.
     */
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Deprecated {
    }

    /**
     * Stable API.
     * <p>
     * This API will be backward compatible within this major version of Helidon.
     * Changes to stable APIs will follow deprecation with alternative available.
     * <p>
     * Mutually exclusive with other API stability annotations.
     * <p>
     * Example: a stable API in Helidon 4.4.0. It may be deprecated in 4.5.0, and then removed in 5.0.0.
     * This API will be available in any minor version of Helidon 4.
     *
     * <p>
     * Possible next lifecycle phases:
     * <ul>
     *     <li>{@link io.helidon.common.Api.Deprecated} - we may decide to deprecate rather than directly remove</li>
     * </ul>
     */
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Stable {
    }
}
