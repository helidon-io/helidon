/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config.mp;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigValue;
import io.helidon.config.MissingValueException;

class SeConfigValue<T> implements ConfigValue<T> {
    private final Config.Key key;
    private final Supplier<T> valueSupplier;

    SeConfigValue(Config.Key key, Supplier<T> valueSupplier) {
        this.key = key;
        this.valueSupplier = valueSupplier;
    }

    @Override
    public Config.Key key() {
        return key;
    }

    @Override
    public Optional<T> asOptional() throws ConfigMappingException {
        try {
            return Optional.of(valueSupplier.get());
        } catch (MissingValueException e) {
            return Optional.empty();
        }
    }

    @Override
    public <N> ConfigValue<N> as(Function<T, N> mapper) {
        return new SeConfigValue<>(key, () -> mapper.apply(valueSupplier.get()));
    }

    @Override
    public Supplier<T> supplier() {
        return valueSupplier;
    }

    @Override
    public Supplier<T> supplier(T defaultValue) {
        return () -> {
            try {
                return valueSupplier.get();
            } catch (MissingValueException e) {
                return defaultValue;
            }
        };
    }

    @Override
    public Supplier<Optional<T>> optionalSupplier() {
        return this::asOptional;
    }

    @Override
    public int hashCode() {
        try {
            return Objects.hash(key, asOptional());
        } catch (ConfigMappingException e) {
            return Objects.hash(key);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SeConfigValue<?> that = (SeConfigValue<?>) o;
        if (!key.equals(that.key)) {
            return false;
        }

        Optional<?> myOptional;
        try {
            myOptional = asOptional();
        } catch (ConfigMappingException e) {
            try {
                that.asOptional();
                // same key, one failed -> different value
                return false;
            } catch (ConfigMappingException configMappingException) {
                // same key, both failed -> same value
                return true;
            }
        }
        try {
            Optional<?> thatOptional = that.asOptional();
            return myOptional.equals(thatOptional);
        } catch (ConfigMappingException e) {
            // same key, one failed -> different value
            return false;
        }
    }
}
