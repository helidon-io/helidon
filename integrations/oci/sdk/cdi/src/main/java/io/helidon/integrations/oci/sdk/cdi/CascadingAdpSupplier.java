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
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Stream.iterate;

class CascadingAdpSupplier<T extends BasicAuthenticationDetailsProvider> implements AdpSupplier<T> {

    private final List<? extends String> names;

    private final Function<? super String, ? extends AdpSupplier<? extends T>> f;

    CascadingAdpSupplier(Iterable<? extends String> names,
                         Function<? super String, ? extends AdpSupplier<? extends T>> f) {
        super();
        this.names = list(names);
        this.f = Objects.requireNonNull(f, "f");
    }

    @Override
    @SuppressWarnings("unchecked")
    public final Optional<T> get() {
        return (Optional<T>) this.names.stream()
            .map(this.f)
            .map(AdpSupplier::get)
            .dropWhile(Optional::isEmpty)
            .findFirst()
            .orElseGet(Optional::empty);
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner(",");
        this.names.forEach(sj::add);
        return sj.toString();
    }

    private static <T> List<T> list(Iterable<T> i) {
        return i instanceof Collection<T> c
            ? List.copyOf(c)
            : iterate(i.iterator(), Iterator::hasNext, identity()).map(Iterator::next).toList();
    }

}
