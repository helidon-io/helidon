/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.secrets.mp.configsource;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * A utility class containing useful operations for guarding {@link Function}s.
 *
 * @see #guard(Function, Predicate)
 */
final class Guards {

    private Guards() {
        super();
    }

    /**
     * Returns a {@link Function} that guards the supplied {@link Function} with the supplied {@link Predicate},
     * returning {@code null} if the {@link Predicate}'s {@link Predicate#test(Object)} method returns {@code false},
     * and returning the result of invoking the supplied {@link Function} otherwise.
     *
     * @param <T> the type of the supplied {@link Function}'s sole parameter
     *
     * @param <R> the return type of the supplied {@link Function}
     *
     * @param f the {@link Function} to guard; must not be {@code null}
     *
     * @param p the {@link Predicate} used to guard the supplied {@link Function}; must not be {@code null}
     *
     * @return {@code null} if the supplied {@link Predicate}'s {@link Predicate#test(Object)} method returns {@code
     * false}, and returning the result of invoking the supplied {@link Function} otherwise
     *
     * @exception NullPointerException if any argument is {@code null}
     */
    static <T, R> Function<T, R> guard(Function<T, R> f, Predicate<? super T> p) {
        Objects.requireNonNull(f, "f");
        Objects.requireNonNull(p, "p");
        return t -> p.test(t) ? f.apply(t) : null;
    }

    static <T extends CharSequence, R> Function<T, R> guardWithAcceptPattern(Function<T, R> f, Pattern p) {
        return guard(f, cs -> p.matcher(cs).matches());
    }

}
