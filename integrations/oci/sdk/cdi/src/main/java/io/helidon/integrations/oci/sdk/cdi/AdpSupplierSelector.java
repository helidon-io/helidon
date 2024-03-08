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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;

/**
 * A {@linkplain FunctionalInterface functional interface} that can {@linkplain #select(Object) select} a suitable
 * {@link AdpSupplier AdpSupplier&lt;? extends BasicAuthenticationDetailsProvider&gt;} given a key (usually an
 * {@linkplain AdpStrategyDescriptors OCI authentication strategy descriptor}).
 *
 * @param <K> the key type
 *
 * @param <T> a subtype of {@link BasicAuthenticationDetailsProvider}
 *
 * @see #select(Object)
 *
 * @see AdpStrategyDescriptors
 */
@FunctionalInterface
interface AdpSupplierSelector<K, T extends BasicAuthenticationDetailsProvider> {

    /**
     * Given a key, returns a suitable {@link AdpSupplier}.
     *
     * <p>Implementations of this method must return a determinate value for a given argument.</p>
     *
     * <p>Implementations of this method must be safe for concurrent use by multiple threads.</p>
     *
     * @param k the key; must not be {@code null}; often an {@linkplain AdpStrategyDescriptors OCI authentication
     * strategy descriptor}
     *
     * @return an {@link AdpSupplier}; never {@code null}; possibly an instance of {@link EmptyAdpSupplier} or similar
     *
     * @exception NullPointerException if {@code k} is {@code null}
     *
     * @see AdpSupplier
     *
     * @see AdpStrategyDescriptors
     */
    AdpSupplier<? extends T> select(K k);

    /**
     * A convenience default method that returns a {@link List} of suitable {@link AdpSupplier}s given an {@link
     * Iterable} of keys.
     *
     * <p>The default implementation of this method calls the {@link #select(Object)} method.</p>
     *
     * <p>The {@link List} that is returned is and must be unmodifiable and unchanging.</p>
     *
     * <p>The default implementation does, and overrides must, return a determinate value for a given sequence of keys
     * represented by the supplied {@link Iterable}.</p>
     *
     * @param i an {@link Iterable} of keys; must not be {@code null}
     *
     * @return an unmodifable and unchanging {@link List} of suitable {@link AdpSupplier}s; never {@code null}; no
     * element of the list will be {@code null}
     *
     * @exception NullPointerException if {@code i} is {@code null}
     *
     * @see #select(Object)
     *
     * @see AdpSupplier
     *
     * @see AdpStrategyDescriptors
     */
    default List<AdpSupplier<? extends T>> select(Iterable<? extends K> i) {
        ArrayList<AdpSupplier<? extends T>> list = new ArrayList<>(9); // 9 == arbitrary, small
        i.forEach(k -> list.add(select(k)));
        list.trimToSize();
        return Collections.unmodifiableList(list);
    }

}
