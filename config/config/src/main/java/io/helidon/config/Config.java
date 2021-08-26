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

package io.helidon.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.ValueProvider;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigMapper;
import io.helidon.config.spi.ConfigMapperProvider;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.MergingStrategy;
import io.helidon.config.spi.OverrideSource;

/**
 * <h1>Configuration</h1>
 * Immutable tree-structured configuration.
 * <h2>Loading Configuration</h2>
 * Load the default configuration using the {@link Config#create} method.
 * <pre>{@code
 * Config config = Config.create();
 * }</pre> Use {@link Config.Builder} to construct a new {@code Config} instance
 * from one or more specific {@link ConfigSource}s using the {@link #builder()}.
 * <p>
 * The application can affect the way the system loads configuration by
 * implementing interfaces defined in the SPI, by explicitly constructing the
 * {@link Builder} which assembles the {@code Config}, and by using other
 * classes provided by the config system that influence loading.
 * <table class="config">
 * <caption><b>Some Config SPI Interfaces</b></caption>
 * <tr>
 * <th>Class.Method</th>
 * <th>Application-implemented Interface</th>
 * <th>Purpose</th>
 * </tr>
 * <tr>
 * <td>{@link ConfigSources#create}</td>
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
 * <td>{@link OverrideSources}</td>
 * <td></td>
 * <td>Replaces config {@code String} values during loading based on their keys.
 * Programs provide overrides in Java property file format on the classpath, at
 * a URL, or in a file, or by invoking {@link OverrideSources#create} and passing
 * the name-matching expressions and the corresponding replacement value as a
 * {@code Map}.</td>
 * </tr>
 * <tr>
 * <td>{@link Builder#addMapper(Class, Function)}</td>
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
 * assert name4.key().equals(Key.create("app.services.svc1.name"))
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
 * {@link #asNodeList()}
 * <ul>
 * <li>on an {@link Type#OBJECT object} node to get all object members,</li>
 * <li>on a {@link Type#LIST list} node to get all list elements.</li>
 * </ul>
 * <p>
 * To get node value, use {@link #as(Class)} to access this config node as a {@link ConfigValue}
 *
 * <h2>Converting Configuration Values to Types</h2>
 * <h3>Explicit Conversion by the Application</h3>
 * The interpretation of a configuration node, including what datatype to use,
 * is up to the application. To interpret a node's value as a type other than
 * {@code String} the application can invoke one of these convenience methods:
 * <ul>
 * <li>{@code as<typename>} such as {@code asBoolean, asDouble, asInt}, etc.
 * which return {@link ConfigValue} representing Java primitive data values ({@code boolean, double, int}, etc.)
 * <p>
 * The {@link ConfigValue} can be used to access the value or use optional style methods.
 *
 * The config value provides access to the value in multiple ways.
 * See {@link ConfigValue} for reference.
 *
 * Basic usages:
 * <pre>{@code
 * // throws a MissingValueException in case the config node does not exist
 * long l1 = config.asLong().get();
 * // returns 42 in case the config node does not exist
 * long l2 = config.asLong().orElse(42L);
 * // invokes the method "timeout(long)" if the value exists
 * config.asLong().ifPresent(this::timeout);
 * }</pre>
 * </li>
 * <li>{@link #as(Class)} to convert the config node to an instance of the specified class, if there is a configured
 * mapper present that supports the class.
 * <pre>{@code
 *   // throws a MissingValueException in case the config node does not exist
 *   // throws a ConfigMappingException in case the config node cannot be converted to Long
 *   long l1 = config.as(Long.class).get();
 *   // returns 42 in case the config node does not exist
 *   // throws a ConfigMappingException in case the config node cannot be converted to Long
 *   long l2 = config.as(Long.class).orElse(42L);
 *   // invokes the method "timeout(long)" if the value exists
 *   // throws a ConfigMappingException in case the config node cannot be converted to Long
 *   config.as(Long.class).ifPresent(this::timeout);
 *   }</pre>
 * </li>
 * <li>{@link #as(Function)} to convert the config node using the function provided.
 * Let's assume there is a method {@code public static Foo create(Config)} on a class {@code Foo}:
 * <pre>{@code
 *  // throws a MissingValueException in case the config node does not exist
 *  // throws a ConfigMappingException in case the config node cannot be converted to Foo
 *  Foo f1 = config.as(Foo::create).get();
 *  // throws a ConfigMappingException in case the config node cannot be converted to Foo
 *  Foo f2 = config.as(Foo::create).orElse(Foo.DEFAULT);
 *  // invokes the method "foo(Foo)" if the value exists
 *  // throws a ConfigMappingException in case the config node cannot be converted to Foo
 *  config.as(Foo::create).ifPresent(this::foo);
 * }</pre>
 * </li>
 * <li>{@link #as(GenericType)} to convert the config node to an instance of the specified generic type, if there is a
 * configured mapper present that supports the generic type.
 * <pre>{@code
 *  // throws a MissingValueException in case the config node does not exist
 *  // throws a ConfigMappingException in case the config node cannot be converted to Map<String, Integer>
 *  Map<String, Integer> m1 = config.as(new GenericType<Map<String, Integer>() {}).get();
 *  // throws a ConfigMappingException in case the config node cannot be converted to Map<String, Integer>
 *  Map<String, Integer> m1 = config.as(new GenericType<Map<String, Integer>() {}).orElseGet(Collections::emptyMap);
 *  // invokes the method "units(Map)" if the value exists
 *  // throws a ConfigMappingException in case the config node cannot be converted to Map<String, Integer>
 *  config.as(new GenericType<Map<String, Integer>() {}).ifPresent(this::units);
 * }</pre>
 * </li>
 * </ul>
 *
 * To deal with application-specific types, the application can provide its own
 * mapping logic by:
 * <ul>
 * <li>invoking the {@link Config#as(Function)} method variants, </li>
 * <li>adding custom mapping function implementations using the
 * {@link Builder#addMapper(Class, Function)} method,</li>
 * <li>add custom mapping function using the {@link Builder#addStringMapper(Class, Function)}</li>
 * <li>registering custom mappers using the Java service loader mechanism. (See
 * {@link ConfigMapperProvider} for details.)
 * </li>
 * </ul>
 *
 * <p>
 * If there is no explicitly registered mapping function in a
 * {@link Builder} for converting a given type then the config system
 * throws {@link ConfigMappingException}, unless you use the config beans support,
 * that can handle classes that fulfill some requirements (see documentation), such as a public constructor,
 * static "create(Config)" method etc.
 *
 * <h2><a id="multipleSources">Handling Multiple Configuration
 * Sources</a></h2>
 * A {@code Config} instance, including the default {@code Config} returned by
 * {@link Config#create}, might be associated with multiple {@link ConfigSource}s. The
 * config system merges these together so that values from config sources with higher priority have
 * precedence over values from config sources with lower priority.
 */
public interface Config extends ValueProvider {
    /**
     * Returns empty instance of {@code Config}.
     *
     * @return empty instance of {@code Config}.
     */
    static Config empty() {
        return BuilderImpl.EmptyConfigHolder.EMPTY;
    }

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
     * corresponding {@link ConfigParser#supportedMediaTypes() media type}.
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
     */
    static Config create() {
        return builder().metaConfig().build();
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
     * @see #builder(Supplier[])
     * @see Builder#sources(List)
     * @see Builder#disableEnvironmentVariablesSource()
     * @see Builder#disableSystemPropertiesSource()
     */
    @SafeVarargs
    static Config create(Supplier<? extends ConfigSource>... configSources) {
        return builder(configSources).build();
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
     * @see #create(Supplier[])
     * @see Builder#sources(List)
     * @see Builder#disableEnvironmentVariablesSource()
     * @see Builder#disableSystemPropertiesSource()
     */
    @SafeVarargs
    static Builder builder(Supplier<? extends ConfigSource>... configSources) {
        return builder().sources(List.of(configSources));
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
     * Creates a new {@link Config} loaded from the specified {@link ConfigSource}s.
     * No other sources will be included.
     *
     * @param configSources ordered list of configuration sources
     * @return new instance of {@link Config}
     * @see #builder(Supplier[])
     */
    @SafeVarargs
    static Config just(Supplier<? extends ConfigSource>... configSources) {
        return builder(configSources)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
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
     * See {@link #asMap()} for example of config detaching.
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
     *         otherwise.
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
        asNode().ifPresent(action);
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
     *                  to continue or stop visiting the node
     * @return stream of deepening depth-first subnodes
     */
    Stream<Config> traverse(Predicate<Config> predicate);

    // instance utility

    /**
     * Convert a String to a specific type.
     * This is a helper method to allow for processing of default values that cannot be typed (e.g. in annotations).
     *
     * @param type  type of the property
     * @param value String value
     * @param <T>   type
     * @return instance of the correct type
     * @throws ConfigMappingException in case the String provided cannot be converted to the type expected
     * @see Config#as(Class)
     */
    <T> T convert(Class<T> type, String value) throws ConfigMappingException;

    /**
     * The mapper used by this config instance.
     *
     * @return configuration mapper
     */
    ConfigMapper mapper();

    //
    // accessors
    //

    /**
     * Typed value as a {@link ConfigValue} for a generic type.
     * If appropriate mapper exists, returns a properly typed generic instance.
     * <p>
     * Example:
     * <pre>
     * {@code
     * ConfigValue<Map<String, Integer>> myMapValue = config.as(new GenericType<Map<String, Integer>>(){});
     * myMapValue.ifPresent(map -> {
     *      Integer port = map.get("service.port");
     *  }
     * }
     * </pre>
     *
     * @param genericType a (usually anonymous) instance of generic type to prevent type erasure
     * @param <T>         type of the returned value
     * @return properly typed config value
     */
    @Override
    <T> ConfigValue<T> as(GenericType<T> genericType);

    /**
     * Typed value as a {@link ConfigValue}.
     *
     * @param type type class
     * @param <T>  type
     * @return typed value
     * @see ConfigValue#map(Function)
     * @see ConfigValue#supplier()
     * @see ConfigValue#get()
     * @see ConfigValue#orElse(Object)
     */
    @Override
    <T> ConfigValue<T> as(Class<T> type);

    /**
     * Typed value as a {@link ConfigValue} created from factory method.
     * To convert from String, you can use
     * {@link #asString() config.asString()}{@link ConfigValue#as(Function) .as(Function)}.
     *
     * @param mapper method to create an instance from config
     * @param <T>    type
     * @return typed value
     */
    <T> ConfigValue<T> as(Function<Config, T> mapper);

    // shortcut methods

    /**
     * Boolean typed value.
     *
     * @return typed value
     */
    @Override
    default ConfigValue<Boolean> asBoolean() {
        return as(Boolean.class);
    }

    /**
     * String typed value.
     *
     * @return typed value
     */
    @Override
    default ConfigValue<String> asString() {
        return as(String.class);
    }

    /**
     * Integer typed value.
     *
     * @return typed value
     */
    @Override
    default ConfigValue<Integer> asInt() {
        return as(Integer.class);
    }

    /**
     * Long typed value.
     *
     * @return typed value
     */
    @Override
    default ConfigValue<Long> asLong() {
        return as(Long.class);
    }

    /**
     * Double typed value.
     *
     * @return typed value
     */
    @Override
    default ConfigValue<Double> asDouble() {
        return as(Double.class);
    }

    /**
     * Returns list of specified type.
     *
     * @param type type class
     * @param <T>  type of list elements
     * @return a typed list with values
     * @throws ConfigMappingException in case of problem to map property value.
     */
    @Override
    <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigMappingException;

    /**
     * Returns this node as a list converting each list value using the provided mapper.
     *
     * @param mapper mapper to convert each list node into a typed value
     * @param <T>    type of list elements
     * @return a typed list with values
     * @throws ConfigMappingException in case the mapper fails to map the values
     */
    <T> ConfigValue<List<T>> asList(Function<Config, T> mapper) throws ConfigMappingException;

    /**
     * Returns existing current config node as a {@link Optional} instance
     * or {@link Optional#empty()} in case of {@link Type#MISSING} node.
     *
     * @return current config node as a {@link Optional} instance
     *         or {@link Optional#empty()} in case of {@link Type#MISSING} node.
     */
    default ConfigValue<Config> asNode() {
        return ConfigValues.create(this,
                                   () -> exists() ? Optional.of(this) : Optional.empty(),
                                   Config::asNode);
    }

    /**
     * Returns a list of child {@code Config} nodes if the node is {@link Type#OBJECT}.
     * Returns a list of element nodes if the node is {@link Type#LIST}.
     * Throws {@link MissingValueException} if the node is {@link Type#MISSING}.
     * Otherwise, if node is {@link Type#VALUE}, it throws {@link ConfigMappingException}.
     *
     * @return a list of {@link Type#OBJECT} members or a list of {@link Type#LIST} members
     * @throws ConfigMappingException in case the node is {@link Type#VALUE}
     */
    ConfigValue<List<Config>> asNodeList() throws ConfigMappingException;

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
     * @see #traverse()
     * @see #detach()
     */
    ConfigValue<Map<String, String>> asMap() throws MissingValueException;

    //
    // config changes
    //

    /**
     * Register a {@link Consumer} that is invoked each time a change occurs on whole Config or on a particular Config node.
     * <p>
     * A user can subscribe on root Config node and than will be notified on any change of Configuration.
     * You can also subscribe on any sub-node, i.e. you will receive notification events just about sub-configuration.
     * No matter how much the sub-configuration has changed you will receive just one notification event that is associated
     * with a node you are subscribed on.
     * If a user subscribes on older instance of Config and ones has already been published the last one is automatically
     * submitted to new-subscriber.
     * <p>
     * Note: It does not matter what instance version of Config (related to single {@link Builder} initialization)
     * a user subscribes on. It is enough to subscribe just on single (e.g. on the first) Config instance.
     * There is no added value to subscribe again on new Config instance.
     *
     * @param onChangeConsumer consumer invoked on change
     */
    default void onChange(Consumer<Config> onChangeConsumer) {
        // no-op
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
         * If the key represents root config node it throws an exception.
         *
         * @return key that represents key of parent config node.
         * @see #isRoot()
         * @throws java.lang.IllegalStateException in case you attempt to call this method on a root node
         */
        Key parent();

        /**
         * Returns {@code true} in case the key represents root config node,
         * otherwise it returns {@code false}.
         *
         * @return {@code true} in case the key represents root node, otherwise {@code false}.
         * @see #parent()
         */
        boolean isRoot();

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

        Key child(Key key);

        /**
         * Creates new instance of Key for specified {@code key} literal.
         * <p>
         * Empty literal means root node.
         * Character dot ('.') has special meaning - it separates fully-qualified key by key tokens (node names).
         *
         * @param key formatted fully-qualified key.
         * @return Key instance representing specified fully-qualified key.
         */
        static Key create(String key) {
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
            for (char ch : chars) {
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
         * ({@link #VALUE values}, {@link #LIST lists} or other objects).
         */
        OBJECT(true, false),
        /**
         * Config node is a list of indexed elements
         * ({@link #VALUE values}, {@link #OBJECT objects} or other lists).
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

        private final boolean exists;
        private final boolean isLeaf;

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
         * This method uses the last known value of the node, as provided through change support.
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
     * {@link Config} Builder.
     * <p>
     * A factory for a {@code Config} object.
     * <p>
     * The application can set the following characteristics:
     * <ul>
     * <li>{@code overrides} - instance of {@link OverrideSource override source};</li>
     * <li>{@code sources} - instances of {@link ConfigSource configuration source};</li>
     * <li>{@code mappers} - ordered list of mapper functions.
     * It is also possible to {@link #disableMapperServices disable} loading of
     * {@link ConfigMapperProvider}s as a {@link java.util.ServiceLoader service}.</li>
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
     * In case of {@link ConfigMapperProvider}s, if there is no one that could be used to map appropriate {@code type},
     * the mapping attempt throws a {@link ConfigMappingException}.
     * <p>
     * A more sophisticated approach can be achieved using the "config beans" module, that provides reflection access
     * and mapping for static factory methods, constructors, builder patterns and more.
     * <p>
     * If a {@link ConfigSource} is not specified, following default config source is used. Same as {@link #create()} uses.
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
     * that supports appropriate {@link ConfigParser#supportedMediaTypes() media type}.
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
         */
        Builder sources(List<Supplier<? extends ConfigSource>> configSources);

        /**
         * Add a config source to the list of sources.
         *
         * @param source to add
         * @return updated builder instance
         */
        Builder addSource(ConfigSource source);

        /**
         * Merging Strategy to use when more than one config source is used.
         *
         * @param strategy strategy to use, defaults to a strategy where a value for first source wins over values from later
         *                sources
         * @return updated builder instance
         */
        Builder mergingStrategy(MergingStrategy strategy);

        /**
         * Add a single config source to this builder.
         *
         * @param source config source to add
         * @return updated builder instance
         */
        default Builder addSource(Supplier<? extends ConfigSource> source) {
            return addSource(source.get());
        }

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
         * @see Config#create(Supplier...)
         * @see #sources(List)
         * @see #disableEnvironmentVariablesSource()
         * @see #disableSystemPropertiesSource()
         */
        default Builder sources(Supplier<? extends ConfigSource> configSource) {
            sources(List.of(configSource));
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
         * @see Config#create(Supplier...)
         * @see #sources(List)
         * @see #disableEnvironmentVariablesSource()
         * @see #disableSystemPropertiesSource()
         */
        default Builder sources(Supplier<? extends ConfigSource> configSource,
                                Supplier<? extends ConfigSource> configSource2) {
            sources(List.of(configSource, configSource2));
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
         * @see Config#create(Supplier...)
         * @see #sources(List)
         * @see #disableEnvironmentVariablesSource()
         * @see #disableSystemPropertiesSource()
         */
        default Builder sources(Supplier<? extends ConfigSource> configSource,
                                Supplier<? extends ConfigSource> configSource2,
                                Supplier<? extends ConfigSource> configSource3) {
            sources(List.of(configSource, configSource2, configSource3));
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
        Builder overrides(Supplier<? extends OverrideSource> overridingSource);

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
         * <p>
         * By default a value resolving filter is added to configuration. When this method is called, the filter will
         *  not be added and value resolving will be disabled
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
         * Registers mapping function for specified {@code type}.
         * The last registration of same {@code type} overwrites previous one.
         * Programmatically registered mappers have priority over other options.
         * <p>
         * As another option, mappers are loaded automatically as a {@link java.util.ServiceLoader service}
         * via {@link io.helidon.config.spi.ConfigMapperProvider} SPI unless it is {@link #disableMapperServices() disabled}.
         * <p>
         * And the last option, {@link ConfigMappers built-in mappers} are registered.
         *
         * @param type   class of type the {@code mapper} is registered for
         * @param mapper mapping function
         * @param <T>    type the {@code mapper} is registered for
         * @return an updated builder instance
         * @see #addStringMapper(Class, Function)
         * @see #addMapper(ConfigMapperProvider)
         * @see #disableMapperServices
         */
        <T> Builder addMapper(Class<T> type, Function<Config, T> mapper);

        /**
         * Register a mapping function for specified {@link GenericType}.
         * This is useful for mappers that support specificly typed generics, such as {@code Map<String, Integer>}
         * or {@code Set<Foo<Bar>>}.
         * To support mappers that can map any type (e.g. all cases of {@code Map<String, V>}),
         * use {@link #addMapper(ConfigMapperProvider)} as it gives you full control over which types are supported, through
         * {@link ConfigMapperProvider#mapper(GenericType)}.
         *
         * @param type   generic type to register a mapper for
         * @param mapper mapping function
         * @param <T>    type of the result
         * @return updated builder instance
         */
        <T> Builder addMapper(GenericType<T> type, Function<Config, T> mapper);

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
         * @see #addMapper(ConfigMapperProvider)
         * @see ConfigMappers
         * @see #disableMapperServices
         */
        <T> Builder addStringMapper(Class<T> type, Function<String, T> mapper);

        /**
         * Registers a {@link ConfigMapperProvider} with a map of {@code String} to specified {@code type}.
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
         * @see #addStringMapper(Class, Function)
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
         * Registers a {@link ConfigParser} instance that can be used by config system to parse
         * parse {@link io.helidon.config.spi.ConfigParser.Content} of {@link io.helidon.config.spi.ParsableSource}.
         * Parsers {@link io.helidon.config.spi.ConfigParser#supportedMediaTypes()} is queried
         * in same order as was registered by this method.
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
         * Filters are applied in same order as was registered by the this method, {@link
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
         * Filters are applied in same order as was registered by the {@link #addFilter(ConfigFilter)}, this method,
         * or {@link #addFilter(Supplier)} method.
         * <p>
         * Registered provider's {@link Function#apply(Object)} method is called every time the new Config is created. Eg. when
         * this builder's {@link #build} method creates the {@link Config} or when the new
         * {@link Config#onChange(java.util.function.Consumer)} is fired with new Config instance with its own filter instance
         * is created.
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
         * #addFilter(Function)}, or this method.
         * <p>
         * Registered provider's {@link Function#apply(Object)} method is called every time the new Config is created. Eg. when
         * this builder's {@link #build} method creates the {@link Config} or when the new
         * {@link Config#onChange(java.util.function.Consumer)} change event
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
         * Specifies "observe-on" {@link Executor} to be used by {@link Config#onChange(java.util.function.Consumer)} to deliver
         * new Config instance.
         * Executor is also used to process reloading of config from appropriate {@link ConfigSource source}.
         * <p>
         * By default dedicated thread pool that creates new threads as needed, but
         * will reuse previously constructed threads when they are available is used.
         *
         * @param changesExecutor the executor to use for async delivery of {@link Config#onChange(java.util.function.Consumer)}
         * @return an updated builder instance
         * @see Config#onChange(java.util.function.Consumer)
         */
        Builder changesExecutor(Executor changesExecutor);

        /**
         * Builds new instance of {@link Config}.
         *
         * @return new instance of {@link Config}.
         */
        Config build();

        /**
         * Check if meta configuration is present and if so, update this builder using
         * the meta configuration.
         *
         * @return updated builder instance
         * @see #config(Config)
         */
        default Builder metaConfig() {
            MetaConfig.metaConfig()
                    .ifPresent(this::config);

            return this;
        }

        /**
         * Configure this config builder from meta configuration.
         * <p>
         * The following configuration options are supported in a meta configuration file:
         *
         * <table class="config">
         * <caption>Meta configuration</caption>
         * <tr>
         *     <th>key</th>
         *     <th>default value</th>
         *     <th>description</th>
         *     <th>reference</th>
         * </tr>
         * <tr>
         *     <td>caching.enabled</td>
         *     <td>{@code true}</td>
         *     <td>Enable or disable caching of results of filters.</td>
         *     <td>{@link #disableCaching()}</td>
         * </tr>
         * <tr>
         *     <td>key-resolving.enabled</td>
         *     <td>{@code true}</td>
         *     <td>Enable or disable resolving of placeholders in keys.</td>
         *     <td>{@link #disableKeyResolving()}</td>
         * </tr>
         * <tr>
         *     <td>value-resolving.enabled</td>
         *     <td>{@code true}</td>
         *     <td>Enable or disable resolving of placeholders in values.</td>
         *     <td>{@link #disableValueResolving()}</td>
         * </tr>
         * <tr>
         *     <td>parsers.enabled</td>
         *     <td>{@code true}</td>
         *     <td>Enable or disable parser services.</td>
         *     <td>{@link #disableParserServices()}</td>
         * </tr>
         * <tr>
         *     <td>mappers.enabled</td>
         *     <td>{@code true}</td>
         *     <td>Enable or disable mapper services.</td>
         *     <td>{@link #disableMapperServices()}</td>
         * </tr>
         * <tr>
         *     <td>override-source</td>
         *     <td>none</td>
         *     <td>Configure an override source. Same as config source configuration (see below)</td>
         *     <td>{@link #overrides(java.util.function.Supplier)}</td>
         * </tr>
         * <tr>
         *     <td>sources</td>
         *     <td>Default config sources are prefixed {@code application}, and suffix is the first available of
         *          {@code yaml, conf, json, properties}</td>
         *     <td>Configure config sources to be used by the application. This node contains the array of objects defining
         *          config sources</td>
         *     <td>{@link #addSource(io.helidon.config.spi.ConfigSource)}</td>
         * </tr>
         * </table>
         *
         * Config source configuration options:
         * <table class="config">
         * <caption>Config source</caption>
         * <tr>
         *     <th>key</th>
         *     <th>default value</th>
         *     <th>description</th>
         *     <th>reference</th>
         * </tr>
         * <tr>
         *     <td>type</td>
         *     <td>&nbsp;</td>
         *     <td>Type of a config source - a string supported by a provider.</td>
         *     <td>{@link io.helidon.config.spi.ConfigSourceProvider#create(String, Config)}</td>
         * </tr>
         * <tr>
         *     <td>multi-source</td>
         *     <td>{@code false}</td>
         *     <td>If set to true, the provider creates more than one config source to be added</td>
         *     <td>{@link io.helidon.config.spi.ConfigSourceProvider#createMulti(String, Config)}</td>
         * </tr>
         * <tr>
         *     <td>properties</td>
         *     <td>&nbsp;</td>
         *     <td>Configuration options to configure the config source (meta configuration of a source)</td>
         *     <td>{@link io.helidon.config.spi.ConfigSourceProvider#create(String, Config)},
         *      {@link MetaConfig#configSource(Config)}</td>
         * </tr>
         * <tr>
         *     <td>properties.optional</td>
         *     <td>false</td>
         *     <td>Config sources can be configured to be optional</td>
         *     <td>{@link io.helidon.config.spi.Source#optional()}</td>
         * </tr>
         * <tr>
         *     <td>properties.polling-strategy</td>
         *     <td>&nbsp;</td>
         *     <td>Some config sources can have a polling strategy defined</td>
         *     <td>{@link io.helidon.config.spi.PollableSource.Builder#pollingStrategy(io.helidon.config.spi.PollingStrategy)},
         *          {@link MetaConfig#pollingStrategy(Config)}</td>
         * </tr>
         * <tr>
         *     <td>properties.change-watcher</td>
         *     <td>&nbsp;</td>
         *     <td>Some config sources can have a change watcher defined</td>
         *     <td>{@link io.helidon.config.spi.WatchableSource.Builder#changeWatcher(io.helidon.config.spi.ChangeWatcher)},
         *          {@link MetaConfig#changeWatcher(Config)}</td>
         * </tr>
         * <tr>
         *     <td>properties.retry-policy</td>
         *     <td>&nbsp;</td>
         *     <td>Config sources can have a retry policy defined</td>
         *     <td>{@link io.helidon.config.spi.Source#retryPolicy()},
         *          {@link MetaConfig#retryPolicy(Config)}</td>
         * </tr>
         * </table>
         *
         * Full meta configuration example:
         * <pre>
         * sources:
         *   - type: "system-properties"
         *   - type: "environment-variables"
         *   - type: "file"
         *     properties:
         *       optional: true
         *       path: "conf/dev-application.yaml"
         *       polling-strategy:
         *         type: "regular"
         *       retry-policy:
         *         type: "repeat"
         *         properties:
         *           retries: 5
         *    - type: "classpath"
         *      properties:
         *        optional: true
         *        resource: "application.yaml"
         * </pre>
         *
         * @param metaConfig meta configuration to set this builder up
         * @return updated builder from meta configuration
         */
        Builder config(Config metaConfig);
    }
}
