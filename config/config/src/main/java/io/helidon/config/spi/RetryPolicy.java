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

import java.time.Duration;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;
import io.helidon.config.RetryPolicies;

/**
 * Mechanism for controlling retry of attempts to load data by an
 * {@link AbstractSource}.
 * <p>
 * When an {@code AbstractSource} attempts to load the underlying data it uses a
 * {@code RetryPolicy} to govern if and how it retries the load operation in
 * case of errors.
 * <p>
 * The {@link #execute(java.util.function.Supplier) } method of each policy
 * implementation must perform at least one attempt to load the data, even if it
 * chooses not to retry in case of errors.
 */
@FunctionalInterface
public interface RetryPolicy extends Supplier<RetryPolicy> {

    /**
     * Constructs a {@code RetryPolicy} from meta-configuration.
     * <p>
     * As described with {@link ConfigSource#create(Config)}, the config system
     * can load {@code ConfigSource}s using meta-configuration, which supports
     * specifying retry policies. The
     * {@link RetryPolicies built-in retry policies} and custom ones are
     * supported. (The support is tightly connected with
     * {@link AbstractSource.Builder#init(Config) AbstractSource extensions} and
     * will not be automatically provided by any another config source
     * implementations.)
     * <p>
     * The meta-configuration for a config source can set the property
     * {@code retry-policy} using the following nested {@code properties}:
     * <ul>
     * <li>{@code type} - name of the retry policy implementation.
     * <table class="config">
     * <caption>Built-in Retry Policies</caption>
     * <tr>
     * <th>Name</th>
     * <th>Policy</th>
     * <th>Properties</th>
     * </tr>
     * <tr>
     * <td>{@code repeat}</td>
     * <td>Tries to load at regular intervals until the retry count reaches
     * {@code retries}. See {@link RetryPolicies#repeat(int)}, and
     * {@link RetryPolicies.Builder} for details on the {@code properties}.</td>
     * <td>
     * <ul>
     * <li>{@code retries} (required) in {@code int} format</li>
     * <li>{@code delay} in {@link Duration} format</li>
     * <li>{@code delay-factor} - in {@code double} format</li>
     * <li>{@code call-timeout} - in {@link Duration} format</li>
     * <li>{@code overall-timeout} - in {@link Duration} format</li>
     * </ul>
     * </td>
     * </tr>
     * </table>
     *
     * </li>
     * <li>{@code class} - fully qualified class name of custom retry policy
     * implementation or a builder class that implements a {@code build()}
     * method that returns a {@code RetryPolicy}.
     * </li>
     * </ul>
     * For a given config source use either {@code type} or {@code class} to
     * indicate a retry policy but not both. If both appear the config system
     * ignores the {@code class} setting.
     * <p>
     * See {@link ConfigSource#create(Config)} for example of using built-in retry
     * policies.
     * <h3>Meta-configuration Support for Custom Retry Policies</h3>
     * To support settings in meta-configuration, a custom retry policy must
     * follow this pattern.
     * <p>
     * The implementation class should define a Java bean property for each
     * meta-configuration property it needs to support. The config system uses
     * mapping functions to convert the text in the
     * meta-configuration into the correct Java type and then assigns the value
     * to the correspondingly-named Java bean property defined on the custom
     * policy instance. See the built-in mappers defined in
     * {@link io.helidon.config.ConfigMappers} to see what Java types are
     * automatically supported.
     *
     * @param metaConfig meta-configuration used to initialize returned retry
     * policy instance from.
     * @return new instance of retry policy described by {@code metaConfig}
     * @throws MissingValueException in case the configuration tree does not
     * contain all expected sub-nodes required by the mapper implementation to
     * provide instance of Java type.
     * @throws ConfigMappingException in case the mapper fails to map the
     * (existing) configuration tree represented by the supplied configuration
     * node to an instance of a given Java type.
     * @see ConfigSources#load(Supplier[])
     * @see ConfigSources#load(Config)
     */
    static RetryPolicy create(Config metaConfig) throws ConfigMappingException, MissingValueException {
        return RetryPolicyConfigMapper.instance().apply(metaConfig);
    }

    /**
     * Invokes the provided {@code Supplier} to read the source data and returns
     * that data.
     * <p>
     * The implementation of this method incorporates the retry logic.
     *
     * @param call supplier of {@code T}
     * @param <T> result type
     * @return loaded data returned by the provided {@code Supplier}
     */
    <T> T execute(Supplier<T> call);

    /**
     * Cancels the current use of the retry policy.
     * <p>
     * Implementations should correctly handle three cases:
     * <ol>
     * <li>{@code cancel} invoked when no invocation of {@code execute} is in
     * progress,</li>
     * <li>{@code cancel} invoked when an invocation of {@code execute} is
     * active but no attempted load is actually in progress (e.g., a prior
     * attempt failed and the retry policy is waiting for some time to pass
     * before trying again), and</li>
     * <li>{@code cancel} invoked while a load attempt is in progress.</li>
     * </ol>
     *
     * @param mayInterruptIfRunning whether an in-progress load attempt should
     * be interrupted
     * @return {@code false} if the task could not be canceled, typically
     * because it has already completed; {@code true} otherwise
     */
    default boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    default RetryPolicy get() {
        return this;
    }
}
