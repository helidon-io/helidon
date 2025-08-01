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
package io.helidon.discovery;

import java.net.URI;
import java.util.Map;

/**
 * A representation of a <dfn>discovered URI</dfn>, often supplied by invocations of an implementation of
 * the {@link Discovery#uris(String, URI)} method.
 *
 * <p>A {@link DiscoveredUri} implementation must be immutable in all possible respects.</p>
 *
 * <h2>Equality</h2>
 *
 * <p>Two {@link DiscoveredUri} implementations are considered <dfn>equal</dfn> if and only if:</p>
 *
 * <ol>
 *
 * <li>The return values of invocations of their {@link Object#getClass() getClass()} methods are {@linkplain Class#equals(Object) equal}, and</li>
 *
 * <li>The return values of invocations of their {@link #uri() uri()} methods are {@linkplain URI#equals(Object) equal}</li>
 *
 * </ol>
 *
 * @see #uri()
 *
 * @see #equals(Object)
 *
 * @see Discovery#uris(String, URI)
 */
public interface DiscoveredUri {

    /**
     * Returns {@code true} if and only if the supplied {@link Object} is <dfn>equal to</dfn> this {@link
     * DiscoveredUri}.
     *
     * <p>For implementations of this method to return {@code true}, the supplied {@link Object}:</p>
     *
     * <ul>
     *
     * <li>Must {@linkplain Object#getClass() have a <code>Class</code>} that is {@linkplain Class#equals(Object) equal
     * to} this {@link DiscoveredUri}'s {@link Object#getClass() Class}, and</li>
     *
     * <li>Must {@linkplain #uri() have a <code>URI</code>} {@linkplain URI#equals(Object) equal to} this {@link
     * DiscoveredUri}'s {@link #uri() URI}</li>
     *
     * </ul>
     *
     * <p>Implementations of this method must return determinate values.</p>
     *
     * <p><strong>Note:</strong> This specification notably and deliberately excludes {@linkplain #metadata() metadata}
     * from equality calculations.</p>
     *
     * @param other an {@link Object} to test; may be {@code null} in which case {@code false} must be returned
     *
     * @return {@code true} if and only if the supplied {@link Object} is <dfn>equal to</dfn> this {@link
     * DiscoveredUri}
     *
     * @see #hashCode()
     */
    @Override // Object
    boolean equals(Object other);

    /**
     * Returns a determinate hashcode for this {@link DiscoveredUri}.
     *
     * <p>Implementations of this method must compute a hashcode based only on the return value of an invocation of the
     * {@link #uri()} method.</p>
     *
     * @return a determinate hashcode for this {@link DiscoveredUri}
     *
     * @see #equals(Object)
     */
    @Override // Object
    int hashCode();

    /**
     * Returns a determinate, immutable {@link Map} of metadata further describing this {@link DiscoveredUri}
     * implementation.
     *
     * <p>Keys in {@link Map}s returned by invocations of implementations of this method that begin with the prefix
     * {@code io.helidon.discovery.} are reserved for internal use by implementations.</p>
     *
     * <p>The default implementation returns the return value of an invocation of the {@link Map#of()} method,
     * indicating that by default there is no metadata associated with a {@link DiscoveredUri}. Overrides are expected
     * and encouraged.</p>
     *
     * <p><strong>Note:</strong> The return value of invocations of implementations of this method must be excluded from
     * {@linkplain #hashCode() hashcode} and {@linkplain #equals(Object) equality} calculations.</p>
     *
     * @return a non-{@code null}, immutable {@link Map} of metadata
     *
     * @see #equals(Object)
     *
     * @see #hashCode()
     */
    default Map<String, String> metadata() {
        return Map.of();
    }

    /**
     * Returns the non-{@code null}, determinate {@link URI} component of this {@link DiscoveredUri} that it
     * fundamentally represents.
     *
     * @return a non-{@code null}, determinate {@link URI} that this {@link DiscoveredUri} fundamentally represents
     */
    URI uri();

}
