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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.BuilderConfigMapper.BuilderAccessor;
import io.helidon.config.FactoryMethodConfigMapper.FactoryAccessor;

/**
 * Manages registered Mappers to be used by Config implementation.
 */
class ConfigMapperManager {

    private static final Logger LOGGER = Logger.getLogger(ConfigMapperManager.class.getName());
    private static final String METHOD_FROM = "from";
    private static final String METHOD_VALUE_OF = "valueOf";
    private static final String METHOD_FROM_CONFIG = "fromConfig";
    private static final String METHOD_FROM_STRING = "fromString";
    private static final String METHOD_BUILDER = "builder";
    private static final String METHOD_BUILD = "build";
    private static final String METHOD_PARSE = "parse";
    private static final String METHOD_CREATE = "create";

    private static final Map<Class<?>, Class<?>> REPLACED_TYPES = new HashMap<>();

    static {
        REPLACED_TYPES.put(byte.class, Byte.class);
        REPLACED_TYPES.put(short.class, Short.class);
        REPLACED_TYPES.put(int.class, Integer.class);
        REPLACED_TYPES.put(long.class, Long.class);
        REPLACED_TYPES.put(float.class, Float.class);
        REPLACED_TYPES.put(double.class, Double.class);
        REPLACED_TYPES.put(boolean.class, Boolean.class);
        REPLACED_TYPES.put(char.class, Character.class);
    }

    private final Map<Class<?>, ConfigMapper<?>> mappers;

    ConfigMapperManager(Map<Class<?>, ConfigMapper<?>> mappers) {
        this.mappers = new HashMap<>(mappers);
    }

    /**
     * Transforms the specified {@code Config} node into the target type.
     * <p>
     * The method uses the {@link ConfigMapper} instance associated with the
     * specified {@code type} to convert the {@code Config} subtree. If there is
     * none it uses the Java {@link java.lang.reflect reflection API} on the
     * {@code type} class parameter to search for a public constructor or a
     * public static method to construct an instance of the {@code type} from
     * the provided config {@code node}, using the first of the following it
     * finds to perform the conversion:
     * <ol>
     * <li>the static method {@code T from(Config)};</li>
     * <li>a constructor that accepts a single {@code Config} argument;</li>
     * <li>the static method {@code T valueOf(Config)};</li>
     * <li>the static method {@code T fromConfig(Config)};</li>
     * <li>the static method {@code T from(String)};</li>
     * <li>a constructor that accepts a single {@code String} argument;</li>
     * <li>the static method {@code T valueOf(String)};</li>
     * <li>the static method {@code T fromString(String)};</li>
     * <li>the static method {@code builder()} that returns an instance of a
     * builder class. Generic JavaBean deserialization is applied to the builder
     * instance using config sub-nodes. See the last item below for more details
     * about generic deserialization support. The returned builder object must
     * have the {@code T build()} method.
     * </li>
     * <li>the factory method {@code from} with parameters (loaded from config
     * sub-nodes) that returns a new instance of the {@code type}. The
     * annotation {@link Config.Value} decorates the parameters to customize
     * sub-key and/or default value.
     * </li>
     * <li>a factory constructor with parameters (loaded from config sub-nodes).
     * The annotation {@link Config.Value} decorates parameters to customize
     * sub-key and/or default value.
     * </li>
     * <li>a no-parameter constructor to create a new instance of the
     * {@code type} and apply recursively the same mapping behavior described
     * above on each JavaBean property of such object, a.k.a. JavaBean
     * deserialization, using one of the following:
     * <ol type="a">
     * <li>invoking the {@code type}'s public property setter {@code void set*}
     * method;</li>
     * <li>invoking a method annotated with {@link Config.Value};</li>
     * <li>assigning the value to a public property field</li>
     * </ol>
     * The generic mapping behavior can be customized by {@link Config.Value}
     * and {@link Config.Transient} annotations.
     * </li>
     * </ol>
     * See {@link Config.Value} documentation for more details about generic
     * deserialization feature.
     * <p>
     * Otherwise it throws {@code ConfigMappingException} to indicate there is
     * no appropriate mapper for specified type.
     *
     * @param type   type to which the config node is to be transformed
     * @param config config node to be transformed
     * @param <T>    type to which the config node is to be transformed
     * @return transformed value of type {@code T}; never returns {@code null}
     * @throws MissingValueException  in case the configuration node does not represent an existing configuration node
     * @throws ConfigMappingException in case the mapper fails to map the existing configuration value
     *                                to an instance of a given Java type
     * @see io.helidon.config.Config.Value
     */
    public <T> T map(Class<T> type, Config config) throws MissingValueException, ConfigMappingException {
        type = supportedType(type);
        ConfigMapper<?> converter = mappers.computeIfAbsent(type, this::fallbackConfigMapper);
        return cast(type, converter.apply(config), config.key());
    }

    public static <T> T cast(Class<T> type, Object instance, Config.Key key) throws ConfigMappingException {
        try {
            return type.cast(instance);
        } catch (ClassCastException ex) {
            throw new ConfigMappingException(key,
                                             type,
                                             "Created instance is not assignable to the type.",
                                             ex);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> Class<T> supportedType(Class<T> type) {
        return (Class<T>) REPLACED_TYPES.getOrDefault(type, type);
    }

    private <T> ConfigMapper<T> fallbackConfigMapper(Class<T> type) {
        Optional<ConfigMapper<T>> configMapper;
        //from(Config) method
        configMapper = findStaticMethod(type, METHOD_FROM, Config.class)
                .map(methodHandle -> new ConfigMethodHandleConfigMapper<>(type, "from(Config) method", methodHandle));
        //Config constructor
        if (!configMapper.isPresent()) {
            configMapper = findConstructor(type, Config.class)
                    .map(methodHandle -> new ConfigMethodHandleConfigMapper<>(type, "Config constructor", methodHandle));
        }
        //valueOf(Config) method
        if (!configMapper.isPresent()) {
            configMapper = findStaticMethod(type, METHOD_VALUE_OF, Config.class)
                    .map(methodHandle -> new ConfigMethodHandleConfigMapper<>(type, "valueOf(Config) method", methodHandle));
        }
        //fromConfig(Config) method
        if (!configMapper.isPresent()) {
            configMapper = findStaticMethod(type, METHOD_FROM_CONFIG, Config.class)
                    .map(methodHandle -> new ConfigMethodHandleConfigMapper<>(type, "fromConfig(Config) method", methodHandle));
        }
        //from(String) method
        if (!configMapper.isPresent()) {
            configMapper = findStaticMethod(type, METHOD_FROM, String.class)
                    .map(methodHandle -> new StringMethodHandleConfigMapper<>(type, "from(String) method", methodHandle));
        }
        //create(Config) method
        if (!configMapper.isPresent()) {
            configMapper = findStaticMethod(type, METHOD_CREATE, Config.class)
                    .map(methodHandle -> new ConfigMethodHandleConfigMapper<>(type, "create(Config) method", methodHandle));
        }
        //parse(String) method
        if (!configMapper.isPresent()) {
            configMapper = findStaticMethod(type, METHOD_PARSE, String.class)
                    .map(methodHandle -> new StringMethodHandleConfigMapper<>(type, "parse(String) method", methodHandle));
        }
        //parse(CharSequence) method
        if (!configMapper.isPresent()) {
            configMapper = findStaticMethod(type, METHOD_PARSE, CharSequence.class)
                    .map(methodHandle -> new StringMethodHandleConfigMapper<>(type, "parse(CharSequence) method", methodHandle));
        }
        //String constructor
        if (!configMapper.isPresent()) {
            configMapper = findConstructor(type, String.class)
                    .map(methodHandle -> new StringMethodHandleConfigMapper<>(type, "String constructor", methodHandle));
        }
        //valueOf(String) method
        if (!configMapper.isPresent()) {
            configMapper = findStaticMethod(type, METHOD_VALUE_OF, String.class)
                    .map(methodHandle -> new StringMethodHandleConfigMapper<>(type, "valueOf(String) method", methodHandle));
        }
        //fromString(String) method
        if (!configMapper.isPresent()) {
            configMapper = findStaticMethod(type, METHOD_FROM_STRING, String.class)
                    .map(methodHandle -> new StringMethodHandleConfigMapper<>(type, "fromString(String) method", methodHandle));
        }
        //static builder()
        if (!configMapper.isPresent()) {
            configMapper = findBuilderMethod(type)
                    .map(builderAccessor -> new BuilderConfigMapper<>(type, builderAccessor));
        }
        //static T from(param1, params...)
        if (!configMapper.isPresent()) {
            configMapper = findStaticMethodWithParameters(type, METHOD_FROM)
                    .map(factoryAccessor -> new FactoryMethodConfigMapper<>(type, factoryAccessor));
        }
        //(param1, params...) constructor
        if (!configMapper.isPresent()) {
            configMapper = findConstructorWithParameters(type)
                    .map(factoryAccessor -> new FactoryMethodConfigMapper<>(type, factoryAccessor));
        }
        //generic mapping support
        if (!configMapper.isPresent()) {
            configMapper = findConstructor(type)
                    .map(methodHandle -> new GenericConfigMapper<>(type, methodHandle, this));
        }
        //or else throw ConfigMappingException
        return configMapper.orElseGet(() -> new UnsupportedTypeConfigMapper<>(type));
    }

    private static Optional<MethodHandle> findConstructor(Class<?> type, Class<?>... parameterTypes) {
        try {
            Constructor<?> constructor = type.getConstructor(parameterTypes);
            if (checkConstructor(constructor, parameterTypes.length > 0)) {
                return Optional.of(MethodHandles.publicLookup().unreflectConstructor(constructor));
            } else {
                LOGGER.log(Level.FINEST,
                           () -> "Class " + type.getName() + " constructor with parameters "
                                   + Arrays.toString(parameterTypes)
                                   + " cannot be used.");
            }
        } catch (NoSuchMethodException ex) {
            LOGGER.log(Level.FINEST,
                       ex,
                       () -> "Class " + type.getName() + " does not have a constructor with parameters "
                               + Arrays.toString(parameterTypes) + ".");
        } catch (IllegalAccessException ex) {
            LOGGER.log(Level.FINER,
                       ex,
                       () -> "Access checking fails on " + type.getName()
                               + " class, constructor with parameters " + Arrays.toString(parameterTypes) + ".");
        }
        return Optional.empty();
    }

    private <T> Optional<FactoryAccessor<T>> findConstructorWithParameters(Class<T> type) {
        AtomicReference<Constructor> foundConstructor = new AtomicReference<>();
        for (Constructor constructor : type.getConstructors()) {
            if (checkConstructor(constructor, true)) {
                if (foundConstructor.get() != null) {
                    LOGGER.log(Level.WARNING,
                               () -> "Class " + type.getName() + " contains more than one constructor with parameters."
                                       + " Any will be used to initialize the type.");
                    return Optional.empty();
                }
                foundConstructor.set(constructor);
            }
        }
        if (foundConstructor.get() == null) {
            return Optional.empty();
        } else {
            return findConstructor(type, foundConstructor.get().getParameterTypes())
                    .map(handle -> new FactoryAccessor<>(this,
                                                         type,
                                                         handle,
                                                         foundConstructor.get().getParameters()));
        }
    }

    private static Optional<MethodHandle> findStaticMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = type.getMethod(methodName, parameterTypes);
            if (checkMethod(method, true, type, methodName, parameterTypes.length > 0)) {
                return unreflect(method);
            } else {
                LOGGER.log(Level.FINEST,
                           () -> "Class " + type.getName() + " method '" + methodName
                                   + "' with parameters " + Arrays.toString(parameterTypes) + " cannot be used.");
            }
        } catch (NoSuchMethodException ex) {
            LOGGER.log(Level.FINEST,
                       ex,
                       () -> "Class " + type.getName() + " does not have a method named '" + methodName
                               + "' with parameters " + Arrays.toString(parameterTypes) + ".");
        }
        return Optional.empty();
    }

    private static Optional<Method> findMethod(Class<?> type, String methodName, boolean isStatic, Class<?> returnType,
                                               Class<?>... parameterTypes) {
        try {
            Method method = type.getMethod(methodName, parameterTypes);
            if (checkMethod(method, isStatic, returnType, methodName, parameterTypes.length > 0)) {
                return Optional.of(method);
            } else {
                LOGGER.log(Level.FINEST,
                           () -> "Class " + type.getName() + " method '" + methodName
                                   + "' with parameters " + Arrays.toString(parameterTypes) + " cannot be used.");
            }
        } catch (NoSuchMethodException ex) {
            LOGGER.log(Level.FINEST,
                       ex,
                       () -> "Class " + type.getName() + " does not have a method named '" + methodName
                               + "' with parameters " + Arrays.toString(parameterTypes) + ".");
        }
        return Optional.empty();
    }

    private static Optional<MethodHandle> unreflect(Method method) {
        try {
            return Optional.of(MethodHandles.publicLookup().unreflect(method));
        } catch (IllegalAccessException ex) {
            LOGGER.log(Level.FINER,
                       ex,
                       () -> "Access checking fails on " + method.getDeclaringClass() + " class, method '" + method.getName()
                               + "' with parameters " + Arrays.asList(method.getParameters()) + ".");
        }
        return Optional.empty();
    }

    /**
     * Check if constructor can be used to deserialization.
     */
    private static boolean checkConstructor(Constructor constructor, boolean hasParams) {
        return (
                Modifier.isPublic(constructor.getModifiers())
                        && !constructor.isAnnotationPresent(Config.Transient.class)
                        && (constructor.getParameterCount() > 0) == hasParams);
    }

    /**
     * Check if method can be used to deserialization.
     */
    private static boolean checkMethod(Method method, boolean isStatic, Class<?> returnType, String name, boolean hasParams) {
        return (
                Modifier.isPublic(method.getModifiers())
                        && Modifier.isStatic(method.getModifiers()) == isStatic
                        && !method.isAnnotationPresent(Config.Transient.class)
                        && method.getName().equals(name)
                        && (returnType == null || returnType.isAssignableFrom(method.getReturnType()))
                        && (method.getParameterCount() > 0) == hasParams);
    }

    public static <T> Optional<MethodHandle> findBuilderBuildHandler(Class<T> type, Class<?> builderType) {
        return findMethod(builderType, METHOD_BUILD, false, type)
                .map(ConfigMapperManager::unreflect)
                .flatMap(methodHandle -> methodHandle);
    }

    /**
     * Find builder method that conforms to design pattern expected by {@link BuilderConfigMapper}.
     */
    private <T> Optional<BuilderAccessor<T>> findBuilderMethod(Class<T> type) {
        return findMethod(type, METHOD_BUILDER, true, null)
                .map(builderMethod ->
                             unreflect(builderMethod)
                                     .map(builderHandler ->
                                                  findBuilderBuildHandler(type, builderMethod.getReturnType())
                                                          .map(buildHandler ->
                                                                       new BuilderAccessor<>(this,
                                                                                             builderMethod.getReturnType(),
                                                                                             builderHandler,
                                                                                             type,
                                                                                             buildHandler)))
                                     .flatMap(builderAccessor -> builderAccessor))
                .flatMap(builderAccessor -> builderAccessor);
    }

    private <T> Optional<FactoryAccessor<T>> findStaticMethodWithParameters(Class<T> type, String methodName) {
        AtomicReference<Method> foundMethod = new AtomicReference<>();
        for (Method method : type.getMethods()) {
            if (checkMethod(method, true, type, methodName, true)) {
                if (foundMethod.get() != null) {
                    LOGGER.log(Level.WARNING,
                               () -> "Class " + type.getName() + " contains more than one static factory method '"
                                       + methodName + "' with parameters. Any will be used to initialize the type.");
                    return Optional.empty();
                }
                foundMethod.set(method);
            }
        }

        if (foundMethod.get() == null) {
            return Optional.empty();
        } else {
            return findStaticMethod(type, methodName, foundMethod.get().getParameterTypes())
                    .map(handle -> new FactoryAccessor<>(this,
                                                         type,
                                                         handle,
                                                         foundMethod.get().getParameters()));
        }
    }

    private abstract static class MethodHandleConfigMapper<T, P> implements ConfigMapper<T> {
        private final Class<T> type;
        private final String methodName;
        private final MethodHandle methodHandle;

        private MethodHandleConfigMapper(Class<T> type, String methodName, MethodHandle methodHandle) {
            this.type = type;
            this.methodName = methodName;
            this.methodHandle = methodHandle;
        }

        protected abstract P invokeParameter(Config config);

        @Override
        public T apply(Config config) throws ConfigMappingException, MissingValueException {
            try {
                return type.cast(methodHandle.invoke(invokeParameter(config)));
            } catch (ConfigMappingException ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new ConfigMappingException(config.key(), type,
                                                 "Invocation of " + methodName + " has failed with an exception.", ex);
            }
        }
    }

    private static class ConfigMethodHandleConfigMapper<T> extends MethodHandleConfigMapper<T, Config> {
        private ConfigMethodHandleConfigMapper(Class<T> type, String methodName, MethodHandle methodHandle) {
            super(type, methodName, methodHandle);
        }

        @Override
        protected Config invokeParameter(Config config) {
            return config;
        }
    }

    private static class StringMethodHandleConfigMapper<T> extends MethodHandleConfigMapper<T, String> {
        private StringMethodHandleConfigMapper(Class<T> type, String methodName, MethodHandle methodHandle) {
            super(type, methodName, methodHandle);
        }

        @Override
        protected String invokeParameter(Config config) {
            return config.asString();
        }
    }

    /**
     * Failing implementation of {@link ConfigMapper} that always throw {@link ConfigMappingException}.
     *
     * @param <T> type to be the config node transformed to
     */
    private static class UnsupportedTypeConfigMapper<T> implements ConfigMapper<T> {

        private final Class<T> type;

        private UnsupportedTypeConfigMapper(Class<T> type) {
            this.type = type;
        }

        @Override
        public T apply(Config config) throws ConfigMappingException, MissingValueException {
            throw new ConfigMappingException(config.key(),
                                             type,
                                             "Unsupported Java type, no compatible config value mapper found.");
        }
    }

}
