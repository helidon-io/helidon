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
 * Annotations used to describe and enforce Helidon API stability contracts.
 * <p>
 * Public top-level APIs in Helidon production modules are expected to declare exactly one stability annotation:
 * <ul>
 *     <li>{@link io.helidon.common.Api.Internal} - Helidon implementation detail, not for external use</li>
 *     <li>{@link io.helidon.common.Api.Incubating} - exploratory API that may change or be removed in any release</li>
 *     <li>{@link io.helidon.common.Api.Preview} - supported but still evolving API that may change between minor
 *     releases</li>
 *     <li>{@link io.helidon.common.Api.Stable} - supported API that is backward compatible within the current major
 *     version</li>
 * </ul>
 * Methods, constructors, and nested types may also declare their own stability annotations.
 * The element's stability annotation must be lower than the one of the declaring type (or the same), using the order
 * {@link io.helidon.common.Api.Stable} &gt; {@link io.helidon.common.Api.Preview}
 * &gt; {@link io.helidon.common.Api.Incubating} &gt; {@link io.helidon.common.Api.Internal}.
 * <p>
 * Deprecation is separate from stability. Use {@link java.lang.Deprecated} together with the relevant stability
 * annotation to indicate planned removal.
 * <p>
 * Consumers can suppress API-stability diagnostics with {@link java.lang.SuppressWarnings}, using the constants
 * provided here for stability annotations, {@code deprecation} for deprecated APIs, or {@link #SUPPRESS_ALL} to
 * suppress all Helidon API-stability diagnostics.
 */
@Api.Stable
// we expect people to use the constants defined here
public final class Api {
    /**
     * Suppression constant to ignore any diagnostic reported by the Helidon API-stability processor.
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

    private Api() {
    }

    /**
     * API may add this annotation to mark the first Helidon version that contains the current contract.
     * It is especially useful together with {@link io.helidon.common.Api.Stable}.
     */
    @Api.Stable
    public @interface Since {
        /**
         * Version of Helidon this API was introduced in.
         *
         * @return version
         */
        String value();
    }

    /**
     * A preview API (type, method, or constructor).
     * <p>
     * Preview APIs are supported and intended for external use, but they are still evolving. They may change,
     * including backward incompatible changes, between minor releases. Preview APIs are not removed without
     * deprecation.
     * <p>
     * Mutually exclusive with other stability annotations. May be combined with {@link java.lang.Deprecated}.
     */
    @Api.Stable
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Preview {
    }

    /**
     * An incubating API (type, method, or constructor).
     * <p>
     * Incubating APIs are early exploratory APIs. They are not production ready, they may change incompatibly, and
     * they may be removed in any release.
     * <p>
     * We welcome feedback for incubating features.
     * <p>
     * Mutually exclusive with other stability annotations. May be combined with {@link java.lang.Deprecated}.
     */
    @Api.Stable
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Incubating {
    }

    /**
     * An internal API (type, method, or constructor).
     * <p>
     * Internal APIs are only intended for Helidon itself, and they may change or disappear at any time.
     * <p>
     * Mutually exclusive with other stability annotations. May be combined with {@link java.lang.Deprecated}.
     */
    @Api.Stable
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Internal {
    }

    /**
     * Stable API.
     * <p>
     * Stable APIs are backward compatible within the current major version of Helidon. Removal or incompatible changes
     * follow deprecation and a later major-version transition.
     * <p>
     * Mutually exclusive with other stability annotations. May be combined with {@link java.lang.Deprecated}.
     */
    @Api.Stable
    @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Stable {
    }
}
