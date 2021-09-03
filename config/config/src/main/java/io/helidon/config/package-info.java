/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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
 * Provides interfaces and classes for loading and working with immutable, tree-structured
 * configuration data.
 *
 * <h2>Loading a Configuration</h2>
 *
 * The program loads a configuration from either the default sources (using {@link io.helidon.config.Config#create}) or
 * from specified {@link io.helidon.config.spi.ConfigSource}s
 * (using {@link io.helidon.config.Config.Builder Config.Builder}).
 * <p>
 * The default sources include all of the following, in order:
 * <ol>
 * <li>environment variables</li>
 * <li>Java system properties</li>
 * <li>the first of the following (if any) on the classpath:
 * <ol type="a">
 * <li><code>application.yaml</code></li>
 * <li><code>application.conf</code> (<a href="https://github.com/lightbend/config/blob/master/HOCON.md">HOCON</a> format)</li>
 * <li><code>application.json</code></li>
 * <li><code>application.properties</code></li>
 * </ol>
 * </li>
 * </ol>
 * The config implementation supports sources in the above formats. The program can add
 * support for other formats by implementing {@link io.helidon.config.spi.ConfigParser}.
 * <p>
 * See {@link io.helidon.config.Config} for further information.
 *
 * <h2>Using a Configuration Tree</h2>
 *
 * This overview summarizes how a program can use loaded configuration.
 * For full details see the {@link io.helidon.config.Config} class.
 * <p>
 * Once loaded, configuration information is available to the program as {@code Config}
 * nodes in a tree. Each node has:
 * <ul>
 * <li>a name,</li>
 * <li>a {@link io.helidon.config.Config.Key} representing
 * the full path from the root to the node, and </li>
 * <li>some content. </li>
 * </ul>
 * The {@link io.helidon.config.Config#type()} method
 * returns an enum value {@link io.helidon.config.Config.Type} that tells how the
 * program should interpret the content of the node.
 * <table class="config">
 * <caption><b>Config Node Types</b></caption>
 * <tr>
 * <th>Type</th>
 * <th>Meaning</th>
 * <th>Useful {@code Config} Methods</th>
 * </tr>
 * <tbody>
 * <tr>
 * <td>VALUE</td>
 * <td>value node with an optional direct {@code String} value</td>
 * </tr>
 * <tr>
 * <td>LIST</td>
 * <td>list of indexed nodes with an optional "direct" value</td>
 * <td>{@link io.helidon.config.Config#asList(java.lang.Class)},
 * {@link io.helidon.config.Config#asNodeList()}</td>
 * </tr>
 * <tr>
 * <td>OBJECT</td>
 * <td>object node with, possibly, child nodes and an optional "direct" value</td>
 * <td>{@link io.helidon.config.Config#asNodeList()}</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <h3>Configuration Values and Types</h3>
 * While each node's direct value is accessible as
 * an {@code Optional<String>}, the program can also
 * have the node convert its {@code String} value to a typed {@link io.helidon.config.ConfigValue} using methods such as
 * {@link io.helidon.config.Config#asString()}, {@link io.helidon.config.Config#asLong()} etc.
 * <p>
 * The program can provide its own {@link io.helidon.config.spi.ConfigMapperProvider} implementations
 * to deal with more complicated value mapping needs. See also {@link io.helidon.config.Config.Builder#addStringMapper}
 * and {@link io.helidon.config.Config.Builder#addMapper(java.lang.Class, java.util.function.Function)}.
 *
 * <h3>Navigation</h3>
 *
 * The program can retrieve a node's child
 * nodes as a {@code List}.
 * <p>
 * The program can navigate directly to a given subnode using the
 * {@link io.helidon.config.Config#get} method and passing the dotted path to the subnode.
 * <p>
 * The {@link io.helidon.config.Config#traverse} methods return a stream of nodes
 * in depth-first order.
 *
 * <h3>Bulk Retrieval of Values</h3>
 *
 * The program can retrieve a {@code Map} of dotted names to {@code String} values
 * for a node's entire subtree using {@link io.helidon.config.Config#asMap}.
 *
 * <h3>Monitoring Changes</h3>
 *
 * The program can react to configuration changes by passing a listener
 * to {@link io.helidon.config.Config#onChange(java.util.function.Consumer)}.
 *
 * <h3 id="conversions">Converting Configuration to Java Types</h3>
 * The {@link io.helidon.config.Config} class provides many methods for converting config
 * {@code String} values to Java primitives and simple Java types, as well as
 * mapping parts of the config tree to {@code List}s and {@code Map}s.
 * <p>
 * The application can convert config data to arbitrary types using the
 * {@link io.helidon.config.Config#as(java.lang.Class)} and {@link io.helidon.config.Config#as(java.util.function.Function)}
 * methods, and can provide its own conversions to handle
 * custom types by implementing a mapping function and registering it
 * with a {@code Config.Builder} using the {@link io.helidon.config.Config.Builder#addMapper} method.
 * <p>
 * If the {@code Config.as} method finds no matching registered mapper it will throw
 * a {@link io.helidon.config.ConfigMappingException}.
 * Support for automated mapping to types is available in module config-beans.
 *
 * @see io.helidon.config.spi Configuration SPI
 */
package io.helidon.config;
