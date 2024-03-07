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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * A {@link ConfigAccessor} implementation backed by <a
 * href="https://microprofile.io/project/eclipse/microprofile-config">MicroProfile Config</a> constructs.
 */
class MicroProfileConfigConfigAccessor implements ConfigAccessor {


    /*
     * Instance fields.
     */


    /**
     * A {@link Supplier} of {@link Config} instances.
     */
    private final Supplier<? extends Config> cs;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link MicroProfileConfigConfigAccessor}.
     *
     * <p>This constructor calls the {@link #MicroProfileConfigConfigAccessor(Supplier)} constructor with a reference to
     * the {@link ConfigProvider#getConfig()} method.</p>
     *
     * @see #MicroProfileConfigConfigAccessor(Supplier)
     */
    MicroProfileConfigConfigAccessor() {
        this(ConfigProvider::getConfig);
    }

    /**
     * Creates a new {@link MicroProfileConfigConfigAccessor}.
     *
     * <p>This constructor calls the {@link #MicroProfileConfigConfigAccessor(Supplier)} constructor with a {@link
     * Supplier} that always returns the supplied {@link Config} from its {@link Supplier#get() get()} method.</p>
     *
     * @param c a {@link Config} instance; must not be {@code null}
     *
     * @exception NullPointerException if {@code c} is {@code null}
     *
     * @see #MicroProfileConfigConfigAccessor(Supplier)
     */
    MicroProfileConfigConfigAccessor(Config c) {
        this(supplier(c));
    }

    /**
     * Creates a new {@link MicroProfileConfigConfigAccessor}.
     *
     * @param cs a {@link Supplier} of {@link Config} instances; must not be {@code null}
     *
     * @exception NullPointerException if {@code cs} is {@code null}
     */
    MicroProfileConfigConfigAccessor(Supplier<? extends Config> cs) {
        super();
        this.cs = Objects.requireNonNull(cs, "cs");
    }


    /*
     * Instance methods.
     */


    /**
     * Calls the {@link Config#getOptionalValue(String, Class)} method with the supplied {@code name} and {@link String
     * String.class} as its arguments and returns its result.
     *
     * @param name the configuration name; must not be {@code null}
     *
     * @return the result of invoking {@link Config#getOptionalValue(String, Class)} with the supplied {@code name} and {@link String
     * String.class} as its arguments; never {@code null}
     *
     * @exception NullPointerException if {@code name} is {@code null}, or if the {@link Supplier} of {@link Config}
     * instances {@linkplain #MicroProfileConfigConfigAccessor(Supplier) supplied at construction time} returns {@code
     * null} from its {@link Supplier#get() get()} method
     *
     * @see Config#getOptionalValue(String, Class)
     */
    @Override
    public Optional<String> get(String name) {
        // MicroProfile Config imlementations are (very surprisingly) not obligated to detect null names. Handle this
        // case early.
        Objects.requireNonNull(name, "name");
        // The MicroProfile Config specification and javadocs say nothing about whether getOptionalValue(String, Class)
        // can return null. The TCK implies that (thankfully) it must not. We follow that implication.
        return Optional.ofNullable(this.cs.get())
            .flatMap(c -> c.getOptionalValue(name, String.class));
    }


    /*
     * Static methods.
     */


    /**
     * Returns a memoized {@link Supplier} that returns the supplied {@link Config} on every invocation of its {@link
     * Supplier#get() get()} method, assuming the supplied {@link Config} is non-{@code null}.
     *
     * @param c the {@link Config} to return; must not be {@code null}
     *
     * @return a {@link Supplier} that returns the supplied {@link Config} on every invocation of its {@link
     * Supplier#get() get()} method; never {@code null}
     *
     * @exception NullPointerException if {@code c} is {@code null}
     */
    private static Supplier<? extends Config> supplier(Config c) {
        Objects.requireNonNull(c, "c");
        return () -> c;
    }

}
