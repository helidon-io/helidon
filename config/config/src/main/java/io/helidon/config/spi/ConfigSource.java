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

import java.net.URL;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;
import io.helidon.config.spi.ConfigNode.ObjectNode;

/**
 * {@link Source} of configuration.
 *
 * @see Config.Builder#sources(Supplier)
 * @see Config.Builder#sources(Supplier, Supplier)
 * @see Config.Builder#sources(Supplier, Supplier, Supplier)
 * @see Config.Builder#sources(java.util.List)
 * @see AbstractConfigSource
 * @see AbstractParsableConfigSource
 * @see ConfigSources ConfigSources - access built-in implementations.
 */
@FunctionalInterface
public interface ConfigSource extends Source<ObjectNode>, Supplier<ConfigSource> {

    /**
     * Initializes a {@link ConfigSource} from meta-configuration.
     * <p>
     * Meta-config can contain the following top level properties to define one
     * or more {@code ConfigSource}s:
     * <ul>
     * <li>{@code type} - specifies the built-in configuration type
     * (environment-variables, system-properties, directory, file, url,
     * prefixed, classpath) or the <a href="#customSourcesAndTypes">custom
     * config types</a> of the {@code ConfigSource} being defined.
     * </li>
     * <li>{@code class} - fully-qualified class name of one of:
     * <ul>
     * <li>a {@link ConfigSource} implementation,</li>
     * <li>a {@link Config.Builder} implementation with a {@code build()} method
     * that returns a {@code ConfigSource} instance.</li>
     * </ul>
     * See the section below on <a href="#customSourcesAndTypes">custom source
     * types</a>.
     * </ul>
     * The meta-config for a source should specify either {@code type} or
     * {@code class} but not both. If the meta-config specifies both the config
     * system ignores the {@code class} information.
     * <p>
     * As the config system loads configuration it uses mappers to convert the
     * raw data into Java types. See {@link ConfigMapperProvider} for
     * details about mapping.
     * <p>
     * The meta-config can modify a {@code type} or {@code class} source
     * declaration using {@code properties}. See
     * {@link AbstractParsableConfigSource.Builder#init(Config)} for the
     * available properties for types other than {@code system-properties} and
     * {@code environment-variables} (which do not support {@code properties}
     * settings).
     * <table>
     * <caption><b>Predefined Configuration Source Types</b></caption>
     * <tr>
     * <th>Source Type</th>
     * <th>Further Information</th>
     * <th>Mandatory Properties</th>
     * </tr>
     * <tr>
     * <td>{@code system-properties}</td>
     * <td>{@link ConfigSources#systemProperties()}</td>
     * <td>none</td>
     * </tr>
     * <tr>
     * <td>{@code environment-variables}</td>
     * <td>{@link ConfigSources#environmentVariables()}</td>
     * <td>none</td>
     * </tr>
     * <tr>
     * <td>{@code classpath}</td>
     * <td>{@link ConfigSources#classpath(String)}</td>
     * <td>{@code resource}</td>
     * </tr>
     * <tr>
     * <td>{@code file}</td>
     * <td>{@link ConfigSources#file(String)}</td>
     * <td>{@code path}</td>
     * </tr>
     * <tr>
     * <td>{@code directory}</td>
     * <td>{@link ConfigSources#directory(String)}</td>
     * <td>{@code path}</td>
     * </tr>
     * <tr>
     * <td>{@code url}</td>
     * <td>{@link ConfigSources#url(URL)}</td>
     * <td>{@code url}</td>
     * </tr>
     * <tr>
     * <td>{@code prefixed}</td>
     * <td>{@link ConfigSources#prefixed(String, Supplier)}</td>
     * <td> {@code key}<br> {@code type} or {@code class}<br> {@code properties}
     * </td>
     * <td></td>
     * </tr>
     * </table>
     *
     * Example configuration in HOCON format:
     * <pre>
     * sources = [
     *     {
     *         type = "environment-variables"
     *     }
     *     {
     *         type = "system-properties"
     *     }
     *     {
     *         type = "directory"
     *         properties {
     *             path = "conf/secrets"
     *             media-type-mapping {
     *                 yaml = "application/x-yaml"
     *                 password = "application/base64"
     *             }
     *             polling-strategy {
     *                 type = "regular"
     *                 properties {
     *                     interval = "PT15S"
     *                 }
     *             }
     *         }
     *     }
     *     {
     *         type = "url"
     *         properties {
     *             url = "http://config-service/my-config"
     *             media-type = "application/hocon"
     *             optional = true
     *             retry-policy {
     *                 type = "repeat"
     *                 properties {
     *                     retries = 3
     *                 }
     *             }
     *         }
     *     }
     *     {
     *         type = "file"
     *         properties {
     *             path = "conf/config.yaml"
     *             polling-strategy {
     *                 type = "watch"
     *             }
     *         }
     *     }
     *     {
     *         type = "prefixed"
     *         properties {
     *             key = "app"
     *             type = "classpath"
     *             properties {
     *                 resource = "app.yaml"
     *             }
     *         }
     *     }
     *     {
     *         type = "classpath"
     *         properties {
     *             resource = "default.yaml"
     *         }
     *     }
     * ]
     * </pre> The example refers to the built-in {@code polling-strategy} types
     * {@code regular} and {@code watch}. See {@link PollingStrategy} for
     * details about all supported properties and custom implementation support.
     * It also shows the built-in {@code retry-policy} type {@code repeat}. See
     * {@link RetryPolicy#create(Config) RetryPolicy} for more information.
     *
     * <h2><a id="customSourcesAndTypes">Custom Sources and Source
     * Types</a></h2>
     * <h3>Custom Configuration Sources</h3>
     * The application can define a custom config source using {@code class}
     * instead of {@code type} in the meta-configuration. The referenced class
     * must implement either {@link ConfigSource} or {@link Config.Builder}. If
     * the custom implementation extends
     * {@link AbstractParsableConfigSource.Builder} then the config system will
     * invoke its {@code init} method passing a {@code Config} object
     * representing the information from the meta-configuration for that custom
     * source. The implementation can then use the relevant properties to load
     * and manage the configuration from the origin.
     * <h3>Custom Configuration Source Types</h3>
     * The application can also add a custom source type to the list of built-in
     * source types. The config system looks for the resource
     * {@code META-INF/resources/meta-config-sources.properties} on the
     * classpath and uses its contents to define custom source types. For each
     * property the name is a new custom source type and the value is the
     * fully-qualified class name of the custom {@code ConfigSource} or a
     * builder for a custom {@code ConfigSource}.
     * <p>
     * For example, the module {@code helidon-config-git} includes the resource
     * {@code META-INF/resources/meta-config-sources.properties} containing
     * <pre>
     * git = io.helidon.config.git.GitConfigSourceBuilder
     * </pre> This defines the new source type {@code git} which can then be
     * referenced from meta-configuration this way:
     * <pre>
     * {
     *     type = "git"
     *     properties {
     *         path = "application.conf"
     *         directory = "/app-config/"
     *     }
     * }
     * </pre>
     *
     * @param metaConfig meta-configuration used to initialize the
     * {@code ConfigSource}
     * @return {@code ConfigSource} described by {@code metaConfig}
     * @throws MissingValueException if the configuration tree does not contain
     * all expected sub-nodes required by the mapper implementation to provide
     * an instance of the corresponding Java type.
     * @throws ConfigMappingException if the mapper fails to map the (existing)
     * configuration tree represented by the supplied configuration node to an
     * instance of the given Java type
     * @see ConfigSources#load(Supplier[])
     * @see ConfigSources#load(Config)
     */
    static ConfigSource create(Config metaConfig) throws ConfigMappingException, MissingValueException {
        return ConfigSourceConfigMapper.instance().apply(metaConfig);
    }

    @Override
    default ConfigSource get() {
        return this;
    }

    /**
     * Initialize the config source with a {@link ConfigContext}.
     * <p>
     * The method is executed during {@link Config} bootstrapping by {@link Config.Builder}.
     *
     * @param context a config context
     */
    default void init(ConfigContext context) {
    }

}
