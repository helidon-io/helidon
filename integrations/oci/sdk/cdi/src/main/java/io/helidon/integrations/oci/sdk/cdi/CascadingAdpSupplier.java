/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.sdk.cdi;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Stream.iterate;

/**
 * An {@link AdpSupplier} that tries a series of other {@link AdpSupplier}s until a suitable one is found.
 *
 * @see #get()
 */
class CascadingAdpSupplier implements AdpSupplier<BasicAuthenticationDetailsProvider> {

    private final List<? extends AdpSupplier<? extends BasicAuthenticationDetailsProvider>> adps;

    /**
     * A convenience constructor that creates a new {@link CascadingAdpSupplier} using an {@link AdpSupplierSelector}
     * and a sequence of {@linkplain AdpStrategyDescriptors OCI authentication strategy descriptors}.
     *
     * @param <K> the type of the {@linkplain AdpStrategyDescriptors OCI authentication strategy descriptors} used by
     * the supplied {@link AdpSupplierSelector}; most commonly {@link String}
     *
     * @param s an {@link AdpSupplierSelector}; must not be {@code null}
     *
     * @param i an {@link Iterable} of strategy descriptors; must not be {@code null}
     *
     * @exception NullPointerException if either argument is {@code null}
     *
     * @see #CascadingAdpSupplier(Iterable)
     *
     * @see AdpSupplierSelector#select(Iterable)
     *
     * @see AdpStrategyDescriptors
     */
    <K> CascadingAdpSupplier(AdpSupplierSelector<K, BasicAuthenticationDetailsProvider> s,
                             Iterable<? extends K> i) {
        this(s.select(i));
    }

    /**
     * Creates a new {@link CascadingAdpSupplier}.
     *
     * @param adps an {@link Iterable} of {@link BasicAuthenticationDetailsProvider}s; must not be {@code null}
     *
     * @exception NullPointerException if {@code adps} is {@code null}
     */
    CascadingAdpSupplier(Iterable<? extends AdpSupplier<? extends BasicAuthenticationDetailsProvider>> adps) {
        super();
        this.adps = list(adps);
    }

    /**
     * Calls the {@link AdpSupplier#get()} method on each of the {@linkplain #CascadingAdpSupplier(Iterable)
     * <code>AdpSupplier</code>s supplied at construction time}, in order, until a non-{@linkplain Optional#isEmpty()
     * empty} {@link Optional} {@linkplain Optional#get() housing} a {@link BasicAuthenticationDetailsProvider} is
     * found, and returns it.
     *
     * <p>If no non-{@linkplain Optional#isEmpty() empty} {@link Optional} can be found, then the result of invoking
     * {@link Optional#empty()} is returned.</p>
     *
     * @return an {@link Optional} {@linkplain Optional#get() housing} a {@link BasicAuthenticationDetailsProvider}, or
     * an {@linkplain Optional#isEmpty() empty} {@link Optional}; never {@code null}
     *
     * @see #CascadingAdpSupplier(Iterable)
     */
    @Override
    @SuppressWarnings("unchecked")
    public final Optional<BasicAuthenticationDetailsProvider> get() {
        return (Optional<BasicAuthenticationDetailsProvider>) this.adps.stream()
            .map(AdpSupplier::get)
            .dropWhile(Optional::isEmpty)
            .findFirst()
            .orElseGet(Optional::empty);
    }

    private static <T> List<T> list(Iterable<T> i) {
        return i instanceof Collection<T> c
            ? List.copyOf(c)
            : iterate(i.iterator(), Iterator::hasNext, identity()).map(Iterator::next).toList();
    }

}
