/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import static java.util.function.Predicate.not;

/**
 * A utility class providing convenience methods and fields for working with <em>OCI authentication strategy
 * descriptors</em>.
 *
 * <p>In this documentation, "OCI" is an abbreviation of "Oracle Cloud Infrastructure". "ADP" and "Adp" are
 * abbreviations of "{@linkplain com.oracle.bmc.auth.BasicAuthenticationDetailsProvider authentication details
 * provider}".</p>
 *
 * <p>An OCI authentication strategy descriptor can serve as a kind of shorthand identifier for {@linkplain
 * AdpSupplierSelector#select(Object) selecting} a particular {@link AdpSupplier} that can ultimately supply a
 * particular {@link com.oracle.bmc.auth.BasicAuthenticationDetailsProvider BasicAuthenticationDetailsProvider}
 * implementation.</p>
 *
 * <p>While fundamentally OCI authentication strategy descriptors are simply {@link String}s with no particular
 * semantics, they are particularly useful when combined with the usage of an {@link AdpSupplierSelector}. See the
 * {@link AdpSupplierSelector#select(Object)} method for more details.</p>
 *
 * @see #strategyDescriptors(ConfigAccessor)
 *
 * @see #DEFAULT_ORDERED_ADP_STRATEGY_DESCRIPTORS
 *
 * @see AdpSupplier
 *
 * @see AdpSupplierSelector#select(Object)
 *
 * @see CascadingAdpSupplier
 */
final class AdpStrategyDescriptors {

    /**
     * A convenience field containing an ordered {@link List} of <em>OCI authentication strategy descriptors</em>, each
     * element of which normally serves as an identifier for selecting a particular {@link AdpSupplier} that can
     * ultimately supply a particular {@link com.oracle.bmc.auth.BasicAuthenticationDetailsProvider
     * BasicAuthenticationDetailsProvider} implementation.
     *
     * <p>The order of the list describes a common-sense approach to authentication that should work adequately in all
     * environments and situations, ranging from local development to Kubernetes deployments.</p>
     *
     * <p>The {@link List} contains the following {@link String}s, in the following order:</p>
     *
     * <ol>
     * <li><strong>{@code config}</strong></li>
     * <li><strong>{@code config-file}</strong></li>
     * <li><strong>{@code session-token-config-file}</strong></li>
     * <li><strong>{@code session-token-builder}</strong></li>
     * <li><strong>{@code instance-principals}</strong></li>
     * <li><strong>{@code resource-principal}</strong></li>
     * <li><strong>{@code oke-workload-identity}</strong></li>
     * </ol>
     *
     * <p>It is strongly recommended, but strictly speaking not required, that any {@link AdpSupplierSelector}
     * implementation that uses {@link String}s as keys map these OCI authentication strategy descriptors to {@link
     * AdpSupplier} instances as follows:</p>
     *
     * <ul>
     *
     * <li><strong>{@code config}</strong> should {@linkplain AdpSupplierSelector#select(Object) select} a
     * <strong>{@link SimpleAdpSupplier}</strong> instance</li>
     *
     * <li><strong>{@code config-file}</strong> should {@linkplain AdpSupplierSelector#select(Object) select} a
     * <strong>{@link ConfigFileAdpSupplier}</strong> instance</li>
     *
     * <li><strong>{@code session-token-config-file}</strong> should {@linkplain AdpSupplierSelector#select(Object)
     * select} a <strong>{@link SessionTokenAdpSupplier}</strong> instance {@linkplain
     * SessionTokenAdpSupplier#ofConfigFileSupplier(java.util.function.Supplier) originating from a
     * <code>ConfigFile</code>}</li>
     *
     * <li><strong>{@code session-token-builder}</strong> should {@linkplain AdpSupplierSelector#select(Object) select}
     * a <strong>{@link SessionTokenAdpSupplier}</strong> instance {@linkplain
     * SessionTokenAdpSupplier#ofBuilder(com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider.SessionTokenAuthenticationDetailsProviderBuilder)
     * built by a <code>SessionTokenAuthenticationDetailsProviderBuilder</code>}</li>
     *
     * <li><strong>{@code instance-principals}</strong> should {@linkplain AdpSupplierSelector#select(Object) select} an
     * <strong>{@link InstancePrincipalsAdpSupplier}</strong> instance</li>

     * <li><strong>{@code resource-principal}</strong> should {@linkplain AdpSupplierSelector#select(Object) select} a
     * <strong>{@link ResourcePrincipalAdpSupplier}</strong> instance</li>
     *
     * <li><strong>{@code oke-workload-identity}</strong> should {@linkplain AdpSupplierSelector#select(Object) select}
     * an <strong>{@link OkeWorkloadIdentityAdpSupplier}</strong> instance</li>
     *
     * </ul>
     *
     * @see strategyDescriptors(ConfigAccessor)
     *
     * @see AdpSupplier
     *
     * @see AdpSupplierSelector#select(Object)
     *
     * @see CascadingAdpSupplier
     */
    public static final List<String> DEFAULT_ORDERED_ADP_STRATEGY_DESCRIPTORS =
        List.of("config",
                "config-file",
                "session-token-config-file",
                "session-token-builder",
                "instance-principals",
                "resource-principal",
                "oke-workload-identity");

    private static final Pattern WHITESPACE_COMMA_WHITESPACE_PATTERN = Pattern.compile("\\s*,\\s*");

    private AdpStrategyDescriptors() {
        super();
    }

    /**
     * A convenience method that returns a {@link List} of <em>OCI authentication strategy descriptors</em>, each
     * element of which can serve as a kind of shorthand identifier for a particular {@link AdpSupplier} that can
     * ultimately supply a {@link com.oracle.bmc.auth.BasicAuthenticationDetailsProvider
     * BasicAuthenticationDetailsProvider} implementation.
     *
     * <p>The behavior of this method is as follows:</p>
     *
     * <ol>
     *
     * <li>The supplied {@link ConfigAccessor} is {@linkplain ConfigAccessor#get(String) queried for} a {@link
     * String}-typed configuration value corresponding to the configuration name "{@code oci.auth-strategies}".
     *
     * <ol type="a">
     *
     * <li>If that results in an {@linkplain java.util.Optional#isEmpty() empty <code>Optional</code>}, the {@link
     * ConfigAccessor} is then {@linkplain ConfigAccessor#get(String) queried for} a {@link String}-typed configuration
     * value corresponding to the configuration name "{@code oci.auth-strategy}". (This property exists for backwards
     * compatibility only and its usage is discouraged.)
     *
     * <ol type="i">
     *
     * <li>If this still results in an {@linkplain java.util.Optional#isEmpty() empty <code>Optional</code>}, then the
     * value of the {@link #DEFAULT_ORDERED_ADP_STRATEGY_DESCRIPTORS} field is returned.</li>
     *
     * </ol></li>
     *
     * </ol></li>
     *
     * <li>The resulting {@link String} value is {@linkplain String#strip() stripped}.
     *
     * <ol type="a">
     *
     * <li>If the resulting {@link String} is {@linkplain String#isEmpty() empty}, then the value of the {@link
     * #DEFAULT_ORDERED_ADP_STRATEGY_DESCRIPTORS} field is returned.</li>
     *
     * </ol></li>
     *
     * <li>The resulting {@link String} value is now {@linkplain Pattern#split(CharSequence) split} as if by code
     * similar to {@link Pattern Pattern}{@code .}{@link Pattern#compile(String) compile}{@code ("\\s*,\\s*").}{@link
     * Pattern#split(CharSequence) split}{@code (value)}, and the resulting array is converted to an immutable {@link
     * List} via the {@link List#of(Object...)} method, and the resulting {@link List} is returned.</li>
     *
     * </ol>
     *
     * <p>This method does not necessarily return a determinate value.</p>
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return an unodifiable, unchanging {@link List} of names; never {@code null}
     *
     * @exception NullPointerException if {@code ca} is {@code null}
     *
     * @see #DEFAULT_ORDERED_ADP_STRATEGY_DESCRIPTORS
     *
     * @see AdpSupplierSelector
     *
     * @see AdpSupplier
     */
    public static List<String> strategyDescriptors(ConfigAccessor ca) {
        return ca.get("oci.auth-strategies")
            .or(() -> ca.get("oci.auth-strategy"))
            .map(String::strip)
            .filter(not(String::isEmpty))
            .map(WHITESPACE_COMMA_WHITESPACE_PATTERN::split)
            .map(List::of)
            .orElse(DEFAULT_ORDERED_ADP_STRATEGY_DESCRIPTORS);
    }

    /**
     * A convenience method that replaces occurrences of an element returned from an {@link
     * java.util.Iterator Iterator} with {@link Collection}s of replacement elements and returns an unmodifiable and
     * unchanging {@link List} representing the result.
     *
     * <p>This method is often used in combination with the return value of the {@link
     * #strategyDescriptors(ConfigAccessor)} method and an {@link AdpSupplierSelector}'s {@link
     * AdpSupplierSelector#select(Iterable)} method, but it is general-purpose.</p>
     *
     * @param i an {@link Iterable}; must not be {@code null}
     *
     * @param f a {@link Function} that receives an element from the supplied {@link Iterable} and returns a {@link
     * Collection} of replacement elements in its place (the {@link Collection} may be {@linkplain Collection#isEmpty()
     * empty}); a {@link Function} that wishes to keep the element could, for example, return the equivalent of {@link
     * List#of(Object) List.of(element)} or similar; must not return {@code null}; need not be safe for concurrent use
     * by multiple threads
     *
     * @return an unmodifiable, unchanging {@link List} of elements that results from the replacement operation
     *
     * @exception NullPointerException if any argument is {@code null}
     */
    public static <T> List<T> replaceElements(Iterable<? extends T> i, Function<? super T, ? extends Collection<? extends T>> f) {
        ArrayList<T> list = new ArrayList<>(9); // 9 == arbitrary, small
        i.forEach(e -> list.addAll(f.apply(e)));
        list.trimToSize();
        return Collections.unmodifiableList(list);
    }

}
