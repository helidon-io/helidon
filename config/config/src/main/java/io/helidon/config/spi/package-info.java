/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

/**
 * Configuration SPI that defines the behavior developers can implement to extend the config system.
 * <p>
 * The most likely developer-implemented extension points:
 * <table class="config">
 * <caption>Common Extension Points</caption>
 * <tr>
 * <th>Interface</th>
 * <th>Purpose</th>
 * </tr>
 * <tr>
 * <td>{@link ConfigSource}</td>
 * <td>Loads configuration data from a type of source.
 * </td>
 * </tr>
 * <tr>
 * <td>{@link ConfigParser}</td>
 * <td>Converts configuration data into a {@link ConfigNode.ObjectNode}.</td>
 * </tr>
 * <tr>
 * <td>{@link ConfigFilter}</td>
 * <td>Filters configuration values after they have been read from a
 * {@code ConfigSource} but before they are used in building a
 * {@link io.helidon.config.Config} tree.</td>
 * </tr>
 * <tr>
 * <td>{@link ConfigMapperProvider}</td>
 * <td>Converts {@code Config} nodes or subtrees to Java types. See
 * {@link io.helidon.config.ConfigMappers} for the built-in mappers.</td>
 * </tr>
 * <tr>
 * <td>{@link OverrideSource}</td>
 * <td>Replaces values pf config nodes whose keys match specified conditions
 * with alternative values.</td>
 * </tr>
 * <tr>
 * <td>{@link PollingStrategy}</td>
 * <td>Notifies interested code when the data underlying a {@link Source} might
 * have changed.</td>
 * </tr>
 * <tr>
 * <td>{@link RetryPolicy}</td>
 * <td>Controls if and how a {@code Source} attempts to retry failed attempts to
 * load the underlying data.</td>
 * </tr>
 * </table>
 *
 * @see io.helidon.config Configuration API
 */
package io.helidon.config.spi;
