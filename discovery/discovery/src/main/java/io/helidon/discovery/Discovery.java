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
import java.util.SequencedSet;

/**
 * An interface whose implementations <dfn>discover</dfn> possibly transient {@link DiscoveredUri} instances suitable,
 * at the moment of discovery, for a given <dfn>discovery name</dfn>.
 *
 * <p>To discover URIs, a user of an instance of this interface invokes its {@link #uris(String, URI)} method, supplying
 * a non-{@code null} discovery name and a non-{@code null} {@link URI} to use, effectively, as a default value. The
 * method returns an immutable, non-determinate <dfn>discovered set</dfn> of {@link DiscoveredUri} instances suitable
 * for the invocation. If the discovered set would otherwise be {@linkplain SequencedSet#isEmpty() empty}, it will
 * instead contain at least a {@link DiscoveredUri} whose {@link DiscoveredUri#uri() uri()} method returns the default
 * value supplied to the invocation of the {@link #uris(String, URI)} method that produced it. More characteristics and
 * requirements of discovery names, discovered sets, and discovered URIs may be found below.</p>
 *
 * <h2>Acquisition</h2>
 *
 * <p>To acquire a {@link Discovery} instance, <a
 * href="https://helidon.io/docs/latest/se/injection#_injection_points">inject</a> an instance of {@link Discovery},
 * using the {@link io.helidon.service.registry.Service.Inject} annotation. Alternatively, and equivalently, use the
 * {@code static} {@link io.helidon.service.registry.Services#get(Class)} method, supplying {@code Discovery.class} as
 * its sole argument.</p>
 *
 * <h2>Discovery Names</h2>
 *
 * <p>A discovery name is a name for which a {@link Discovery} implementation is capable of furnishing, at a moment in
 * time, zero or more URIs, represented by {@link DiscoveredUri} instances.</p>
 *
 * <p>A {@link Discovery} implementation <em>must</em> be prepared to handle names that do not identify any URIs at the
 * moment of invocation, or will never identify any URIs. In either case, a {@link Discovery} implementation
 * <em>must</em> return a discovered set that consists solely of a {@link DiscoveredUri} that represents the default
 * value supplied to the invocation of the {@link #uris(String, URI)} method that produced it. Implementations
 * <em>should</em> return a {@link DiscoveredUri} in the {@linkplain SequencedSet#getLast() tail position} of the
 * discovered set in all cases.</p>
 *
 * <p>For maximum portability, a discovery name <em>should</em> comprise only <a
 * href="https://www.rfc-editor.org/rfc/rfc1123#page-13">legal hostname characters</a> (to permit DNS-based or DNS-using
 * implementations).</p>
 *
 * <p>Discovery implementations <em>should</em> treat discovery names case-insensitively.</p>
 *
 * <p>Since a discovery name is an application-level concern, not a component-level comncern, it is recommended that
 * <dfn>discovery-using components</dfn>&mdash;which may be included in disparate applications with disparate naming
 * requirements and {@link Discovery} implementations&mdash;allow an application developer to configure, or otherwise
 * specify at deployment or runtime, any relevant discovery names that they use to account for potential differences in
 * application and component namespaces.</p>
 *
 * <h2>Discovered Sets</h2>
 *
 * <p>Invocations of the {@link #uris(String, URI)} method return immutable {@link SequencedSet}s of {@link
 * DiscoveredUri}s. These are <dfn>discovered sets</dfn>. Discovered sets have at least the following
 * characteristics:</p>
 *
 * <ol>
 *
 * <li>They are <dfn>immutable</dfn>, following the <a
 * href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collection.html#unmodview">definition of
 * immutability</a> in the Java Collections Framework. They are thus by definition also safe for concurrent use by
 * multiple threads.</li>
 *
 * <li>They are <dfn>stable</dfn>, in keeping with the contract of an immutable {@link SequencedSet}. Note however that
 * the ordering that is preserved is implementation-dependent. Implementations <em>should</em>, as a general rule, add a
 * {@link DiscoveredUri} representing the supplied {@code defaultValue} as the {@linkplain SequencedSet#addLast(Object)
 * last element} of the discovered set, and <em>must</em> do so if the set would otherwise be {@linkplain
 * SequencedSet#isEmpty() empty}.</li>
 *
 * <li>They are <dfn>non-determinate</dfn>. That is, it is <em>not</em> guaranteed that two invocations of the {@link
 * #uris(String, URI)} method will return {@linkplain SequencedSet#equals(Object) equal values}. This is because a
 * discovered set represents a moment-suitable collection of potentially available distinct resources.</li>
 *
 * <li>Similarly, discovered sets may or may not represent cached information. Any caching, or lack of it, is a property
 * of the {@link Discovery} implementation, not this specification.</li>
 *
 * <li>They are never <dfn>empty</dfn>, since a discovered set will always contain at least one {@link DiscoveredUri}
 * {@linkplain DiscoveredUri#uri() representing} the {@code defaultValue} argument supplied during the invocation of the
 * {@link #uris(String, URI)} method.</li>
 *
 * <li>They and their contents are not retained by the {@link Discovery} implementation and are free for use by the
 * caller for any purpose.</li>
 *
 * </ol>
 *
 * <h2>Discovered URIs</h2>
 *
 * <p>Discovered sets contain <dfn>discovered URIs</dfn>, represented by an instance of the {@link DiscoveredUri}
 * interface (<em>q. v.</em>). Discovered URIs have the following characteristics:</p>
 *
 * <ol>
 *
 * <li>They are immutable. They are thus by definition also safe for concurrent use by
 * multiple threads.</li>
 *
 * <li>The {@linkplain DiscoveredUri#uri() URIs they represent} are (by definition) immutable. They are thus by
 * definition also safe for concurrent use by multiple threads.</li>
 *
 * <li>They may {@linkplain DiscoveredUri#metadata() bear <dfn>metadata</dfn>}. The metadata <em>must</em> also be
 * wholly immutable and thus by definition safe for concurrent use by multiple threads.</li>
 *
 * <li>Two {@link DiscoveredUri} implementations are {@linkplain DiscoveredUri#equals(Object) equal} if and only if
 * {@linkplain Object#getClass() their <code>Class</code>}es are {@linkplain Class#equals(Object) equal} and {@linkplain
 * DiscoveredUri#uri() the <code>URI</code>s they represent} are {@linkplain URI#equals(Object) equal}. Notably and
 * deliberately, metadata borne by a {@link DiscoveredUri} implementation <em>must not</em> be factored into any
 * equality calculations, since some of it may be time-dependent, among other reasons.</li>
 *
 * </ol>
 *
 * @see #uris(String, URI)
 *
 * @see DiscoveredUri
 */
public interface Discovery extends AutoCloseable {

    /**
     * Closes any resources used by this {@link Discovery} implementation.
     *
     * <p>The default implementation of this method does nothing. Overrides are expected and encouraged, particularly by
     * implementations that contact external resources.</p>
     *
     * <p>An implementation may or may not permit other operations (such as invocations of its {@link #uris(String,
     * URI)} method) after an invocation of this method has successfully completed (an implementation of this method may
     * result in a <dfn>terminal state</dfn>). An implementation <em>should</em> document whether closing results in
     * such a terminal state.</p>
     *
     * <p>Implementations of this method must be idempotent and safe for concurrent use by multiple threads.</p>
     *
     * @see #uris(String, URI)
     *
     * @see AutoCloseable#close()
     */
    @Override // AutoCloseable
    default void close() {

    }

    /**
     * Returns an immutable, non-{@linkplain SequencedSet#isEmpty() empty}, {@link SequencedSet} (a <dfn>discovered
     * set</dfn>) of {@link DiscoveredUri} instances suitable for the supplied <dfn>discovery name</dfn>.
     *
     * <p>Because discovery may involve systems like the DNS, discovery names <em>should</em> contain only <a
     * href="https://www.rfc-editor.org/rfc/rfc1123#page-13">legal hostname characters</a>.</p>
     *
     * <p>Return values yielded by invocations of implementations of this method are not guaranteed to be determinate:
     * any two invocations of this method with {@linkplain String#equals(Object) the same discovery name} and
     * {@linkplain URI#equals(Object) default value} arguments may return non-{@linkplain SequencedSet#equals(Object)
     * equal} discovered sets.</p>
     *
     * <p>The return value of an invocation of an implementation of this method must not be {@linkplain
     * SequencedSet#isEmpty() empty}. At a minimum it must contain a single {@link DiscoveredUri} element representing
     * the supplied {@code defaultValue} argument. Normally, this element is located at the {@linkplain
     * SequencedSet#getLast() tail} of the returned {@link SequencedSet}.</p>
     *
     * <p>The semantics of the order of the returned {@link SequencedSet} are strictly speaking undefined (beyond the
     * guarantee of <dfn>stability</dfn> offered by all {@link SequencedSet} implementations), but implementations are
     * encouraged to place what they consider to be the <dfn>most suitable</dfn> {@link DiscoveredUri} at the
     * {@linkplain SequencedSet#getFirst() head} of the notional queue represented by the returned {@link
     * SequencedSet}.</p>
     *
     * <p>Any given invocation of an implementation of this method may return a cached value, or a value retrieved at
     * that moment, or a set containing only a {@linkplain DiscoveredUri representation} of the supplied {@code
     * defaultValue} {@link URI}.</p>
     *
     * <p>Implementations of this method must be idempotent and safe for concurrent use by multiple threads.</p>
     *
     * @param discoveryName a discovery name ideally comprised only of <a
     * href="https://www.rfc-editor.org/rfc/rfc1123#page-13">legal hostname characters</a>; must not be {@code null}
     *
     * @param defaultValue a {@link URI} that will be {@linkplain DiscoveredUri#uri() represented} as a {@link
     * DiscoveredUri} if any kind of error occurs that prevents a normal invocation, or if the return value of an
     * invocation of this method would otherwise be empty; must not be {@code null}; may be effectively included or
     * ignored by the implementation if the discovered set would not be empty without it
     *
     * @return a non-{@code null}, non-{@linkplain SequencedSet#isEmpty() empty}, immutable {@link SequencedSet} of
     * {@link DiscoveredUri} instances
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @exception IllegalArgumentException if any argument is unsuitable in any way
     *
     * @exception IllegalStateException if the {@link Discovery} implementation is {@linkplain #close() closed} and does
     * not support subsequent usage, as documented in its documentation of its {@link #close()} method
     *
     * @see #close()
     *
     * @see DiscoveredUri
     */
    SequencedSet<DiscoveredUri> uris(String discoveryName, URI defaultValue);

}
