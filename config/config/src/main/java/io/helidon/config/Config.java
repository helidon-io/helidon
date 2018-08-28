/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.OptionalHelper;
import io.helidon.common.reactive.Flow;
import io.helidon.config.internal.ConfigKeyImpl;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigMapperProvider;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.OverrideSource;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Immutable tree-structured configuration.
 * <h2>Loading Configuration</h2>
 * Load the default configuration using the {@link #create} method.
 * <pre>{@code
 * Config config = Config.create();
 * }</pre> Use {@link Config.Builder} to construct a new {@code Config} instance
 * from one or more specific {@link ConfigSource}s.
 * <p>
 * The application can affect the way the system loads configuration by
 * implementing interfaces defined in the SPI, by explicitly constructing the
 * {@link Builder} which assembles the {@code Config}, and by using other
 * classes provided by the config system that influence loading.
 * <table summary="Some Config SPI Interfaces">
 * <tr>
 * <th>Class.Method</th>
 * <th>Application-implemented Interface</th>
 * <th>Purpose</th>
 * </tr>
 * <tr>
 * <td>{@link ConfigSources#from}</td>
 * <td>{@link ConfigSource}</td>
 * <td>Loads configuration from a different type of origin. Each
 * {@code ConfigSource} implementation handles a type of location. Different
 * instances of a given {@code ConfigSource} implementation represent separate
 * sources of that location type.</td>
 * </tr>
 * <tr>
 * <td>{@link Builder#addParser}</td>
 * <td>{@link ConfigParser}</td>
 * <td>Converts one format of config representation into the corresponding
 * {@code Config} tree.
 * </tr>
 * <tr>
 * <td>{@link Builder#addFilter}</td>
 * <td>{@link ConfigFilter}</td>
 * <td>Changes the {@code String} representation of each config value from one
 * {@code String} to another as the {@code Config} tree is built from its
 * sources.</td>
 * </tr>
 * <tr>
 * <td>{@link OverrideSources} methods</td>
 * <td></td>
 * <td>Replaces config {@code String} values during loading based on their keys.
 * Programs provide overrides in Java property file format on the classpath, at
 * a URL, or in a file, or by invoking {@link OverrideSources#from} and passing
 * the name-matching expressions and the corresponding replacement value as a
 * {@code Map}.</td>
 * </tr>
 * <tr>
 * <td>{@link Builder#addMapper}</td>
 * <td>{@link ConfigMapper}</td>
 * <td>Implements conversion from a {@code Config} node (typically with
 * children) to an application-specific Java type.</td>
 * </tr>
 * </table>
 *
 * <h2>Navigating in a Configuration Tree</h2>
 * Each loaded configuration is a tree of {@code Config} objects. The
 * application can access an arbitrary node in the tree by passing its
 * fully-qualified name to {@link Config#get}:
 * <pre>{@code
 * Config greeting = config.get("greeting");
 * }</pre> Method {@link #key()} always returns fully-qualified
 * {@link Config.Key} of a config node.
 * <pre>{@code
 * assert greeting.key().toString().equals("greeting")
 * }</pre> These are equivalent ways of obtaining the same {@code Config}
 * instance, and the two assertions will succeed:
 * <pre>{@code
 * Config name1 = config.get("app.services.svc1.name");
 * Config name2 = config.get("app").get("services.svc1.name");
 * Config name3 = config.get("app.services").get("svc1").get("name");
 * Config name4 = config.get("app").get("services").get("svc1").get("name");
 *
 * assert name4.key().equals(Key.from("app.services.svc1.name"))
 * assert name1 == name2 == name3 == name4
 * }</pre> The {@link #get} method always returns a {@code Config} object, even
 * if no configuration is present using the corresponding key. The application
 * can invoke the {@link #type} method to find out the type of the node,
 * represented by one of the {@link Type} enum values. The {@link #exists}
 * method tells whether or not the {@code Config} node represents existing
 * configuration.
 * <pre>{@code
 * if (!config.get("very.rare.prop42").exists()) {
 *     // node 'very.rare.prop42' does NOT exist
 * }
 * }</pre> The {@link #traverse} method visits all nodes in a subtree. This
 * example gathers all nodes with keys matching {@code logging.**.level} -- that
 * is, all nodes within the "logging" subtree that have a key ending in "level"
 * and also has a single value:
 * <pre>{@code
 * Map<String,String> loggingLevels = config.get("logging")  // find "logging" subtree
 *     .traverse()                                           // traverse through logging' nodes
 *     .filter(node -> node.isLeaf())                        // filter leaf values
 *     .filter(node -> node.name().equals("level"))          // filter key suffix '.level'
 *     .collect(Collectors.toMap(Config::key, Config::asString));
 * }</pre>
 * <p>
 * To retrieve children of a config node use
 * {@link #nodeList()}, {@link #asNodeList()} or {@link #asNodeList(List)}
 * <ul>
 * <li>on an {@link Type#OBJECT object} node to get all object members,</li>
 * <li>on a {@link Type#LIST list} node to get all list elements.</li>
 * </ul>
 * <p>
 * On a leaf {@link Type#VALUE value} node get the {@code String} value using
 * {@link #value()}, {@link #asString()} or {@link #asString(String)}.
 *
 * <h2>Converting Configuration Values to Types</h2>
 * <h3>Explicit Conversion by the Application</h3>
 * The interpretation of a configuration node, including what datatype to use,
 * is up to the application. To interpret a node's value as a type other than
 * {@code String} the application can invoke one of these convenience methods:
 * <ul>
 * <li>{@code as<typename>} such as {@code asBoolean, asDouble, asInt}, etc.
 * which return Java primitive data values ({@code boolean, double, int}, etc.)
 * <p>
 * Each method has two variants: one without parameters that throws
 * {@link MissingValueException} if the config at the node does not exist, and
 * one that accepts the default value as a single parameter. For example:
 * <pre>{@code
 * long l1 = config.asLong();
 * long l2 = config.asLong(42L);
 * }</pre>
 * </li>
 * <li>{@code asOptional<typename>} which returns the autoboxed datatype wrapped
 * in an {@code Optional}.
 * <p>
 * For example:
 * <pre>{@code
 * Optional<Long> l3 = config.asOptionalLong();
 * }</pre>
 * </li>
 * <li>{@code as(Class)} or {@code as(Class, defaultValue)} or
 * {@code asOptional(Class)} which return an instance of the requested type.
 * <p>
 * For example:
 * <pre>{@code
 * Long l1 = config.as(Long.class);
 * Long l2 = config.as(Long.class, 42L);
 * Optional<Long> l3 = config.asOptional(Long.class);
 * }</pre>
 * </ul>
 * <h3>Using Built-in and Custom Mappers</h3>
 * Each {@code as*} method delegates to a {@link ConfigMapper} to convert a
 * config node to a type. The config system provides mappers for primitive
 * datatypes, {@code List}s, and {@code Map}s, and automatically registers them
 * with each {@code Config.Builder} instance.
 * <p>
 * To deal with application-specific types, the application can provide its own
 * mapping logic by:
 * <ul>
 * <li>invoking one of the {@link Config#map} method variants, </li>
 * <li>adding custom {@code ConfigMapper} implementations using the
 * {@link Builder#addMapper} method,</li>
 * <li>registering custom mappers using the Java service loader mechanism. (See
 * {@link ConfigMapper} for details.)
 * </li>
 * </ul>
 * <p>
 * Returning to the {@code long} example:
 * <pre>{@code
 * long l4 = config.map(ConfigMappers::toLong);
 * long l5 = config.map(ConfigMappers::toLong, 42L);
 * Optional<Long> l6 = config.mapOptional(ConfigMappers::toLong);
 * }</pre> Note that the variants of {@code map} accept a {@code Function} or a
 * {@code ConfigMapper}. The {@link ConfigMappers} class implements many useful
 * conversions; check there before writing your own custom mapper.
 * <p>
 * If there is no explicitly registered {@link ConfigMapper} instance in a
 * {@link Builder} for converting a given type then the config system uses
 * generic mapping. See {@link ConfigMapperManager} for details on how generic
 * mapping works.
 * <h2><a name="multipleSources">Handling Multiple Configuration
 * Sources</a></h2>
 * A {@code Config} instance, including the default {@code Config} returned by
 * {@link #create}, might be associated with multiple {@link ConfigSource}s. The
 * config system deals with multiple sources as follows.
 * <p>
 * The {@link ConfigSources.CompositeBuilder} class handles multiple config
 * sources; in fact the config system uses an instance of that builder
 * automatically when your application invokes {@link Config#from} and
 * {@link Config#withSources}, for example. Each such composite builder has a
 * merging strategy that controls how the config system will search the multiple
 * config sources for a given key. By default each {@code CompositeBuilder} uses
 * the {@link FallbackMergingStrategy}: configuration sources earlier in the
 * list have a higher priority than the later ones. The system behaves as if,
 * when resolving a value of a key, it checks each source in sequence order. As
 * soon as one source contains a value for the key the system returns that
 * value, ignoring any sources that fall later in the list.
 * <p>
 * Your application can set a different strategy by constructing its own
 * {@code CompositeBuilder} and invoking
 * {@link ConfigSources.CompositeBuilder#mergingStrategy}, passing the strategy
 * to be used:
 * <pre>
 * Config.withSources(ConfigSources.from(source1, source2, source3)
 *                      .mergingStrategy(new MyMergingStrategy());
 * </pre>
 *
 *
 */
public interface Config {

    /**
     * Returns empty instance of {@code Config}.
     *
     * @return empty instance of {@code Config}.
     */
    static Config empty() {
        return BuilderImpl.EmptyConfigHolder.EMPTY;
    }

    //
    // tree (config nodes) method
    //

    /**
     * Returns a new default {@link Config} loaded using one of the
     * configuration files available on the classpath and/or using the runtime
     * environment.
     * <p>
     * The config system loads the default configuration using a default {@link Builder}
     * which loads configuration data as described below. In contrast, the application can
     * control how and from where configuration is loaded by explicitly creating and fine-tuning
     * one or more {@code Builder} instances itself.
     * <ol>
     * <li>Meta-configuration
     * <p>
     * Meta-configuration specifies at least one {@link ConfigSource} or
     * {@link Config.Builder} from which the system can load configuration. The
     * config system searches for at most one of the following
     * meta-configuration locations on the classpath, in this order:
     * <ol type="a">
     * <li>{@code meta-config.yaml} - meta configuration file in YAML
     * format</li>
     * <li>{@code meta-config.conf} - meta configuration file in HOCON
     * format</li>
     * <li>{@code meta-config.json} - meta configuration file in JSON
     * format</li>
     * <li>{@code meta-config.properties} - meta configuration file in Java
     * Properties format</li></ol>
     * </li>
     * <li>Configuration
     * <p>
     * In the absence of meta-configuration the config system loads the default
     * configuration from all of the following sources:
     * <ol type="a">
     * <li>{@link ConfigSources#environmentVariables() environment variables};</li>
     * <li>{@link ConfigSources#systemProperties() system properties}</li>
     * <li>at most one of following locations on the classpath, in this order:
     * <ol>
     * <li>{@code application.yaml} - configuration file in YAML format</li>
     * <li>{@code application.conf} - configuration file in HOCON format</li>
     * <li>{@code application.json} - configuration file in JSON format</li>
     * <li>{@code application.properties} - configuration file in Java
     * Properties format</li>
     * </ol>
     * </li>
     * </ol>
     * The config system uses only the first {@code application.*} location it
     * finds for which it can locate a {@link ConfigParser} that supports the
     * corresponding {@link ConfigParser#getSupportedMediaTypes() media type}.
     * <p>
     * When creating the default configuration the config system detects parsers
     * that were loaded using the {@link java.util.ServiceLoader} mechanism or,
     * if it finds none loaded, a built-in parser provided by the config system.
     * </li>
     * </ol>
     * Every invocation of this method creates a new {@code Config} instance
     * which has neither a {@link PollingStrategies#nop() polling strategy} nor
     * a {@link RetryPolicies#justCall() retry policy}. To set up these and other
     * behaviors the application should create explicitly a {@code Config.Builder},
     * tailor it accordingly, and then use its {@code build} method to create the
     * {@code Config} instance as desired.
     *
     * @return new instance of {@link Config}
     * @see #loadSourcesFrom(Supplier[])
     */
    static Config create() {
        return builder().build();
    }

    /**
     * Creates a new {@link Config} loaded from environment variables, system
     * properties, and the specified {@link ConfigSource}s.
     * <p>
     * The resulting configuration uses the following sources, in order:
     * <ol>
     * <li>{@link ConfigSources#environmentVariables() environment variables config source}<br>
     * Can disabled by {@link Builder#disableEnvironmentVariablesSource()}</li>
     * <li>{@link ConfigSources#systemProperties() system properties config source}
     * Can disabled by {@link Builder#disableSystemPropertiesSource()}</li>
     * <li>Source(s) specified by user in the method.</li>
     * </ol>
     * See <a href="multipleSources">multiple sources</a> for more information.
     *
     * @param configSources ordered list of configuration sources
     * @return new instance of {@link Config}
     * @see #loadSourcesFrom(Supplier[])
     * @see #withSources(Supplier[])
     * @see #loadSources(Supplier[])
     * @see Builder#sources(List)
     * @see Builder#disableEnvironmentVariablesSource()
     * @see Builder#disableSystemPropertiesSource()
     * @see ConfigSources#from(Supplier[])
     * @see ConfigSources.CompositeBuilder
     * @see ConfigSources.MergingStrategy
     */
    @SafeVarargs
    static Config from(Supplier<ConfigSource>... configSources) {
        return withSources(configSources).build();
    }

    /**
     * Creates a new {@link Config} loaded from the specified
     * {@link ConfigSource}s representing meta-configurations.
     * <p>
     * See {@link ConfigSource#from(Config)} for more information about the
     * format of meta-configuration.
     *
     * @param metaSources ordered list of meta sources
     * @return new instance of {@link Config}
     * @see #from(Supplier[])
     * @see #withSources(Supplier[])
     * @see #loadSources(Supplier[])
     * @see ConfigSources#load(Supplier[])
     */
    @SafeVarargs
    static Config loadSourcesFrom(Supplier<ConfigSource>... metaSources) {
        return loadSources(metaSources).build();
    }

    /**
     * Provides a {@link Builder} for creating a {@link Config}
     * based on the specified {@link ConfigSource} instances.
     * <p>
     * The resulting configuration uses the following sources, in order:
     * <ol>
     * <li>{@link ConfigSources#environmentVariables() environment variables}<br>
     * Can be disabled by invoking {@link Builder#disableEnvironmentVariablesSource()}</li>
     * <li>{@link ConfigSources#systemProperties() system properties config source}<br>
     * Can be disabled by invoking {@link Builder#disableSystemPropertiesSource()}</li>
     * <li>source(s) passed in the method invocation</li>
     * </ol>
     * See <a href="multipleSources">multiple sources</a> for more information.
     *
     * @param configSources ordered list of configuration sources
     * @return new initialized Builder instance
     * @see #builder()
     * @see #from(Supplier[])
     * @see #loadSourcesFrom(Supplier[])
     * @see #loadSources(Supplier[])
     * @see Builder#sources(List)
     * @see Builder#disableEnvironmentVariablesSource()
     * @see Builder#disableSystemPropertiesSource()
     * @see ConfigSources#from(Supplier[])
     * @see ConfigSources.CompositeBuilder
     * @see ConfigSources.MergingStrategy
     */
    @SafeVarargs
    static Builder withSources(Supplier<ConfigSource>... configSources) {
        return builder().sources(CollectionsHelper.listOf(configSources));
    }

    /**
     * Provides a {@link Builder} for creating a {@link Config} based on the
     * specified {@link ConfigSource}s representing meta-configurations.
     * <p>
     * Each meta-configuration source should set the {@code sources} property to
     * be an array of config sources. See {@link ConfigSource#from(Config)} for
     * more information about the format of meta-configuration.
     *
     * @param metaSources ordered list of meta sources
     * @return new initialized Builder instance
     * @see #builder()
     * @see #withSources(Supplier[])
     * @see ConfigSources#load(Supplier[])
     * @see #loadSourcesFrom(Supplier[])
     */
    @SafeVarargs
    static Builder loadSources(Supplier<ConfigSource>... metaSources) {
        return withSources(ConfigSources.load(metaSources))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource();
    }

    /**
     * Provides a {@link Builder} for creating a {@link Config} instance.
     *
     * @return new Builder instance
     */
    static Builder builder() {
        return new BuilderImpl();
    }

    /**
     * Returns the {@code Context} instance associated with the current
     * {@code Config} node that allows the application to access the last loaded
     * instance of the node or to request that the entire configuration be
     * reloaded.
     *
     * @return Context instance associated with specific Config node
     */
    default Context context() {
        //default implementation does not support changes
        return new Context() {
            @Override
            public Instant timestamp() {
                return Config.this.timestamp();
            }

            @Override
            public Config last() {
                return Config.this;
            }

            @Override
            public Config reload() {
                return Config.this;
            }
        };
    }

    /**
     * Returns when the configuration tree was created.
     * <p>
     * Each config node of single Config tree returns same timestamp.
     *
     * @return timestamp of created instance of whole configuration tree.
     * @see #context()
     * @see Context#timestamp()
     */
    Instant timestamp();

    /**
     * Returns the fully-qualified key of the {@code Config} node.
     * <p>
     * The fully-qualified key is a sequence of tokens derived from the name of
     * each node along the path from the config root to the current node. Tokens
     * are separated by {@code .} (the dot character). See {@link #name()} for
     * more information on the format of each token.
     *
     * @return current config node key
     * @see #name()
     */
    Key key();

    /**
     * Returns the last token of the fully-qualified key for the {@code Config}
     * node.
     * <p>
     * The name of a node is the last token in its fully-qualified key.
     * <p>
     * The exact format of the name depends on the {@code Type} of the
     * containing node:
     * <ul>
     * <li>from a {@link Type#OBJECT} node the token for a child is the
     * <strong>name of the object member</strong>;</li>
     * <li>from a {@link Type#LIST} node the token for a child is a zero-based
     * <strong>index of the element</strong>, an unsigned base-10 integer value
     * with no leading zeros.</li>
     * </ul>
     * <p>
     * The ABNF syntax of config key is:
     * <pre>{@code
     * config-key = *1( key-token *( "." key-token ) )
     *  key-token = *( unescaped / escaped )
     *  unescaped = %x00-2D / %x2F-7D / %x7F-10FFFF
     *            ; %x2E ('.') and %x7E ('~') are excluded from 'unescaped'
     *    escaped = "~" ( "0" / "1" )
     *            ; representing '~' and '.', respectively
     * }</pre>
     *
     * @return current config node key
     * @see #key()
     * @see Key#name()
     */
    default String name() {
        return key().name();
    }

    /**
     * Returns the single sub-node for the specified sub-key.
     * <p>
     * The format of the key is described on {@link #key()} method.
     *
     * @param key sub-key of requested sub-node
     * @return config node for specified sub-key, never returns {@code null}.
     * @see #get(Key)
     */
    default Config get(String key) {
        Objects.requireNonNull(key, "Key argument is null.");

        return get(ConfigKeyImpl.of(key));
    }

    /**
     * Returns the single sub-node for the specified sub-key.
     *
     * @param key sub-key of requested sub-node
     * @return config node for specified sub-key, never returns {@code null}.
     * @see #get(String)
     */
    Config get(Config.Key key);

    /**
     * Returns a copy of the {@code Config} node with no parent.
     * <p>
     * The returned node acts as a root node for the subtree below it. Its key
     * is the empty string; {@code ""}. The original config node is unchanged,
     * and the original and the copy point to the same children.
     * <p>
     * Consider the following configuration:
     * <pre>
     * app:
     *      name: Example 1
     *      page-size: 20
     * logging:
     *      app.level = INFO
     *      level = WARNING
     * </pre>
     * The {@code Config} instances {@code name1} and {@code name2} represents same data and
     * in fact refer to the same object:
     * <pre>
     * Config name1 = config
     *                  .get("app")
     *                  .get("name");
     * Config name2 = config
     *                  .get("app")
     *                  .detach()               //DETACHED node
     *                  .get("name");
     *
     * assert name1.asString() == "Example 1";
     * assert name2.asString() == "Example 1";  //DETACHED node
     * </pre>
     * The only difference is the key each node returns:
     * <pre>
     * assert name1.key() == "app.name";
     * assert name2.key() == "name";            //DETACHED node
     * </pre>
     * <p>
     * See {@link #asOptionalMap()} for example of config detaching.
     *
     * @return returns detached Config instance of same config node
     */
    Config detach();

    /**
     * Provides the {@link Type} of the {@code Config} node.
     *
     * @return the {@code Type} of the configuration node
     */
    Type type();

    /**
     * Returns {@code true} if the node exists, whether an object, a list, or a
     * value node.
     *
     * @return {@code true} if the node exists
     */
    default boolean exists() {
        return type().exists();
    }

    /**
     * Returns {@code true} if this node exists and is a leaf node (has no
     * children).
     * <p>
     * A leaf node has no nested configuration subtree and has a single value.
     *
     * @return {@code true} if the node is existing leaf node, {@code false}
     * otherwise.
     */
    default boolean isLeaf() {
        return type().isLeaf();
    }

    /**
     * Returns {@code true} if this configuration node has a direct value.
     * <p>
     * This may be a value node (e.g. a leaf) or object node or a list node
     * (e.g. a branch with value). The application can invoke methods such as
     * {@link #as(Class)} on nodes that have value.
     *
     * @return {@code true} if the node has direct value, {@code false} otherwise.
     */
    boolean hasValue();

    /**
     * Performs the given action with the config node if node
     * {@link #exists() exists}, otherwise does nothing.
     *
     * @param action the action to be performed if the node exists
     * @see #exists()
     * @see #type()
     */
    default void ifExists(Consumer<Config> action) {
        node().ifPresent(action);
    }

    /**
     * Performs the given action with the config node if the node
     * {@link #exists() exists}, otherwise performs the specified "missing"
     * action.
     *
     * @param action        the action to be performed, if a config node exists
     * @param missingAction the missing-based action to be performed, if a config node is {@link Type#MISSING missing}.
     * @see #exists()
     * @see #type()
     */
    default void ifExistsOrElse(Consumer<Config> action, Runnable missingAction) {
        OptionalHelper.from(node()).ifPresentOrElse(action, missingAction);
    }

    /**
     * <strong>Iterative deepening depth-first traversal</strong> of the node
     * and its subtree as a {@code Stream<Config>}.
     * <p>
     * If the config node does not exist or is a leaf the returned stream is
     * empty.
     * <p>
     * Depending on the structure of the configuration the returned stream can
     * deliver a mix of object, list, and leaf value nodes. The stream will
     * include and traverse through object members and list elements.
     *
     * @return stream of deepening depth-first sub-nodes
     */
    default Stream<Config> traverse() {
        return traverse((node) -> true);
    }

    /**
     * <strong>Iterative deepening depth-first traversal</strong> of the node
     * and its subtree as a {@code Stream<Config>}, qualified by the specified
     * predicate.
     * <p>
     * If the config node does not exist or is a leaf the returned stream is
     * empty.
     * <p>
     * Depending on the structure of the configuration the returned stream can
     * deliver a mix of object, list, and leaf value nodes. The stream will
     * include and traverse through object members and list elements.
     * <p>
     * The traversal continues as long as the specified {@code predicate}
     * evaluates to {@code true}. When the predicate evaluates to {@code false}
     * the node being traversed and its subtree will be excluded from the
     * returned {@code Stream<Config>}.
     *
     * @param predicate predicate evaluated on each visited {@code Config} node
     * to continue or stop visiting the node
     * @return stream of deepening depth-first subnodes
     */
    Stream<Config> traverse(Predicate<Config> predicate);

    //
    // accessors
    //

    /**
     * Returns a {@code String} value as {@link Optional} of configuration node if the node is {@link Type#VALUE}.
     * Returns a {@link Optional#empty() empty} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @return value as type instance as {@link Optional}, {@link Optional#empty() empty} in case the node is {@link Type#MISSING}
     * @throws ConfigMappingException in case the node is {@link Type#OBJECT} or {@link Type#LIST}
     * @see #asOptionalString()
     */
    Optional<String> value() throws ConfigMappingException;

    /**
     * Returns a {@code String} value as {@link Optional} of configuration node if the node is {@link Type#VALUE}.
     * Returns a {@link Optional#empty() empty} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @return value as type instance as {@link Optional}, {@link Optional#empty() empty} in case the node is {@link Type#MISSING}
     * @throws ConfigMappingException in case the node is {@link Type#OBJECT} or {@link Type#LIST}
     * @see #value()
     * @see #asOptionalStringSupplier()
     */
    default Optional<String> asOptionalString() throws ConfigMappingException {
        return value();
    }

    /**
     * Returns a {@link Supplier} of an {@link Optional Optional&lt;String&gt;} of the configuration node if the node is {@link
     * Type#VALUE}.
     * Supplier returns a {@link Optional#empty() empty} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @return a supplier of the value as an {@link Optional} typed instance, {@link Optional#empty() empty} in case the node is
     * {@link Type#MISSING}
     * @see #asOptionalString()
     */
    default Supplier<Optional<String>> asOptionalStringSupplier() {
        return () -> context().last().asOptionalString();
    }

    /**
     * Returns existing current config node as a {@link Optional} instance
     * or {@link Optional#empty()} in case of {@link Type#MISSING} node.
     *
     * @return current config node as a {@link Optional} instance
     * or {@link Optional#empty()} in case of {@link Type#MISSING} node.
     * @see #nodeSupplier()
     */
    default Optional<Config> node() {
        if (exists()) {
            return Optional.of(this);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns a {@link Supplier} of the configuration node as an {@link Optional Optional&lt;Config&gt;} or {@link
     * Optional#empty()} if the node is {@link Type#MISSING}.
     *
     * @return a {@link Supplier} of the configuration node as an {@link Optional Optional&lt;Config&gt;} or {@link
     * Optional#empty()} if the node is {@link Type#MISSING}
     * @see #asOptionalStringSupplier()
     * @see #node()
     */
    default Supplier<Optional<Config>> nodeSupplier() {
        return () -> context().last().node();
    }

    /**
     * Returns a list of child {@code Config} nodes if the node is {@link Type#OBJECT}.
     * Returns a list of element nodes if the node is {@link Type#LIST}.
     * Returns an {@link Optional#empty()} if the node is {@link Type#MISSING}.
     * Otherwise, if node is {@link Type#VALUE}, it throws {@link ConfigMappingException}.
     *
     * @return list of {@link Type#OBJECT} members, list of {@link Type#LIST} members
     * or empty list in case of {@link Type#MISSING}
     * @throws ConfigMappingException in case the node is {@link Type#VALUE}
     * @see #asOptionalNodeList
     */
    Optional<List<Config>> nodeList() throws ConfigMappingException;

    /**
     * Returns a list of child {@code Config} nodes if the node is {@link Type#OBJECT}.
     * Returns a list of element nodes if the node is {@link Type#LIST}.
     * Returns an empty list if the node is {@link Type#MISSING}.
     * Otherwise, if node is {@link Type#VALUE}, it throws {@link ConfigMappingException}.
     *
     * @return list of {@link Type#OBJECT} members, list of {@link Type#LIST} members
     * or empty list in case of {@link Type#MISSING}
     * @throws ConfigMappingException in case the node is {@link Type#VALUE}
     * @see #nodeList()
     * @see #asOptionalNodeListSupplier()
     */
    default Optional<List<Config>> asOptionalNodeList() throws ConfigMappingException {
        return nodeList();
    }

    /**
     * Returns a {@link Supplier} of a list of child {@code Config} nodes if the node is {@link Type#OBJECT}.
     * Returns a {@link Supplier} of a list of element nodes if the node is {@link Type#LIST}.
     * Returns a {@link Supplier} of an empty list if the node is {@link Type#MISSING}.
     * Otherwise, if node is {@link Type#VALUE}, a calling of {@link Supplier#get()} causes {@link ConfigMappingException}.
     *
     * @return a supplier of a list of {@link Type#OBJECT} members, a list of {@link Type#LIST} members
     * or an empty list in case of {@link Type#MISSING}
     * @see #nodeList()
     * @see #asOptionalNodeList()
     */
    default Supplier<Optional<List<Config>>> asOptionalNodeListSupplier() {
        return () -> context().last().asOptionalNodeList();
    }

    /**
     * Transform all leaf nodes (values) into Map instance.
     * <p>
     * Fully qualified key of config node is used as a key in returned Map.
     * {@link #detach() Detach} config node before transforming to Map in case you want to cut
     * current Config node key prefix.
     * <p>
     * Let's say we work with following configuration:
     * <pre>
     * app:
     *      name: Example 1
     *      page-size: 20
     * logging:
     *      app.level = INFO
     *      level = WARNING
     * </pre>
     * Map {@code app1} contains two keys: {@code app.name}, {@code app.page-size}.
     * <pre>{@code
     * Map<String, String> app1 = config.get("app").asOptionalMap().get();
     * }</pre>
     * {@link #detach() Detaching} {@code app} config node returns new Config instance with "reset" local root.
     * <pre>{@code
     * Map<String, String> app2 = config.get("app").detach().asOptionalMap().get();
     * }</pre>
     * Map {@code app2} contains two keys without {@code app} prefix: {@code name}, {@code page-size}.
     *
     * @return map as {@link Optional}, {@link Optional#empty() empty} in case of {@link Type#MISSING} node
     * @see #asMap()
     * @see #asMap(Map)
     * @see #traverse()
     * @see #detach()
     */
    Optional<Map<String, String>> asOptionalMap();

    /**
     * Returns a {@link Supplier} of a transformed leaf nodes (values) into a Map instance.
     * <p>
     * See {@link #asOptionalMap()} to more detailed about the result.
     *
     * @return a supplier of optional transformed leaf nodes to map
     * @see #asOptionalMap()
     */
    default Supplier<Optional<Map<String, String>>> asOptionalMapSupplier() {
        return () -> context().last().asOptionalMap();
    }

    /**
     * Returns typed value as a specified type.
     *
     * @param type type class
     * @param <T>  type
     * @return value as type instance as {@link Optional}, {@link Optional#empty() empty} if entry does not have set value
     * @throws ConfigMappingException in case of problem to map property value.
     */
    <T> Optional<T> asOptional(Class<? extends T> type) throws ConfigMappingException;

    /**
     * Returns a supplier of an optional typed value.
     *
     * @param type a type class
     * @param <T>  a type
     * @return a supplier of a value as type instance as {@link Optional}, {@link Optional#empty() empty} if entry does not
     * have set value
     * @see #asOptional(Class)
     */
    default <T> Supplier<Optional<T>> asOptionalSupplier(Class<? extends T> type) {
        return () -> context().last().asOptional(type);
    }

    /**
     * Returns list of specified type (single values as well as objects).
     *
     * @param type type class
     * @param <T>  type
     * @return typed list as {@link Optional}, {@link Optional#empty() empty} if entry does not have set value
     * @throws ConfigMappingException in case of problem to map property value.
     */
    <T> Optional<List<T>> asOptionalList(Class<? extends T> type) throws ConfigMappingException;

    /**
     * Returns a supplier of as optional list of typed values.
     *
     * @param type a type class
     * @param <T>  a type
     * @return a supplier of as optional list of typed values
     * @see #asOptionalList(Class)
     */
    default <T> Supplier<Optional<List<T>>> asOptionalListSupplier(Class<? extends T> type) {
        return () -> context().last().asOptionalList(type);
    }

    /**
     * Returns list of {@code String}.
     *
     * @return typed list as {@link Optional}, {@link Optional#empty() empty} if entry does not have set value
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default Optional<List<String>> asOptionalStringList() throws ConfigMappingException {
        return asOptionalList(String.class);
    }

    /**
     * Returns a supplier of as optional list of {@code String}.
     *
     * @return a supplier of as optional list of {@code String}
     * @see #asOptionalStringList()
     */
    default Supplier<Optional<List<String>>> asOptionalStringListSupplier() {
        return () -> context().last().asOptionalStringList();
    }

    /**
     * Returns typed value as a using specified type mapper.
     *
     * @param mapper type mapper
     * @param <T>    type
     * @return value as type instance as {@link Optional}, {@link Optional#empty() empty} if entry does not have set value
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> Optional<T> mapOptional(Function<String, ? extends T> mapper) throws ConfigMappingException {
        return mapOptional(ConfigMappers.wrap(mapper));
    }

    /**
     * Returns a supplier of an optional value, typed using a specified type mapper.
     *
     * @param mapper a type mapper
     * @param <T>    a type
     * @return a supplier of an optional value, typed using a specified type mapper
     * @see #mapOptional(Function)
     */
    default <T> Supplier<Optional<T>> mapOptionalSupplier(Function<String, ? extends T> mapper) {
        return () -> context().last().mapOptional(mapper);
    }

    /**
     * Returns typed value as a using specified type mapper.
     *
     * @param mapper configuration hierarchy mapper.
     * @param <T>    expected Java type
     * @return value as type instance as {@link Optional}, {@link Optional#empty() empty} if entry does not represent an existing
     * configuration node.
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> Optional<T> mapOptional(ConfigMapper<? extends T> mapper) throws ConfigMappingException {
        return type() == Type.MISSING ? Optional.empty() : Optional.of(mapper.apply(this));
    }

    /**
     * Returns a supplier of an optional value, typed using a specified type mapper.
     *
     * @param mapper a configuration mapper
     * @param <T>    an expected Java type
     * @return a supplier of an optional value, typed using a specified type mapper
     * @see #mapOptional(ConfigMapper)
     */
    default <T> Supplier<Optional<T>> mapOptionalSupplier(ConfigMapper<? extends T> mapper) {
        return () -> context().last().mapOptional(mapper);
    }

    /**
     * Maps the node {@link #value()} to {@link Optional}.
     *
     * @return value as a {@link Optional}
     * @throws ConfigMappingException in case it is not possible map the value
     */
    default Optional<Boolean> asOptionalBoolean() throws ConfigMappingException {
        return asOptional(Boolean.class);
    }

    /**
     * Returns a supplier to a value typed as an {@link Optional}.
     *
     * @return a supplier to a value typed as an {@code OptionalInt}
     * @see #asOptionalBoolean()
     */
    default Supplier<Optional<Boolean>> asOptionalBooleanSupplier() {
        return () -> context().last().asOptionalBoolean();
    }

    /**
     * Maps the node {@link #value()} to {@link OptionalInt}.
     *
     * @return value as a {@link OptionalInt}
     * @throws ConfigMappingException in case it is not possible map the value
     */
    default OptionalInt asOptionalInt() throws ConfigMappingException {
        return asOptional(Integer.class)
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }

    /**
     * Returns a supplier to a value typed as an {@link OptionalInt}.
     *
     * @return a supplier to a value typed as an {@code OptionalInt}
     * @see #asOptionalInt()
     */
    default Supplier<OptionalInt> asOptionalIntSupplier() {
        return () -> context().last().asOptionalInt();
    }

    /**
     * Maps the node {@link #value()} to {@link OptionalLong}.
     *
     * @return value as a {@link OptionalLong}
     * @throws ConfigMappingException in case it is not possible map the value
     */
    default OptionalLong asOptionalLong() throws ConfigMappingException {
        return asOptional(Long.class)
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }

    /**
     * Returns a supplier to a value typed as an {@link OptionalLong}.
     *
     * @return a supplier to a value typed as an {@code OptionalLong}
     * @see #asOptionalLong()
     */
    default Supplier<OptionalLong> asOptionalLongSupplier() {
        return () -> context().last().asOptionalLong();
    }

    /**
     * Maps the node {@link #value()} to {@link OptionalDouble}.
     *
     * @return value as a {@link OptionalDouble}
     * @throws ConfigMappingException in case it is not possible map the value
     */
    default OptionalDouble asOptionalDouble() throws ConfigMappingException {
        return asOptional(Double.class)
                .map(OptionalDouble::of)
                .orElseGet(OptionalDouble::empty);
    }

    /**
     * Returns a supplier to a value typed as an {@link OptionalDouble}.
     *
     * @return a supplier to a value typed as an {@code OptionalDouble}
     * @see #asOptionalDouble()
     */
    default Supplier<OptionalDouble> asOptionalDoubleSupplier() {
        return () -> context().last().asOptionalDouble();
    }

    /**
     * Returns typed list of type (single values as well as objects) provided by specified type mapper.
     *
     * @param mapper type mapper
     * @param <T>    single item type
     * @return typed list as {@link Optional}, {@link Optional#empty() empty} if entry does not have set value
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> Optional<List<T>> mapOptionalList(Function<String, ? extends T> mapper) throws ConfigMappingException {
        try {
            return asOptionalList(Config.class)
                    .map(configList -> configList.stream()
                            .map(config -> config.map(mapper)) //map every single list item
                            .collect(Collectors.toList()));
        } catch (ConfigMappingException ex) {
            throw new ConfigMappingException(key(),
                                             "Error to map list element from config node. " + ex.getLocalizedMessage(),
                                             ex);
        }
    }

    /**
     * Returns a supplier of an optional typed list of a type (single values as well as objects) provided by a specified type
     * mapper.
     *
     * @param mapper type mapper
     * @param <T>    single item type
     * @return a supplier of an optional typed list of a type (single values as well as objects) provided by a specified type
     * mapper
     * @see #mapOptionalList(Function)
     */
    default <T> Supplier<Optional<List<T>>> mapOptionalListSupplier(Function<String, ? extends T> mapper) {
        return () -> context().last().mapOptionalList(mapper);
    }

    /**
     * Returns typed list of type (single values as well as objects) provided by specified type mapper.
     *
     * @param mapper type mapper
     * @param <T>    single item type
     * @return typed list as {@link Optional}, {@link Optional#empty() empty} if entry does not have set value
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> Optional<List<T>> mapOptionalList(ConfigMapper<? extends T> mapper) throws ConfigMappingException {
        try {
            return asOptionalList(Config.class)
                    .map(configList -> configList.stream()
                            .map(mapper::apply) //map every single list item
                            .collect(Collectors.toList()));
        } catch (ConfigMappingException ex) {
            throw new ConfigMappingException(key(),
                                             "Error to map list element from config node. " + ex.getLocalizedMessage(),
                                             ex);
        }
    }

    /**
     * Returns a supplier of an optional typed list of a type (single values as well as objects) provided by a specified type
     * mapper.
     *
     * @param mapper type mapper
     * @param <T>    single item type
     * @return a supplier of an optional typed list of a type (single values as well as objects) provided by a specified type
     * mapper
     * @see #mapOptionalList(ConfigMapper)
     */
    default <T> Supplier<Optional<List<T>>> mapOptionalListSupplier(ConfigMapper<? extends T> mapper) {
        return () -> context().last().mapOptionalList(mapper);
    }

    /**
     * Returns typed value as a specified type.
     *
     * @param type type class
     * @param <T>  type
     * @return value as type instance
     * @throws MissingValueException  in case of the missing value for the key represented by this configuration.
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> T as(Class<? extends T> type) throws MissingValueException, ConfigMappingException {
        return asOptional(type)
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier of a typed value.
     *
     * @param type type class
     * @param <T>  type
     * @return a supplier of a typed value
     * @see #as(Class)
     */
    default <T> Supplier<T> asSupplier(Class<? extends T> type) {
        return () -> context().last().as(type);
    }

    /**
     * Returns typed value as a specified type.
     *
     * @param type         type class
     * @param <T>          type
     * @param defaultValue default value
     * @return value as type instance or default value
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> T as(Class<? extends T> type, T defaultValue) throws ConfigMappingException {
        return this.<T>asOptional(type)
                .orElse(defaultValue);
    }

    /**
     * Returns a supplier of a typed value.
     *
     * @param type         a type class
     * @param <T>          a type
     * @param defaultValue a default value
     * @return a supplier of a typed value or a default value
     * @see #as(Class, Object)
     */
    default <T> Supplier<T> asSupplier(Class<? extends T> type, T defaultValue) {
        return () -> context().last().as(type, defaultValue);
    }

    /**
     * Returns typed value as a using specified type mapper.
     *
     * @param mapper type mapper
     * @param <T>    type
     * @return value as type instance
     * @throws MissingValueException  in case of the missing value for the key represented by this configuration.
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> T map(Function<String, ? extends T> mapper) throws MissingValueException, ConfigMappingException {
        return mapOptional(mapper)
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier of a typed value, using a specified type mapper.
     *
     * @param mapper a type mapper
     * @param <T>    a type
     * @return a supplier of a typed value
     * @see #map(Function)
     */
    default <T> Supplier<T> mapSupplier(Function<String, ? extends T> mapper) {
        return () -> context().last().map(mapper);
    }

    /**
     * Returns typed value as a using specified config hierarchy mapper.
     *
     * @param mapper config hierarchy mapper
     * @param <T>    type
     * @return value as type instance
     * @throws MissingValueException  in case of the missing value for the key represented by this configuration.
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> T map(ConfigMapper<? extends T> mapper) throws MissingValueException, ConfigMappingException {
        return mapOptional(mapper)
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier of a typed value, using a specified type mapper.
     *
     * @param mapper a type mapper
     * @param <T>    a type
     * @return a supplier of a typed value
     * @see #map(ConfigMapper)
     */
    default <T> Supplier<T> mapSupplier(ConfigMapper<? extends T> mapper) {
        return () -> context().last().map(mapper);
    }

    /**
     * Returns typed value as a using specified type mapper.
     *
     * @param mapper       type mapper
     * @param <T>          type
     * @param defaultValue default value
     * @return value as type instance or default value
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> T map(Function<String, ? extends T> mapper, T defaultValue) throws ConfigMappingException {
        return this.<T>mapOptional(mapper)
                .orElse(defaultValue);
    }

    /**
     * Returns a supplier of a typed value, using a specified type mapper or a default value.
     *
     * @param mapper       a type mapper
     * @param <T>          a type
     * @param defaultValue a default value
     * @return a supplier of a typed value or default value
     * @see #map(Function, Object)
     */
    default <T> Supplier<T> mapSupplier(Function<String, ? extends T> mapper, T defaultValue) {
        return () -> context().last().map(mapper, defaultValue);
    }

    /**
     * Returns typed value as a using specified config hierarchy mapper.
     *
     * @param mapper       config hierarchy mapper
     * @param defaultValue default value
     * @param <T>          type
     * @return value as type instance or default value
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> T map(ConfigMapper<? extends T> mapper, T defaultValue) throws ConfigMappingException {
        return this.<T>mapOptional(mapper)
                .orElse(defaultValue);
    }

    /**
     * Returns a supplier of a typed value, using a specified type mapper or a default value.
     *
     * @param mapper       a type mapper
     * @param <T>          a type
     * @param defaultValue a default value
     * @return a supplier of a typed value or default value
     * @see #map(ConfigMapper, Object)
     */
    default <T> Supplier<T> mapSupplier(ConfigMapper<? extends T> mapper, T defaultValue) {
        return () -> context().last().map(mapper, defaultValue);
    }

    /**
     * Returns list of specified type.
     *
     * @param type type class
     * @param <T>  type
     * @return a typed list with values
     * @throws MissingValueException  in case of the missing value for the key represented by this configuration.
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> List<T> asList(Class<? extends T> type) throws MissingValueException, ConfigMappingException {
        return this.<T>asOptionalList(type)
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier of a list of specified type.
     *
     * @param type a type class
     * @param <T>  a type
     * @return a supplier of a typed list of values
     * @see #asList(Class)
     */
    default <T> Supplier<List<T>> asListSupplier(Class<? extends T> type) {
        return () -> context().last().asList(type);
    }

    /**
     * Returns list of specified type.
     *
     * @param type         type class
     * @param <T>          type
     * @param defaultValue default value
     * @return a typed list or default value
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> List<T> asList(Class<? extends T> type, List<T> defaultValue) throws ConfigMappingException {
        return this.<T>asOptionalList(type).orElse(defaultValue);
    }

    /**
     * Returns a supplier of a list of a specified type or a default value.
     *
     * @param type         a type class
     * @param <T>          a type
     * @param defaultValue a default value
     * @return a supplier of a typed list of values or a default value
     * @see #asList(Class, List)
     */
    default <T> Supplier<List<T>> asListSupplier(Class<? extends T> type, List<T> defaultValue) {
        return () -> context().last().asList(type, defaultValue);
    }

    /**
     * Returns typed list of type provided by specified type mapper.
     *
     * @param mapper type mapper
     * @param <T>    mapped Java type
     * @return a typed list of values or default value
     * @throws MissingValueException  in case of the missing value for the key represented by this configuration.
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> List<T> mapList(ConfigMapper<? extends T> mapper) throws MissingValueException, ConfigMappingException {
        return this.<T>mapOptionalList(mapper)
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier of a list of a type provided by a specified type mapper.
     *
     * @param mapper a type class
     * @param <T>    a type
     * @return a supplier of a list of a type provided by a specified type mapper
     * @see #mapList(ConfigMapper)
     */
    default <T> Supplier<List<T>> mapListSupplier(ConfigMapper<? extends T> mapper) {
        return () -> context().last().mapList(mapper);
    }

    /**
     * Returns typed list of type provided by specified type mapper.
     *
     * @param mapper       type mapper
     * @param <T>          mapped Java type
     * @param defaultValue default value
     * @return a typed list of values or default value
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> List<T> mapList(ConfigMapper<? extends T> mapper, List<T> defaultValue) throws ConfigMappingException {
        return this.<T>mapOptionalList(mapper).orElse(defaultValue);
    }

    /**
     * Returns a supplier of a list of a type provided by a specified type mapper or a default value.
     *
     * @param mapper       a type class
     * @param <T>          a type
     * @param defaultValue a default value
     * @return a supplier of a list of a type provided by a specified type mapper or a default value
     * @see #mapList(ConfigMapper, List)
     */
    default <T> Supplier<List<T>> mapListSupplier(ConfigMapper<? extends T> mapper, List<T> defaultValue) {
        return () -> context().last().mapList(mapper, defaultValue);
    }

    /**
     * Returns typed list of type provided by specified config hierarchy mapper.
     *
     * @param mapper config hierarchy  mapper
     * @param <T>    mapped Java type
     * @return a typed list of values or default value
     * @throws MissingValueException  in case of the missing value for the key represented by this configuration.
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> List<T> mapList(Function<String, ? extends T> mapper) throws MissingValueException, ConfigMappingException {
        return this.<T>mapOptionalList(mapper)
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier of a list of a type provided by a specified type mapper.
     *
     * @param mapper a type class
     * @param <T>    a type
     * @return a supplier of a list of a type provided by a specified type mapper
     * @see #mapList(Function)
     */
    default <T> Supplier<List<T>> mapListSupplier(Function<String, ? extends T> mapper) {
        return () -> context().last().mapList(mapper);
    }

    /**
     * Returns typed list of type provided by specified config hierarchy  mapper.
     *
     * @param mapper       config hierarchy  mapper
     * @param <T>          mapped Java type
     * @param defaultValue default value
     * @return a typed list of values or default value
     * @throws ConfigMappingException in case of problem to map property value.
     */
    default <T> List<T> mapList(Function<String, ? extends T> mapper, List<T> defaultValue) throws ConfigMappingException {
        return this.<T>mapOptionalList(mapper)
                .orElse(defaultValue);
    }

    /**
     * Returns a supplier of a list of a type provided by a specified type mapper or a default value.
     *
     * @param mapper       a type class
     * @param <T>          a type
     * @param defaultValue a default value
     * @return a supplier of a list of a type provided by a specified type mapper or a default value
     * @see #mapList(Function, List)
     */
    default <T> Supplier<List<T>> mapListSupplier(Function<String, ? extends T> mapper, List<T> defaultValue) {
        return () -> context().last().mapList(mapper, defaultValue);
    }

    /**
     * Returns a {@code String} value of configuration node if the node is {@link Type#VALUE}.
     * Throws {@link MissingValueException} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @return single value if the node is {@link Type#VALUE}.
     * @throws MissingValueException  in case the node is {@link Type#MISSING}.
     * @throws ConfigMappingException in case the node is {@link Type#OBJECT} or {@link Type#LIST}
     */
    default String asString() throws MissingValueException, ConfigMappingException {
        return value()
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier of a {@code String} value of this configuration node.
     * <p>
     * Calling {@link Supplier#get()} returns {@code String} value when the node is {@link Type#VALUE}.
     * Throws {@link MissingValueException} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @return a supplier of the value if the node is {@link Type#VALUE}.
     * @see #asString()
     */
    default Supplier<String> asStringSupplier() {
        return () -> context().last().asString();
    }

    /**
     * Returns a {@code String} value of configuration node if the node is {@link Type#VALUE}.
     * Returns {@code defaultValue} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @param defaultValue default value
     * @return single value if the node is {@link Type#VALUE} or {@code defaultValue} if the node is {@link Type#MISSING}.
     * @throws ConfigMappingException in case the node is {@link Type#OBJECT} or {@link Type#LIST}
     */
    default String asString(String defaultValue) throws ConfigMappingException {
        return value()
                .orElse(defaultValue);
    }

    /**
     * Returns a supplier of a {@code String} value of this configuration node or a default value.
     * <p>
     * Calling {@link Supplier#get()} returns {@code String} value when the node is {@link Type#VALUE}.
     * Returns a {@code defaultValue} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @param defaultValue a default value
     * @return a supplier of the value if the node is {@link Type#VALUE} or {@code defaultValue}.
     * @see #asString(String)
     */
    default Supplier<String> asStringSupplier(String defaultValue) {
        return () -> context().last().asString(defaultValue);
    }

    /**
     * Returns a {@code boolean} value of configuration node if the node is {@link Type#VALUE}
     * and original {@link #value} can be mapped to.
     * Throws {@link MissingValueException} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @return single value if the node is {@link Type#VALUE} and can be mapped
     * @throws MissingValueException  in case the node is {@link Type#MISSING}.
     * @throws ConfigMappingException in case the node is {@link Type#OBJECT} or {@link Type#LIST}, or value cannot be mapped
     *                                to.
     */
    default boolean asBoolean() throws MissingValueException, ConfigMappingException {
        return asOptional(Boolean.class)
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier of a {@code Boolean} value of this configuration node.
     * <p>
     * Calling {@link Supplier#get()} returns {@code Boolean} value when the node is {@link Type#VALUE}.
     * Throws {@link MissingValueException} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @return a supplier of the value if the node is {@link Type#VALUE}.
     * @see #asBoolean()
     */
    default Supplier<Boolean> asBooleanSupplier() {
        return () -> context().last().asBoolean();
    }

    /**
     * Returns a {@code boolean} value of configuration node if the node is {@link Type#VALUE}
     * and original {@link #value} can be mapped to.
     * Returns {@code defaultValue} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @param defaultValue a default value
     * @return single value if the node is {@link Type#VALUE} or {@code defaultValue} if the node is {@link Type#MISSING}.
     * @throws ConfigMappingException in case the node is {@link Type#OBJECT} or {@link Type#LIST}, or value cannot be mapped
     *                                to.
     */
    default boolean asBoolean(boolean defaultValue) throws ConfigMappingException {
        return asOptional(Boolean.class)
                .orElse(defaultValue);
    }

    /**
     * Returns a supplier of a {@code Boolean} value of this configuration node or a default value.
     * <p>
     * Calling {@link Supplier#get()} returns {@code Boolean} value when the node is {@link Type#VALUE}.
     * Returns a {@code defaultValue} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @param defaultValue a default value
     * @return a supplier of the value if the node is {@link Type#VALUE} or {@code defaultValue}.
     * @see #asBoolean(boolean)
     */
    default Supplier<Boolean> asBooleanSupplier(boolean defaultValue) {
        return () -> context().last().asBoolean(defaultValue);
    }

    /**
     * Returns a {@code int} value of configuration node if the node is {@link Type#VALUE}
     * and original {@link #value} can be mapped to.
     * Throws {@link MissingValueException} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @return single value if the node is {@link Type#VALUE} and can be mapped
     * @throws MissingValueException  in case the node is {@link Type#MISSING}.
     * @throws ConfigMappingException in case the node is {@link Type#OBJECT} or {@link Type#LIST}, or value cannot be mapped
     *                                to.
     */
    default int asInt() throws MissingValueException, ConfigMappingException {
        return asOptionalInt()
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier of a {@code Integer} value of this configuration node.
     * <p>
     * Calling {@link Supplier#get()} returns {@code Integer} value when the node is {@link Type#VALUE}.
     * Throws {@link MissingValueException} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @return a supplier of the value if the node is {@link Type#VALUE}.
     * @see #asInt()
     */
    default Supplier<Integer> asIntSupplier() {
        return () -> context().last().asInt();
    }

    /**
     * Returns a {@code int} value of configuration node if the node is {@link Type#VALUE}
     * and original {@link #value} can be mapped to.
     * Returns {@code defaultValue} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @param defaultValue a default value
     * @return single value if the node is {@link Type#VALUE} or {@code defaultValue} if the node is {@link Type#MISSING}.
     * @throws ConfigMappingException in case the node is {@link Type#OBJECT} or {@link Type#LIST}, or value cannot be mapped
     *                                to.
     */
    default int asInt(int defaultValue) throws ConfigMappingException {
        return asOptionalInt()
                .orElse(defaultValue);
    }

    /**
     * Returns a supplier of a {@code Integer} value of this configuration node or a default value.
     * <p>
     * Calling {@link Supplier#get()} returns {@code Integer} value when the node is {@link Type#VALUE}.
     * Returns a {@code defaultValue} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @param defaultValue a default value
     * @return a supplier of the value if the node is {@link Type#VALUE} or {@code defaultValue}.
     * @see #asInt(int)
     */
    default Supplier<Integer> asIntSupplier(int defaultValue) {
        return () -> context().last().asInt(defaultValue);
    }

    /**
     * Returns a {@code long} value of configuration node if the node is {@link Type#VALUE}
     * and original {@link #value} can be mapped to.
     * Throws {@link MissingValueException} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @return single value if the node is {@link Type#VALUE} and can be mapped
     * @throws MissingValueException  in case the node is {@link Type#MISSING}.
     * @throws ConfigMappingException in case the node is {@link Type#OBJECT} or {@link Type#LIST}, or value cannot be mapped
     *                                to.
     */
    default long asLong() throws MissingValueException, ConfigMappingException {
        return asOptionalLong()
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier of a {@code Long} value of this configuration node.
     * <p>
     * Calling {@link Supplier#get()} returns {@code Long} value when the node is {@link Type#VALUE}.
     * Throws {@link MissingValueException} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @return a supplier of the value if the node is {@link Type#VALUE}.
     * @see #asLong()
     */
    default Supplier<Long> asLongSupplier() {
        return () -> context().last().asLong();
    }

    /**
     * Returns a {@code long} value of configuration node if the node is {@link Type#VALUE}
     * and original {@link #value} can be mapped to.
     * Returns a {@code defaultValue} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @param defaultValue a default value
     * @return single value if the node is {@link Type#VALUE} or {@code defaultValue} if the node is {@link Type#MISSING}.
     * @throws ConfigMappingException in case the node is {@link Type#OBJECT} or {@link Type#LIST}, or value cannot be mapped
     *                                to.
     */
    default long asLong(long defaultValue) throws ConfigMappingException {
        return asOptionalLong()
                .orElse(defaultValue);
    }

    /**
     * Returns a supplier of a {@code Long} value of this configuration node or a default value.
     * <p>
     * Calling {@link Supplier#get()} returns {@code Long} value when the node is {@link Type#VALUE}.
     * Returns a {@code defaultValue} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @param defaultValue a default value
     * @return a supplier of the value if the node is {@link Type#VALUE} or {@code defaultValue}.
     * @see #asLong(long)
     */
    default Supplier<Long> asLongSupplier(long defaultValue) {
        return () -> context().last().asLong(defaultValue);
    }

    /**
     * Returns a {@code double} value of configuration node if the node is {@link Type#VALUE}
     * and original {@link #value} can be mapped to.
     * Throws {@link MissingValueException} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @return single value if the node is {@link Type#VALUE} and can be mapped
     * @throws MissingValueException  in case the node is {@link Type#MISSING}.
     * @throws ConfigMappingException in case the node is {@link Type#OBJECT} or {@link Type#LIST}, or value cannot be mapped
     *                                to.
     */
    default double asDouble() throws MissingValueException, ConfigMappingException {
        return asOptionalDouble()
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier of a {@code Double} value of this configuration node.
     * <p>
     * Calling {@link Supplier#get()} returns {@code Double} value when the node is {@link Type#VALUE}.
     * Throws {@link MissingValueException} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @return a supplier of the value if the node is {@link Type#VALUE}.
     * @see #asDouble()
     */
    default Supplier<Double> asDoubleSupplier() {
        return () -> context().last().asDouble();
    }

    /**
     * Returns a {@code double} value of configuration node if the node is {@link Type#VALUE}
     * and original {@link #value} can be mapped to.
     * Returns a {@code defaultValue} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @param defaultValue a default value
     * @return single value if the node is {@link Type#VALUE} or {@code defaultValue} if the node is {@link Type#MISSING}.
     * @throws ConfigMappingException in case the node is {@link Type#OBJECT} or {@link Type#LIST}, or value cannot be mapped
     *                                to.
     */
    default double asDouble(double defaultValue) throws ConfigMappingException {
        return asOptionalDouble()
                .orElse(defaultValue);
    }

    /**
     * Returns a supplier of a {@code Double} value of this configuration node or a default value.
     * <p>
     * Calling {@link Supplier#get()} returns {@code Double} value when the node is {@link Type#VALUE}.
     * Returns a {@code defaultValue} if the node is {@link Type#MISSING} type.
     * Otherwise, if node is {@link Type#OBJECT} or {@link Type#LIST}, it throws {@link ConfigMappingException}.
     *
     * @param defaultValue a default value
     * @return a supplier of the value if the node is {@link Type#VALUE} or {@code defaultValue}.
     * @see #asDouble(double)
     */
    default Supplier<Double> asDoubleSupplier(double defaultValue) {
        return () -> context().last().asDouble(defaultValue);
    }

    /**
     * Returns a list of {@code String}s mapped from {@link Type#LIST} or {@link Type#OBJECT} {@link #nodeList() nodes}.
     *
     * @return a list of {@code String}s of it is possible to map elementary {@link #nodeList() sub-nodes} into {@code String}
     * @throws MissingValueException  in case the node is {@link Type#MISSING}.
     * @throws ConfigMappingException in case the node is {@link Type#VALUE}, or list of nodes cannot be mapped
     *                                to list of {@code String}s
     */
    default List<String> asStringList() throws MissingValueException, ConfigMappingException {
        return asOptionalList(String.class)
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier to a list of {@code String}s mapped from {@link Type#LIST} or {@link Type#OBJECT} {@link #nodeList()
     * nodes}.
     * <p>
     * Calling {@link Supplier#get()} throws {@link MissingValueException} if this node is {@link Type#MISSING} or {@link
     * ConfigMappingException} if this node is {@link Type#VALUE} or a list of nodes cannot be mapped to list of {@code String}s.
     *
     * @return a supplier of a list of {@code String}s of it is possible to map elementary {@link #nodeList() sub-nodes} into
     * {@code String}
     * @see #asStringList()
     */
    default Supplier<List<String>> asStringListSupplier() {
        return () -> context().last().asStringList();
    }

    /**
     * Returns a list of {@code String}s mapped from {@link Type#LIST} or {@link Type#OBJECT} {@link #nodeList() nodes}.
     * Returns a {@code defaultValue} if the node is {@link Type#MISSING} type.
     *
     * @param defaultValue a default value
     * @return a list of {@code String}s of it is possible to map elementary {@link #nodeList() sub-nodes} into {@code String}
     * or {@code defaultValue} if the node is {@link Type#MISSING}.
     * @throws ConfigMappingException in case the node is {@link Type#VALUE}, or list of nodes cannot be mapped
     *                                to list of {@code String}s
     */
    default List<String> asStringList(List<String> defaultValue) throws ConfigMappingException {
        return asOptionalList(String.class)
                .orElse(defaultValue);
    }

    /**
     * Returns a supplier to a list of {@code String}s mapped from {@link Type#LIST} or {@link Type#OBJECT} {@link #nodeList()
     * nodes} or a default value.
     * <p>
     * Calling {@link Supplier#get()} throws {@link ConfigMappingException} if this node is {@link Type#VALUE} or a list of nodes
     * cannot be mapped to list of {@code String}s.
     *
     * @param defaultValue a default value
     * @return a supplier of a list of {@code String}s of it is possible to map elementary {@link #nodeList() sub-nodes} into
     * {@code String} or a {@code defaultValue}
     * @see #asStringList(List)
     */
    default Supplier<List<String>> asStringListSupplier(List<String> defaultValue) {
        return () -> context().last().asStringList(defaultValue);
    }

    /**
     * Returns a list of child {@code Config} nodes if the node is {@link Type#OBJECT}.
     * Returns a list of element nodes if the node is {@link Type#LIST}.
     * Throws {@link MissingValueException} if the node is {@link Type#MISSING}.
     * Otherwise, if node is {@link Type#VALUE}, it throws {@link ConfigMappingException}.
     *
     * @return a list of {@link Type#OBJECT} members or a list of {@link Type#LIST} members
     * @throws MissingValueException  in case the node is {@link Type#MISSING}.
     * @throws ConfigMappingException in case the node is {@link Type#VALUE}
     */
    default List<Config> asNodeList() throws MissingValueException, ConfigMappingException {
        return nodeList()
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier of a list of child nodes when this node is {@link Type#OBJECT} or a list of element nodes for {@link
     * Type#LIST}.
     * <p>
     * Calling {@link Supplier#get()} may throw {@link MissingValueException} if this node is {@link Type#MISSING} or {@link
     * ConfigMappingException} for nodes of type {@link Type#VALUE}.
     *
     * @return a supplier of a list of {@link Type#OBJECT} members or a list of {@link Type#LIST} members
     * @see #asNodeList()
     */
    default Supplier<List<Config>> asNodeListSupplier() {
        return () -> context().last().asNodeList();
    }

    /**
     * Returns a list of child {@code Config} nodes if the node is {@link Type#OBJECT}.
     * Returns a list of element nodes if the node is {@link Type#LIST}.
     * Returns a {@code defaultValue} if the node is {@link Type#MISSING}.
     * Otherwise, if node is {@link Type#VALUE}, it throws {@link ConfigMappingException}.
     *
     * @param defaultValue a default value
     * @return list of {@link Type#OBJECT} members, list of {@link Type#LIST} members
     * or {@code defaultValue} in case of {@link Type#MISSING}
     * @throws ConfigMappingException in case the node is {@link Type#VALUE}
     */
    default List<Config> asNodeList(List<Config> defaultValue) throws ConfigMappingException {
        return nodeList()
                .orElse(defaultValue);
    }

    /**
     * Returns a supplier of a list of child nodes when this node is {@link Type#OBJECT}, a list of element nodes for {@link
     * Type#LIST} or {@code defaultValue} if this node is {@link Type#MISSING}.
     * <p>
     * Calling {@link Supplier#get()} may throw {@link ConfigMappingException} for nodes of type {@link Type#VALUE}.
     *
     * @param defaultValue a default value
     * @return a supplier of a list of {@link Type#OBJECT} members or a list of {@link Type#LIST} members
     * or {@code defaultValue} in case of {@link Type#MISSING}
     * @see #asNodeList(List)
     */
    default Supplier<List<Config>> asNodeListSupplier(List<Config> defaultValue) {
        return () -> context().last().asNodeList(defaultValue);
    }

    /**
     * Transform all leaf nodes (values) into Map instance.
     * <p>
     * Fully qualified key of config node is used as a key in returned Map.
     * {@link #detach() Detach} config node before transforming to Map in case you want to cut
     * current Config node key prefix.
     * <p>
     * Let's say we work with following configuration:
     * <pre>
     * app:
     *      name: Example 1
     *      page-size: 20
     * logging:
     *      app.level = INFO
     *      level = WARNING
     * </pre>
     * Map {@code app1} contains two keys: {@code app.name}, {@code app.page-size}.
     * <pre>{@code
     * Map<String, String> app1 = config.get("app").asMap();
     * }</pre>
     * {@link #detach() Detaching} {@code app} config node returns new Config instance with "reset" local root.
     * <pre>{@code
     * Map<String, String> app2 = config.get("app").detach().asMap();
     * }</pre>
     * Map {@code app2} contains two keys without {@code app} prefix: {@code name}, {@code page-size}.
     *
     * @return new Map instance that contains all config leaf node values
     * @throws MissingValueException in case the node is {@link Type#MISSING}.
     * @see #asOptionalMap()
     * @see #asMap(Map)
     * @see #traverse()
     * @see #detach()
     */
    default Map<String, String> asMap() throws MissingValueException {
        return asOptionalMap()
                .orElseThrow(MissingValueException.supplierForKey(key()));
    }

    /**
     * Returns a supplier of a map with transformed leaf nodes.
     * <p>
     * For detail see {@link #asMap()}.
     *
     * @return a supplier of a map with transformed leaf nodes
     * @see #asMap()
     */
    default Supplier<Map<String, String>> asMapSupplier() {
        return () -> context().last().asMap();
    }

    /**
     * Transform all leaf nodes (values) into Map instance.
     * <p>
     * Fully qualified key of config node is used as a key in returned Map.
     * {@link #detach() Detach} config node before transforming to Map in case you want to cut
     * current Config node key prefix.
     * <p>
     * Let's say we work with following configuration:
     * <pre>
     * app:
     *      name: Example 1
     *      page-size: 20
     * logging:
     *      app.level = INFO
     *      level = WARNING
     * </pre>
     * Map {@code app1} contains two keys: {@code app.name}, {@code app.page-size}.
     * <pre>{@code
     * Map<String, String> app1 = config.get("app").asMap(CollectionsHelper.mapOf());
     * }</pre>
     * {@link #detach() Detaching} {@code app} config node returns new Config instance with "reset" local root.
     * <pre>{@code
     * Map<String, String> app2 = config.get("app").detach().asMap(CollectionsHelper.mapOf());
     * }</pre>
     * Map {@code app2} contains two keys without {@code app} prefix: {@code name}, {@code page-size}.
     *
     * @param defaultValue a default value
     * @return new Map instance that contains all config leaf node values or {@code defaultValue} in case of {@link Type#MISSING}
     * @see #asOptionalMap()
     * @see #asMap()
     * @see #traverse()
     * @see #detach()
     */
    default Map<String, String> asMap(Map<String, String> defaultValue) {
        return asOptionalMap()
                .orElse(defaultValue);
    }

    /**
     * Returns a supplier of a map with transformed leaf nodes or a default value if this node is {@link Type#MISSING}.
     * <p>
     * For detail see {@link #asMap(Map)}.
     *
     * @param defaultValue a default value
     * @return a supplier of a map with transformed leaf nodes
     * @see #asMap(Map)
     */
    default Supplier<Map<String, String>> asMapSupplier(Map<String, String> defaultValue) {
        return () -> context().last().asMap(defaultValue);
    }

    //
    // config changes
    //

    /**
     * Allows to subscribe on change on whole Config as well as on particular Config node.
     * <p>
     * A user can subscribe on root Config node and than will be notified on any change of Configuration.
     * You can also subscribe on any sub-node, i.e. you will receive notification events just about sub-configuration.
     * No matter how much the sub-configuration has changed you will receive just one notification event that is associated
     * with a node you are subscribed on.
     * If a user subscribes on older instance of Config and ones has already been published the last one is automatically
     * submitted to new-subscriber.
     * <p>
     * The {@code Config} notification support is based on {@link ConfigSource#changes() ConfigSource changes support}.
     * <p>
     * Method {@link Flow.Subscriber#onError(Throwable)} is never called.
     * Method {@link Flow.Subscriber#onComplete()} is called in case an associated
     * {@link ConfigSource#changes() ConfigSource's changes Publisher} signals {@code onComplete} as well.
     * <p>
     * Note: It does not matter what instance version of Config (related to single {@link Builder} initialization)
     * a user subscribes on. It is enough to subscribe just on single (e.g. on the first) Config instance.
     * There is no added value to subscribe again on new Config instance.
     *
     * @return {@link Flow.Publisher} to be subscribed in. Never returns {@code null}.
     * @see Config#onChange(Function)
     */
    @Deprecated
    default Flow.Publisher<Config> changes() {
        return Flow.Subscriber::onComplete;
    }

    /**
     * Directly subscribes {@code onNextFunction} function on change on whole Config or on particular Config node.
     * <p>
     * It automatically creates {@link ConfigHelper#subscriber(Function) Flow.Subscriber} that will
     * delegate {@link Flow.Subscriber#onNext(Object)} to specified {@code onNextFunction} function.
     * Created subscriber automatically {@link Flow.Subscription#request(long) requests} {@link Long#MAX_VALUE all events}
     * in it's {@link Flow.Subscriber#onSubscribe(Flow.Subscription)} method.
     * Function {@code onNextFunction} returns {@code false} in case user wants to {@link Flow.Subscription#cancel() cancel}
     * current subscription.
     * <p>
     * A user can subscribe on root Config node and than will be notified on any change of Configuration.
     * You can also subscribe on any sub-node, i.e. you will receive notification events just about sub-configuration.
     * No matter how much the sub-configuration has changed you will receive just one notification event that is associated
     * with a node you are subscribed on.
     * If a user subscribes on older instance of Config and ones has already been published the last one is automatically
     * submitted to new-subscriber.
     * <p>
     * The {@code Config} notification support is based on {@link ConfigSource#changes() ConfigSource changes support}.
     * <p>
     * Note: It does not matter what instance version of Config (related to single {@link Builder} initialization)
     * a user subscribes on. It is enough to subscribe just on single (e.g. on the first) Config instance.
     * There is no added value to subscribe again on new Config instance.
     *
     * @param onNextFunction {@link Flow.Subscriber#onNext(Object)} functionality
     * @see Config#changes()
     * @see ConfigHelper#subscriber(Function)
     */
    default void onChange(Function<Config, Boolean> onNextFunction) {
        changes().subscribe(ConfigHelper.subscriber(onNextFunction));
    }

    /**
     * Object represents fully-qualified key of config node.
     * <p>
     * Fully-qualified key is list of key tokens separated by {@code .} (dot character).
     * Depending on context the key token is evaluated one by one:
     * <ul>
     * <li>in {@link Type#OBJECT} node the token represents a <strong>name of object member</strong>;</li>
     * <li>in {@link Type#LIST} node the token represents an zero-based <strong>index of list element</strong>,
     * an unsigned base-10 integer value, leading zeros are not allowed.</li>
     * </ul>
     * <p>
     * The ABNF syntax of config key is:
     * <pre>{@code
     * config-key = *1( key-token *( "." key-token ) )
     *  key-token = *( unescaped / escaped )
     *  unescaped = %x00-2D / %x2F-7D / %x7F-10FFFF
     *            ; %x2E ('.') and %x7E ('~') are excluded from 'unescaped'
     *    escaped = "~" ( "0" / "1" )
     *            ; representing '~' and '.', respectively
     * }</pre>
     *
     * @see Config#key()
     */
    interface Key extends Comparable<Key> {

        /**
         * Returns instance of Key that represents key of parent config node.
         * <p>
         * If the key represents root config node it returns {@code null}.
         *
         * @return key that represents key of parent config node.
         * @see #isRoot()
         */
        Key parent();

        /**
         * Returns {@code true} in case the key represents root config node,
         * otherwise it returns {@code false}.
         *
         * @return {@code true} in case the key represents root node, otherwise {@code false}.
         * @see #parent()
         */
        default boolean isRoot() {
            return parent() == null;
        }

        /**
         * Returns the name of Config node.
         * <p>
         * The name of a node is the last token in fully-qualified key.
         * Depending on context the name is evaluated one by one:
         * <ul>
         * <li>in {@link Type#OBJECT} node the name represents a <strong>name of object member</strong>;</li>
         * <li>in {@link Type#LIST} node the name represents an zero-based <strong>index of list element</strong>,
         * an unsigned base-10 integer value, leading zeros are not allowed.</li>
         * </ul>
         *
         * @return name of config node
         * @see Config#name()
         */
        String name();

        /**
         * Returns formatted fully-qualified key.
         *
         * @return formatted fully-qualified key.
         */
        @Override
        String toString();

        /**
         * Creates new instance of Key for specified {@code key} literal.
         * <p>
         * Empty literal means root node.
         * Character dot ('.') has special meaning - it separates fully-qualified key by key tokens (node names).
         *
         * @param key formatted fully-qualified key.
         * @return Key instance representing specified fully-qualified key.
         */
        static Key of(String key) {
            return ConfigKeyImpl.of(key);
        }

        /**
         * Escape {@code '~'} to {@code ~0} and {@code '.'} to {@code ~1} in specified name.
         *
         * @param name name to be escaped
         * @return escaped name
         */
        static String escapeName(String name) {
            if (!name.contains("~") && !name.contains(".")) {
                return name;
            }
            StringBuilder sb = new StringBuilder();
            char[] chars = name.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                char ch = chars[i];
                if (ch == '~') {
                    sb.append("~0");
                } else if (ch == '.') {
                    sb.append("~1");
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }

        /**
         * Unescape {@code ~0} to {@code '~'} and {@code ~1} to {@code '.'} in specified escaped name.
         *
         * @param escapedName escaped name
         * @return unescaped name
         */
        static String unescapeName(String escapedName) {
            return escapedName.replaceAll("~1", ".")
                    .replaceAll("~0", "~");
        }
    }

    //
    // enum node Type
    //

    /**
     * Configuration node types.
     */
    enum Type {
        /**
         * Config node is an object of named members
         * ({@link #VALUE values}, {@link #LIST lists} or other {@link #OBJECT objects}).
         */
        OBJECT(true, false),
        /**
         * Config node is a list of indexed elements
         * ({@link #VALUE values}, {@link #OBJECT objects} or other {@link #LIST lists}).
         */
        LIST(true, false),
        /**
         * Config node is a leaf {@code String}-based single value,
         * member of {@link #OBJECT object} or {@link #LIST list} element.
         */
        VALUE(true, true),
        /**
         * Config node does not exists.
         */
        MISSING(false, false);

        private boolean exists;
        private boolean isLeaf;

        Type(boolean exists, boolean isLeaf) {
            this.exists = exists;
            this.isLeaf = isLeaf;
        }

        /**
         * Returns {@code true} if the node exists, either as an object, a list or as a value node.
         *
         * @return {@code true} if the node exists
         */
        public boolean exists() {
            return exists;
        }

        /**
         * Returns {@code true} if this configuration node is existing a value node.
         * <p>
         * Leaf configuration node does not contain any nested configuration sub-trees,
         * but only a single associated value.
         *
         * @return {@code true} if the node is existing leaf node, {@code false} otherwise.
         */
        public boolean isLeaf() {
            return isLeaf;
        }
    }

    //
    // interface Builder
    //

    /**
     * {@link Config} Builder.
     * <p>
     * A factory for a {@code Config} object.
     * <p>
     * The application can set the following characteristics:
     * <ul>
     * <li>{@code overrides} - instance of {@link OverrideSource override source};</li>
     * <li>{@code sources} - instances of {@link ConfigSource configuration source};</li>
     * <li>{@code mappers} - ordered list of {@link ConfigMapper configuration node mappers}.
     * It is also possible to {@link #disableMapperServices disable} loading of
     * {@link ConfigMapper}s as a {@link java.util.ServiceLoader service}.</li>
     * <li>{@code parsers} - ordered list of {@link ConfigParser configuration content parsers}.
     * It is also possible to {@link #disableParserServices disable} loading of
     * {@link ConfigParser}s as a {@link java.util.ServiceLoader service}.</li>
     * <li>{@code token reference resolving} - a resolving of reference tokens in a key can be {@link #disableKeyResolving()
     * disabled}</li>
     * <li>{@code filters} - ordered list of {@link ConfigFilter configuration value filters}.
     * It is also possible to {@link #disableFilterServices disable} loading of
     * {@link ConfigFilter}s as a {@link java.util.ServiceLoader service}.</li>
     * <li>{@code caching} - can be elementary configuration value processed by filter cached?</li>
     * </ul>
     * <p>
     * In case of {@link ConfigMapper}s, if there is no one that could be used to map appropriate {@code type},
     * it uses {@link java.lang.reflect reflection API} to find public constructor or a public static method
     * to construct {@code type} instance from config {@code node}:
     * <ol>
     * <li>a static method named {@code from} with a single {@code Config} argument that return an instance of the {@code
     * type};</li>
     * <li>a constructor that accepts a single {@code Config} argument;</li>
     * <li>a static method named {@code valueOf} with a single {@code Config} argument
     * that return an instance of the {@code type};</li>
     * <li>a static method named {@code fromConfig} with a single {@code Config} argument
     * that return an instance of the {@code type};</li>
     * <li>a static method named {@code from} with a single {@code String} argument that return an instance of the {@code
     * type};</li>
     * <li>a constructor that accepts a single {@code String} argument;</li>
     * <li>a static method named {@code valueOf} with a single {@code String} argument
     * that return an instance of the {@code type};</li>
     * <li>a static method named {@code fromString} with a single {@code String} argument
     * that return an instance of the {@code type};</li>
     * <li>a static method {@code builder()} that creates instance of a builder class.
     * Generic JavaBean deserialization is applied on the builder instance using config sub-nodes.
     * See the last list item for more details about generic deserialization support.
     * Builder has {@code build()} method to create new instance of a bean.
     * </li>
     * <li>a factory method {@code from} with parameters (loaded from config sub-nodes) creates new instance of a bean;
     * Annotation {@link Config.Value} is used on parameters to customize sub-key and/or default value.
     * </li>
     * <li>a "factory" constructor with parameters (loaded from config sub-nodes);
     * Annotation {@link Config.Value} is used on parameters to customize sub-key and/or default value.
     * </li>
     * <li>a no-parameter constructor to create new instance of type and apply recursively same mapping behaviour
     * described above on each JavaBean property of such object, a.k.a. JavaBean deserialization.
     * Public property setter is used by default to set a property value loaded from appropriate config sub-node.
     * By default a setter pattern is required - public method named {@code set*} with single parameter that returns {@code void}.
     * It is possible to suppress returned {@code void} and {@code set*} name requirements
     * by marking a method by {@link Value} annotation.
     * If there is no public setter, a public property field is used to set a property value.
     * Generic mapping behaviour can be customized by {@link Config.Value} and {@link Config.Transient} annotations.
     * </li>
     * </ol>
     * See {@link Config.Value} documentation for more details about generic deserialization feature.
     * <p>
     * If {@link ConfigSource} not specified, following default config source is used. Same as {@link #create()} uses.
     * It builds composite config source from following sources, checked in order:
     * <ol>
     * <li>Tries to load configuration from meta one of following meta configuration files on classpath, in order:
     * <ol>
     * <li>{@code meta-config.yaml} - meta configuration file in YAML format</li>
     * <li>{@code meta-config.conf} - meta configuration file in HOCON format</li>
     * <li>{@code meta-config.json} - meta configuration file in JSON format</li>
     * <li>{@code meta-config.properties} - meta configuration file in Java Properties format</li>
     * </ol>
     * </li>
     * <li>Otherwise, configuration consists of:
     * <ol>
     * <li>{@link ConfigSources#environmentVariables() Environment variables};</li>
     * <li>or else {@link ConfigSources#systemProperties() System properties}</li>
     * <li>one of following files on classpath, checked in order:
     * <ol>
     * <li>{@code application.yaml} - configuration file in YAML format</li>
     * <li>{@code application.conf} - configuration file in HOCON format</li>
     * <li>{@code application.json} - configuration file in JSON format</li>
     * <li>{@code application.properties} - configuration file in Java Properties format</li>
     * </ol>
     * </li>
     * </ol>
     * </li>
     * </ol>
     * It uses the first and only one file that exists and there is a {@link ConfigParser} available
     * that supports appropriate {@link ConfigParser#getSupportedMediaTypes() media type}.
     * Available parser means that the parser:
     * <ol>
     * <li>is loaded as a service using {@link java.util.ServiceLoader};</li>
     * <li>or if it does not exist, a config core built-in parser is used, if exists.</li>
     * </ol>
     *
     * @see Config#create()
     * @see ConfigSource
     * @see ConfigParser
     * @see ConfigFilter
     * @see ConfigMapper
     */
    interface Builder {

        /**
         * Sets ordered list of {@link ConfigSource} instance to be used as single source of configuration
         * to be wrapped into {@link Config} API.
         * <p>
         * Configuration sources found earlier in the list are considered to have a higher priority than the latter ones. I.e.,
         * when resolving a value of a key, the sources are consulted in the order they have been provided and as soon as
         * the value is found in a configuration source, the value immediately is returned without consulting any of the remaining
         * configuration sources in the prioritized collection.
         * <p>
         * This is default implementation of
         * {@link ConfigSources#from(Supplier...)} Composite ConfigSource} provided by
         * {@link ConfigSources.MergingStrategy#fallback() Fallback MergingStrategy}.
         * It is possible to {@link ConfigSources.CompositeBuilder#mergingStrategy(ConfigSources.MergingStrategy)
         * use custom implementation of merging strategy}.
         * <pre>
         * builder.source(ConfigSources.from(source1, source2, source3)
         *                      .mergingStrategy(new MyMergingStrategy));
         * </pre>
         * Target source is composed from following sources, in order:
         * <ol>
         * <li>{@link ConfigSources#environmentVariables() environment variables config source}<br>
         * Can disabled by {@link #disableEnvironmentVariablesSource()}</li>
         * <li>{@link ConfigSources#systemProperties() system properties config source}
         * Can disabled by {@link #disableSystemPropertiesSource()}</li>
         * <li>Source(s) specified by user in the method.</li>
         * </ol>
         *
         * @param configSources ordered list of configuration sources
         * @return an updated builder instance
         * @see #disableEnvironmentVariablesSource()
         * @see #disableSystemPropertiesSource()
         * @see ConfigSources#from(Supplier...)
         * @see ConfigSources.CompositeBuilder
         * @see ConfigSources.MergingStrategy
         */
        Builder sources(List<Supplier<ConfigSource>> configSources);

        /**
         * Sets a {@link ConfigSource} instance to be used as a source of configuration to be wrapped into {@link Config} API.
         * <p>
         * Target source is composed from {@code configSource} and following sources (unless they are disabled) in order:
         * <ol>
         * <li>{@link ConfigSources#environmentVariables() environment variables config source}<br>
         * Can disabled by {@link #disableEnvironmentVariablesSource()}</li>
         * <li>{@link ConfigSources#systemProperties() system properties config source}
         * Can disabled by {@link #disableSystemPropertiesSource()}</li>
         * <li>Source(s) specified by user in the method.</li>
         * </ol>
         *
         * @param configSource the only config source
         * @return an updated builder instance
         * @see Config#from(Supplier...)
         * @see #sources(List)
         * @see #disableEnvironmentVariablesSource()
         * @see #disableSystemPropertiesSource()
         */
        default Builder sources(Supplier<ConfigSource> configSource) {
            sources(CollectionsHelper.listOf(configSource));
            return this;
        }

        /**
         * Sets an ordered pair of {@link ConfigSource} instances to be used as single source of configuration
         * to be wrapped into {@link Config} API.
         * <p>
         * Target source is composed from {@code configSource} and following sources (unless they are disabled) in order:
         * <ol>
         * <li>{@link ConfigSources#environmentVariables() environment variables config source}<br>
         * Can disabled by {@link #disableEnvironmentVariablesSource()}</li>
         * <li>{@link ConfigSources#systemProperties() system properties config source}
         * Can disabled by {@link #disableSystemPropertiesSource()}</li>
         * <li>Source(s) specified by user in the method.</li>
         * </ol>
         *
         * @param configSource  the first config source
         * @param configSource2 the second config source
         * @return an updated builder instance
         * @see Config#from(Supplier...)
         * @see #sources(List)
         * @see #disableEnvironmentVariablesSource()
         * @see #disableSystemPropertiesSource()
         */
        default Builder sources(Supplier<ConfigSource> configSource,
                                Supplier<ConfigSource> configSource2) {
            sources(CollectionsHelper.listOf(configSource, configSource2));
            return this;
        }

        /**
         * Sets an ordered trio of {@link ConfigSource} instances to be used as single source of configuration
         * to be wrapped into {@link Config} API.
         * <p>
         * Target source is composed from config sources parameters and following sources (unless they are disabled) in order:
         * <ol>
         * <li>{@link ConfigSources#environmentVariables() environment variables config source}<br>
         * Can disabled by {@link #disableEnvironmentVariablesSource()}</li>
         * <li>{@link ConfigSources#systemProperties() system properties config source}
         * Can disabled by {@link #disableSystemPropertiesSource()}</li>
         * <li>Source(s) specified by user in the method.</li>
         * </ol>
         *
         * @param configSource  the first config source
         * @param configSource2 the second config source
         * @param configSource3 the third config source
         * @return an updated builder instance
         * @see Config#from(Supplier...)
         * @see #sources(List)
         * @see #disableEnvironmentVariablesSource()
         * @see #disableSystemPropertiesSource()
         */
        default Builder sources(Supplier<ConfigSource> configSource,
                                Supplier<ConfigSource> configSource2,
                                Supplier<ConfigSource> configSource3) {
            sources(CollectionsHelper.listOf(configSource, configSource2, configSource3));
            return this;
        }

        /**
         * Sets source of a override source.
         * <p>
         * The feature allows user to override existing values with other ones, specified by wildcards. Default values might be
         * defined with key token references (i.e. {@code $env.$pod.logging.level: INFO}) that might be overridden by a config
         * source with a higher priority to identify the current environment (i.e. {@code env: test} and {@code pod: qwerty}. The
         * overrides are able to redefine values using wildcards (or without them). For example {@code test.*.logging.level =
         * FINE} overrides {@code logging.level} for all pods in test environment.
         * <p>
         * Override definitions are applied before any {@link ConfigFilter filter}.
         *
         * @param overridingSource a source with overriding key patterns and assigned values
         * @return an updated builder instance
         */
        Builder overrides(Supplier<OverrideSource> overridingSource);

        /**
         * Disables an usage of resolving key tokens.
         * <p>
         * A key can contain tokens starting with {@code $} (i.e. $host.$port), that are resolved by default and tokens are
         * replaced with a value of the key with the token as a key.
         *
         * @return an updated builder instance
         */
        Builder disableKeyResolving();

        /**
         * Disables an usage of resolving value tokens.
         * <p>
         * A value can contain tokens enclosed in {@code ${}} (i.e. ${name}), that are resolved by default and tokens are replaced
         * with a value of the key with the token as a key.
         *
         * @return an updated builder instance
         */
        Builder disableValueResolving();

        /**
         * Disables use of {@link ConfigSources#environmentVariables() environment variables config source}.
         *
         * @return an updated builder instance
         * @see ConfigSources#environmentVariables()
         */
        Builder disableEnvironmentVariablesSource();

        /**
         * Disables use of {@link ConfigSources#systemProperties() system properties config source}.
         *
         * @return an updated builder instance
         * @see ConfigSources#systemProperties()
         */
        Builder disableSystemPropertiesSource();

        /**
         * Registers contextual {@link ConfigMapper} for specified {@code type}.
         * The last registration of same {@code type} overwrites previous one.
         * Programmatically registered mappers have priority over other options.
         * <p>
         * As another option, mappers are loaded automatically as a {@link java.util.ServiceLoader service}
         * via {@link io.helidon.config.spi.ConfigMapperProvider} SPI, if not {@link #disableMapperServices() disabled}.
         * <p>
         * And the last option, {@link ConfigMappers built-in mappers} are registered.
         *
         * @param type   class of type the {@code mapper} is registered for
         * @param mapper mapper instance
         * @param <T>    type the {@code mapper} is registered for
         * @return an updated builder instance
         * @see #addMapper(Class, Function)
         * @see #addMapper(ConfigMapperProvider)
         * @see ConfigMappers
         * @see #disableMapperServices
         */
        <T> Builder addMapper(Class<T> type, ConfigMapper<T> mapper);

        /**
         * Registers simple {@link Function} from {@code String} for specified {@code type}.
         * The last registration of same {@code type} overwrites previous one.
         * Programmatically registered mappers have priority over other options.
         * <p>
         * As another option, mappers are loaded automatically as a {@link java.util.ServiceLoader service}
         * via {@link io.helidon.config.spi.ConfigMapperProvider} SPI, if not {@link #disableMapperServices() disabled}.
         * <p>
         * And the last option, {@link ConfigMappers built-in mappers} are registered.
         *
         * @param type   class of type the {@code mapper} is registered for
         * @param mapper mapper instance
         * @param <T>    type the {@code mapper} is registered for
         * @return an updated builder instance
         * @see #addMapper(Class, ConfigMapper)
         * @see #addMapper(ConfigMapperProvider)
         * @see ConfigMappers
         * @see #disableMapperServices
         */
        <T> Builder addMapper(Class<T> type, Function<String, T> mapper);

        /**
         * Registers a {@link ConfigMapper} provider with a map of {@code String} to specified {@code type}.
         * The last registration of same {@code type} overwrites previous one.
         * Programmatically registered mappers have priority over other options.
         * <p>
         * As another option, mappers are loaded automatically as a {@link java.util.ServiceLoader service}
         * via {@link io.helidon.config.spi.ConfigMapperProvider} SPI, if not {@link #disableMapperServices() disabled}.
         * <p>
         * And the last option, {@link ConfigMappers built-in mappers} are registered.
         *
         * @param configMapperProvider mapper provider instance
         * @return modified builder instance
         * @see #addMapper(Class, ConfigMapper)
         * @see #addMapper(Class, Function)
         * @see ConfigMappers
         * @see #disableMapperServices
         */
        Builder addMapper(ConfigMapperProvider configMapperProvider);

        /**
         * Disables automatic registration of mappers via {@link io.helidon.config.spi.ConfigMapperProvider} SPI
         * loaded as a {@link java.util.ServiceLoader service}.
         * <p>
         * Order of configuration mapper providers loaded as a service
         * is defined by {@link javax.annotation.Priority} annotation.
         * <p>
         * Automatic registration of mappers as a service is enabled by default.
         *
         * @return an updated builder instance
         * @see io.helidon.config.spi.ConfigMapperProvider
         */
        Builder disableMapperServices();

        /**
         * Registers a {@link ConfigParser} instance that can be used by registered {@link ConfigSource}s to
         * parse {@link ConfigParser.Content configuration content}.
         * Parsers are tried to be used by {@link io.helidon.config.spi.ConfigContext#findParser(String)}
         * in same order as was registered by the {@link #addParser(ConfigParser)} method.
         * Programmatically registered parsers have priority over other options.
         * <p>
         * As another option, parsers are loaded automatically as a {@link java.util.ServiceLoader service}, if not
         * {@link #disableParserServices() disabled}.
         *
         * @param configParser parser instance
         * @return an updated builder instance
         * @see #disableParserServices
         */
        Builder addParser(ConfigParser configParser);

        /**
         * Disables automatic registration of parsers loaded as a {@link java.util.ServiceLoader service}.
         * <p>
         * Order of configuration parsers loaded as a service is defined by {@link javax.annotation.Priority} annotation.
         * <p>
         * Automatic registration of parsers as a service is enabled by default.
         *
         * @return an updated builder instance
         * @see ConfigParser
         */
        Builder disableParserServices();

        /**
         * Registers a {@link ConfigFilter} instance that will be used by {@link Config} to
         * filter elementary value before it is returned to a user.
         * <p>
         * Filters are applied in same order as was registered by the {@link #addFilter(ConfigFilter)}, {@link
         * #addFilter(Function)} or {@link #addFilter(Supplier)} method.
         * <p>
         * {@link ConfigFilter} is actually a {@link java.util.function.BiFunction}&lt;{@link String},{@link String},{@link
         * String}&gt; where the first input parameter is the config key, the second is the original value and the result is the
         * new value. So the filter can be added as simply as:
         * <pre>
         *     Config.builder()
         *          .addFilter((key, originalValue) -&gt; originalValue.toUpperCase())
         *          .build();
         * </pre>
         *
         * The config system will automatically load filters defined as a
         * {@link java.util.ServiceLoader service}, unless
         * {@link #disableFilterServices() disabled}.
         *
         * @param configFilter filter instance
         * @return an updated builder instance
         * @see #addFilter(Function)
         * @see #addFilter(Supplier)
         */
        Builder addFilter(ConfigFilter configFilter);

        /**
         * Registers a {@link ConfigFilter} provider as a {@link Function}&lt;{@link Config}, {@link ConfigFilter}&gt;. An
         * obtained filter will be used by {@link Config} to filter elementary value before it is returned to a user.
         * <p>
         * Filters are applied in same order as was registered by the {@link #addFilter(ConfigFilter)}, {@link
         * #addFilter(Function)} or {@link #addFilter(Supplier)} method.
         * <p>
         * Registered provider's {@link Function#apply(Object)} method is called every time the new Config is created. Eg. when
         * this builder's {@link #build} method creates the {@link Config} or when the new {@link Config#changes() change event}
         * is fired with new Config instance with its own filter instance is created.
         *
         * @param configFilterProvider a config filter provider as a function of {@link Config} to {@link ConfigFilter}
         * @return an updated builder instance
         * @see #addFilter(ConfigFilter)
         * @see #addFilter(Supplier)
         */
        Builder addFilter(Function<Config, ConfigFilter> configFilterProvider);

        /**
         * Registers a {@link ConfigFilter} provider as a {@link Supplier}&lt;{@link Function}&lt;{@link Config}, {@link
         * ConfigFilter}&gt;&gt;. An obtained filter will be used by {@link Config} to filter elementary value before it is
         * returned to a user.
         * <p>
         * Filters are applied in same order as was registered by the {@link #addFilter(ConfigFilter)}, {@link
         * #addFilter(Function)} or {@link #addFilter(Supplier)} method.
         * <p>
         * Registered provider's {@link Function#apply(Object)} method is called every time the new Config is created. Eg. when
         * this builder's {@link #build} method creates the {@link Config} or when the new {@link Config#changes() change event}
         * is fired with new Config instance with its own filter instance is created.
         *
         * @param configFilterSupplier a config filter provider as a supplier of a function of {@link Config} to {@link
         *                             ConfigFilter}
         * @return an updated builder instance
         * @see #addFilter(ConfigFilter)
         * @see #addFilter(Function)
         */
        Builder addFilter(Supplier<Function<Config, ConfigFilter>> configFilterSupplier);

        /**
         * Disables automatic registration of filters loaded as a {@link java.util.ServiceLoader service}.
         * <p>
         * Order of configuration filters loaded as a service is defined by {@link javax.annotation.Priority} annotation.
         * <p>
         * Automatic registration of filters as a service is enabled by default.
         *
         * @return an updated builder instance
         * @see ConfigFilter
         */
        Builder disableFilterServices();

        /**
         * Disables caching of elementary configuration values on {@link Config} side.
         * <p>
         * Caching is about {@link ConfigFilter}s. With disabled caching, registered filters are applied always you
         * access elementary configuration value. With enabled caching, registered filters are applied just once per
         * unique config node (key). Repeated access of already filtered key directly returns already cached value.
         * <p>
         * Caching is enabled by default.
         *
         * @return an updated builder instance
         * @see #addFilter(ConfigFilter)
         */
        Builder disableCaching();

        /**
         * Specifies "observe-on" {@link Executor} to be used by {@link Config#changes()} to deliver new Config instance.
         * Executor is also used to process reloading of config from appropriate {@link ConfigSource#changes() source}.
         * <p>
         * By default dedicated thread pool that creates new threads as needed, but
         * will reuse previously constructed threads when they are available is used.
         *
         * @param changesExecutor the executor to use for async delivery of {@link Config#changes()} events
         * @return an updated builder instance
         * @see #changesMaxBuffer(int)
         * @see Config#changes()
         * @see Config#onChange(Function)
         * @see ConfigSource#changes()
         */
        Builder changesExecutor(Executor changesExecutor);

        /**
         * Specifies maximum capacity for each subscriber's buffer to be used by by {@link Config#changes()}
         * to deliver new Config instance.
         * <p>
         * By default {@link io.helidon.common.reactive.Flow#DEFAULT_BUFFER_SIZE} is used.
         * <p>
         * Note: Not consumed events will be dropped off.
         *
         * @param changesMaxBuffer the maximum capacity for each subscriber's buffer of {@link Config#changes()} events.
         * @return an updated builder instance
         * @see #changesExecutor(Executor)
         * @see Config#changes()
         * @see Config#onChange(Function)
         */
        Builder changesMaxBuffer(int changesMaxBuffer);

        /**
         * Builds new instance of {@link Config}.
         *
         * @return new instance of {@link Config}.
         */
        Config build();
    }

    /**
     * Context associated with specific {@link Config} node that allows to access the last loaded instance of the node
     * or to request reloading of whole configuration.
     */
    interface Context {
        /**
         * Returns timestamp of the last loaded configuration.
         *
         * @return timestamp of the last loaded configuration.
         * @see Config#timestamp()
         */
        Instant timestamp();

        /**
         * Returns instance of Config node related to same Config {@link Config#key() key}
         * as original {@link Config#context() node} used to get Context from.
         * <p>
         * If the configuration has not been reloaded yet it returns original Config node instance.
         *
         * @return the last instance of Config node associated with same key as original node
         * @see Config#context()
         */
        Config last();

        /**
         * Requests reloading of whole configuration and returns new instance of
         * Config node related to same Config {@link Config#key() key}
         * as original {@link Config#context() node} used to get Context from.
         *
         * @return the new instance of Config node associated with same key as original node
         * @see Config.Builder
         */
        Config reload();
    }

    /**
     * Annotation used to customize behaviour of JavaBean deserialization support, generic {@link ConfigMapper}
     * implementation.
     * <p>
     * The first option for generic Config to JavaBean deserialization works just with class with no-parameter constructor.
     * Each JavaBean property value is then set by value mapped from appropriate configuration node.
     * Each public setter method and public non-final fields are taken as JavaBean properties that will be set.
     * The deserialization process is applied recursively on each property.
     * <p>
     * Use {@link Transient} annotation to exclude setter or field from set of processed JavaBean properties.
     * <p>
     * By default JavaBean property name is used as config key to {@link Config#get(String) get} configuration node.
     * The config key can be customized by {@link #key()} attribute. Use the annotation on public setter or public field.
     * Annotation on method has precedence over annotation used on field. The second one is ignored.
     * <p>
     * If the appropriate configuration node does not exist it is possible to specify default value:
     * <ul>
     * <li>{@link #withDefaultSupplier()} - instance of supplier class is used to get default value of target type; or</li>
     * <li>{@link #withDefault()} - default value in {@code String} form that will be mapped to target type
     * by associated {@link ConfigMapper}</li>
     * </ul>
     * In case of both <i>default</i> attributes are set the {@code withDefaultSupplier} is used
     * and {@code withDefault} is ignored.
     * <pre><code>
     * public class AppConfig {
     *     private String greeting;
     *     private int pageSize;
     *     private List{@literal <Integer>} range;
     *
     *     public AppConfig() { // {@literal <1>}
     *     }
     *
     *     public void setGreeting(String greeting) { // {@literal <2>}
     *         this.greeting = greeting;
     *     }
     *
     *     {@literal @}Config.Value(key = "page-size", withDefault = "10") // {@literal <3>}
     *     public void setPageSize(int pageSize) {
     *         this.pageSize = pageSize;
     *     }
     *
     *     {@literal @}Config.Value(withDefaultSupplier = DefaultRangeSupplier.class) // {@literal <4>}
     *     public void setRange(List{@literal <Integer>} basicRange) {
     *         this.range = basicRange;
     *     }
     *
     *     //...
     *
     *     public static class DefaultRangeSupplier // {@literal <5>}
     *                 implements Supplier{@literal <List<Integer>>} {
     *         {@literal @}Override
     *         public List{@literal <Integer>} get() {
     *             return CollectionsHelper.listOf(0, 10);
     *         }
     *     }
     * }
     * </code></pre>
     * <ol>
     * <li>public no-parameter constructor;</li>
     * <li>property {@code greeting} is not customized; will be set from config node with {@code greeting} key, if exists;</li>
     * <li>property {@code pageSize} customizes key of config node to {@code page-size};
     * if the config node does not exist, value {@code "10"} will be mapped to {@code int};
     * </li>
     * <li>property {@code range} will be set from config node with same {@code range} key;
     * if the config node does not exist, {@code DefaultRangeSupplier} instance will be used to get default value;
     * </li>
     * <li>{@code DefaultRangeSupplier} is used to supply {@code List<Integer>} value.</li>
     * </ol>
     * <p>
     * The second option is to provide factory public static method {@code from} with parameters set from configuration.
     * Or public "factory" constructor with parameters can be used too.
     * <pre><code>
     * public class AppConfig {
     *     private final String greeting;
     *     private final int pageSize;
     *     private final List{@literal <Integer>} basicRange;
     *
     *     private AppConfig(String greeting, int pageSize, List{@literal <Integer>} basicRange) {
     *         this.greeting = greeting;
     *         this.pageSize = pageSize;
     *         this.basicRange = basicRange;
     *     }
     *
     *     //...
     *
     *     // FACTORY METHOD
     *     public static AppConfig from({@literal @}Config.Value(key = "greeting", withDefault = "Hi")
     *                                  String greeting,
     *                                  {@literal @}Config.Value(key = "page-size", withDefault = "10")
     *                                  int pageSize,
     *                                  {@literal @}Config.Value(key = "basic-range",
     *                                          withDefaultSupplier = DefaultBasicRangeSupplier.class)
     *                                  List{@literal <Integer>} basicRange) {
     *         return new AppConfig(greeting, pageSize, basicRange);
     *     }
     * }
     * </code></pre>
     * <p>
     * The third option is to provide Builder accessible by public static {@code builder()} method.
     * The Builder instances is initialized via public setters or fields, similar to the first deserialization option.
     * Finally, Builder has {@code build()} method that creates new instances of a bean.
     * <pre><code>
     * public class AppConfig {
     *     private final String greeting;
     *     private final int pageSize;
     *     private final List{@literal <Integer>} basicRange;
     *
     *     private AppConfig(String greeting, int pageSize, List{@literal <Integer>} basicRange) {
     *         this.greeting = greeting;
     *         this.pageSize = pageSize;
     *         this.basicRange = basicRange;
     *     }
     *
     *     // BUILDER METHOD
     *     public static Builder builder() {
     *         return new Builder();
     *     }
     *
     *     public static class Builder {
     *         private String greeting;
     *         private int pageSize;
     *         private List{@literal <Integer>} basicRange;
     *
     *         private Builder() {
     *         }
     *
     *         {@literal @}Config.Value(withDefault = "Hi")
     *         public void setGreeting(String greeting) {
     *             this.greeting = greeting;
     *         }
     *
     *         {@literal @}Config.Value(key = "page-size", withDefault = "10")
     *         public void setPageSize(int pageSize) {
     *             this.pageSize = pageSize;
     *         }
     *
     *         {@literal @}Config.Value(key = "basic-range",
     *                 withDefaultSupplier = DefaultBasicRangeSupplier.class)
     *         public void setBasicRange(List{@literal <Integer>} basicRange) {
     *             this.basicRange = basicRange;
     *         }
     *
     *         // BUILD METHOD
     *         public AppConfig build() {
     *             return new AppConfig(greeting, pageSize, basicRange);
     *         }
     *     }
     * }
     * </code></pre>
     * <p>
     * Configuration example:
     * <pre>{@code
     * {
     *     "app": {
     *         "greeting": "Hello",
     *         "page-size": 20,
     *         "range": [ -20, 20 ]
     *     }
     * }
     * }</pre>
     * Getting {@code app} config node as {@code AppConfig} instance:
     * <pre>{@code
     * AppConfig appConfig = config.get("app").as(AppConfig.class);
     * assert appConfig.getGreeting().equals("Hello");
     * assert appConfig.getPageSize() == 20;
     * assert appConfig.getRange().get(0) == -20;
     * assert appConfig.getRange().get(1) == 20;
     * }</pre>
     * In this case default values where not used because JSON contains all expected nodes.
     * <p>
     * The annotation cannot be applied on same JavaBean property together with {@link Transient}.
     *
     * @see Transient
     */
    @Retention(RUNTIME)
    @Target({METHOD, FIELD, PARAMETER})
    @interface Value {

        /**
         * Specifies a key of configuration node to be used to set JavaBean property value from.
         * <p>
         * If not specified original JavaBean property name is used.
         *
         * @return config property key
         */
        String key() default "";

        /**
         * Specifies default value in form of single String value
         * that will be used to set JavaBean property value in case configuration does not contain a config node
         * of appropriate config key.
         * <p>
         * In case {@link #withDefaultSupplier} is also used current value is ignored.
         *
         * @return single default value that will be converted into target type
         */
        String withDefault() default None.VALUE;

        /**
         * Specifies supplier of default value
         * that will be used to set JavaBean property value in case configuration does not contain config node
         * of appropriate config key.
         * <p>
         * Default value is used in case appropriate config value is not set.
         * In case {@link #withDefault} is also used this one has higher priority and will be used.
         *
         * @return supplier that will provide default value in target type
         */
        Class<? extends Supplier> withDefaultSupplier() default None.class;

        /**
         * Class that represents not-set default values.
         */
        interface None extends Supplier {
            String VALUE = "io.helidon.config:default=null";

            @Override
            default Object get() {
                return null;
            }
        }

    }

    /**
     * Annotation used to exclude JavaBean property, method or constructor from JavaBean deserialization support.
     * The annotation can be used on JavaBean property public setter, on public property field,
     * on public constructor or on public {@code builder} and {@code build} method.
     * <p>
     * The annotation cannot be applied on same JavaBean property together with {@link Value}.
     * <p>
     * In following example, property {@code timestamp} is not set even {@code timestamp} config value is available.
     * Property {@code timestamp} is completely ignored by deserialization process.
     * <pre><code>
     * public class AppConfig {
     *     private Instant timestamp;
     *     private String greeting;
     *
     *     {@literal @}Config.Transient
     *     public void setTimestamp(Instant timestamp) { // {@literal <1>}
     *         this.timestamp = timestamp;
     *     }
     *
     *     public void setGreeting(String greeting) {    // {@literal <2>}
     *         this.greeting = greeting;
     *     }
     *
     *     //...
     * }
     * </code></pre>
     * <ol>
     * <li>The {@code setTimestamp(Instant)} method is never called during deserialization.</li>
     * <li>While {@code setGreeting(String)} can be called if {@code greeting} config value is available.</li>
     * </ol>
     * Configuration example:
     * <pre>{@code
     * {
     *     "app" : {
     *         "greeting" : "Hello",
     *         "timestamp" : "2007-12-03T10:15:30.00Z"
     *     }
     * }
     * }</pre>
     * Getting {@code app} config node as {@code AppConfig} instance:
     * <pre>{@code
     * AppConfig appConfig = config.get("app").as(AppConfig.class);
     * assert appConfig.getTimestamp() == null;
     * }</pre>
     *
     * @see Value
     */
    @Retention(RUNTIME)
    @Target({METHOD, FIELD, CONSTRUCTOR})
    @interface Transient {
    }

}
