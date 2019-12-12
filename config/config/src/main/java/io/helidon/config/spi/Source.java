/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.spi;

import java.util.Optional;
import java.util.concurrent.Flow;

import io.helidon.config.ConfigException;

/**
 * Source of the specified type {@code <T>} of data.
 *
 * @param <T> a type of source
 */
public interface Source<T> extends Changeable<T>, AutoCloseable {

    /**
     * Short, human-readable summary referring to the underlying source.
     * <p>
     * For example, a file path or a URL or any other information that helps the
     * user recognize the underlying origin of the data this {@code Source}
     * provides.
     * <p>
     * Default is the implementation class simple name with any {@code "Source"}
     * suffix removed.
     *
     * @return description of the source
     */
    default String description() {
        String name = this.getClass().getSimpleName();
        if (name.endsWith("Source")) {
            name = name.substring(0, name.length() - "Source".length());
        }
        return name;
    }

    /**
     * Loads the underlying source data, converting it into an {@code Optional}
     * around the parameterized type {@code T}.
     * <p>
     * Implementations should return {@link Optional#empty()} if the underlying
     * origin does not exist.
     * <p>
     * The method can be invoked repeatedly, for example during retries.
     *
     * @return {@code Optional<T>} referring to an instance of {@code T} as read
     * from the underlying origin of the data (if it exists) or
     * {@link Optional#empty()} otherwise
     * @throws ConfigException in case of errors loading from the underlying
     * origin
     */
    Optional<T> load() throws ConfigException;

    //
    // source changes
    //

    @Override
    @Deprecated
    default Flow.Publisher<Optional<T>> changes() { //TODO later remove, see Changeable interface
        return Flow.Subscriber::onComplete;
    }

    /**
     * Closes the @{code Source}, releasing any resources it holds.
     *
     * @throws Exception in case of errors encountered while closing the source
     */
    @Override
    default void close() throws Exception {
    }
}
