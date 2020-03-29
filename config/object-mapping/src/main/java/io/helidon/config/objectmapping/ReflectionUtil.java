/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
package io.helidon.config.objectmapping;

import java.lang.invoke.MethodType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigMappingException;

/**
 * Utilities for reflective access.
 */
final class ReflectionUtil {
    private static final Map<Class<?>, Class<?>> REPLACED_TYPES = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger(ReflectionUtil.class.getName());
    private static final String METHOD_BUILDER = "builder";
    private static final String METHOD_BUILD = "build";
    private static final String CLASS_BUILDER = "Builder";

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

    private ReflectionUtil() {
    }

    @SuppressWarnings("unchecked")
    static <T> Class<T> supportedType(Class<T> type) {
        return (Class<T>) REPLACED_TYPES.getOrDefault(type, type);
    }

    static Optional<HelidonMethodHandle> findStaticMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = type.getMethod(methodName, parameterTypes);
            if (checkMethod(method, true, type, methodName, parameterTypes.length > 0)) {
                return Optional.of(HelidonMethodHandle.create(type, method));
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

    static Optional<HelidonMethodHandle> findConstructor(Class<?> type, Class<?>... parameterTypes) {
        try {
            Constructor<?> constructor = type.getConstructor(parameterTypes);
            if (checkConstructor(constructor, parameterTypes.length > 0)) {
                return Optional.of(HelidonMethodHandle.create(type, constructor));
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
        }
        return Optional.empty();
    }

    /**
     * Find builder method that conforms to design pattern of builder.
     * e.g. Type t = Type.builder().build();
     */
    static <T> Optional<BuilderAccessor<T>> findBuilderMethod(Class<T> type) {
        return findMethod(type, METHOD_BUILDER, true, null)
                .flatMap(builderMethod -> {
                    HelidonMethodHandle builderHandler = HelidonMethodHandle.create(type, builderMethod);
                    return findBuilderBuildHandler(type, builderMethod.getReturnType()).map(
                            buildHandler ->
                                    new BuilderAccessor<>(builderMethod.getReturnType(),
                                                          builderHandler,
                                                          type,
                                                          buildHandler));
                });
    }

    /**
     * Find builder constructor that conforms to design pattern of builder.
     * e.g. Type.Builder builder = new Type.Builder();
     */
    static <T> Optional<BuilderAccessor<T>> findBuilderConstructor(Class<T> type) {
        Class<?>[] declaredClasses = type.getDeclaredClasses();
        for (Class<?> declaredClass : declaredClasses) {
            if (CLASS_BUILDER.equals(declaredClass.getSimpleName())) {
                return findConstructor(declaredClass)
                        .flatMap(constructor -> findBuilderBuildHandler(type, declaredClass)
                                .map(buildHandler -> new BuilderAccessor<>(declaredClass,
                                                                           constructor,
                                                                           type,
                                                                           buildHandler)
                                ));
            }
        }

        return Optional.empty();
    }

    static <T> Optional<FactoryAccessor<T>> findStaticMethodWithParameters(Class<T> type, String methodName) {
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
                    .map(handle -> new FactoryAccessor<>(type,
                                                         handle,
                                                         foundMethod.get().getParameters()));
        }
    }

    static <T> Optional<FactoryAccessor<T>> findConstructorWithParameters(Class<T> type) {
        AtomicReference<Constructor<?>> foundConstructor = new AtomicReference<>();
        for (Constructor<?> constructor : type.getConstructors()) {
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
                    .map(handle -> new FactoryAccessor<>(type,
                                                         handle,
                                                         foundConstructor.get().getParameters()));
        }
    }

    static <T> Optional<HelidonMethodHandle> findBuilderBuildHandler(Class<T> type, Class<?> builderType) {
        return findMethod(builderType, METHOD_BUILD, false, type)
                .map(it -> HelidonMethodHandle.create(type, it));
    }

    /**
     * Check if constructor can be used for deserialization.
     */
    private static boolean checkConstructor(Constructor<?> constructor, boolean hasParams) {
        return Modifier.isPublic(constructor.getModifiers())
                && !constructor.isAnnotationPresent(Transient.class)
                && ((constructor.getParameterCount() > 0) == hasParams);
    }

    /**
     * Check if method can be used for deserialization.
     */
    private static boolean checkMethod(Method method, boolean isStatic, Class<?> returnType, String name, boolean hasParams) {
        return Modifier.isPublic(method.getModifiers())
                && (Modifier.isStatic(method.getModifiers()) == isStatic)
                && !method.isAnnotationPresent(Transient.class)
                && method.getName().equals(name)
                && ((returnType == null) || returnType.isAssignableFrom(method.getReturnType()))
                && ((method.getParameterCount() > 0) == hasParams);
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

    /**
     * The class covers work with factory method.
     *
     * @param <T> type of target java bean
     */
    static class FactoryAccessor<T> {
        private final Class<T> type;
        private final HelidonMethodHandle handle;
        private final LinkedHashMap<String, PropertyWrapper<?>> parameterValueProviders;

        FactoryAccessor(Class<T> type,
                        HelidonMethodHandle handle,
                        Parameter[] parameters) {
            this.type = type;
            this.handle = handle;

            this.parameterValueProviders = initParameterValueProviders(parameters);
        }

        public T create(Config configNode) {
            List<Object> args = createArguments(configNode);

            try {
                Object obj = handle.invoke(args);
                return type.cast(obj);
            } catch (ConfigException ex) {
                throw ex;
            } catch (Throwable throwable) {
                throw new ConfigException("Unable to create '" + type.getName() + "' instance.", throwable);
            }
        }

        private List<Object> createArguments(Config configNode) {
            List<Object> arguments = new ArrayList<>(parameterValueProviders.size());

            parameterValueProviders.forEach((name, propertyWrapper) -> {
                Config subConfig = configNode.get(name);
                Object argument = propertyWrapper
                        .get(subConfig)
                        .orElseThrow(() -> new ConfigMappingException(configNode.key(),
                                                                      type,
                                                                      "Missing value for parameter '" + name + "'."));
                arguments.add(argument);
            });

            return arguments;
        }

        private static LinkedHashMap<String, PropertyWrapper<?>> initParameterValueProviders(Parameter[] parameters) {
            LinkedHashMap<String, PropertyWrapper<?>> parameterValueProvider = new LinkedHashMap<>();

            for (Parameter parameter : parameters) {
                String name = propertyName(parameter, parameter::getName);
                parameterValueProvider.put(name, createPropertyWrapper(name, parameter));
            }

            return parameterValueProvider;
        }

        private static PropertyWrapper<?> createPropertyWrapper(String name, Parameter parameter) {
            Value value = parameter.getAnnotation(Value.class);

            final Class<?> propertyType = parameter.getType();
            Class<?> configAsType = propertyType;
            boolean list = List.class.isAssignableFrom(configAsType);
            if (list) {
                Type genType = parameter.getParameterizedType();
                if (genType instanceof ParameterizedType) {
                    configAsType = (Class<?>) ((ParameterizedType) genType).getActualTypeArguments()[0];
                } else {
                    throw new ConfigException("Unable to find generic type of List on parameter type: " + parameter);
                }
            }

            return new PropertyWrapper<>(name,
                                         propertyType,
                                         configAsType,
                                         list,
                                         createDefaultSupplier(name, value));
        }
    }

    /**
     * The class covers work with {@code T} builder.
     *
     * @param <T> type of target java bean
     */
    static class BuilderAccessor<T> {
        private final Class<?> builderType;
        private final HelidonMethodHandle builderHandler;
        private final Class<T> buildType;
        private final HelidonMethodHandle buildHandler;
        private final Collection<PropertyAccessor<?>> builderAccessors;

        BuilderAccessor(Class<?> builderType,
                        HelidonMethodHandle builderHandler,
                        Class<T> buildType,
                        HelidonMethodHandle buildHandler) {
            this.builderType = builderType;
            this.builderHandler = builderHandler;
            this.buildType = buildType;
            this.buildHandler = buildHandler;

            builderAccessors = getBeanProperties(builderType);
        }

        public T create(Config config) {
            try {
                Object builder = builderType.cast(builderHandler.invoke(List.of()));

                for (PropertyAccessor<?> builderAccessor : builderAccessors) {
                    builderAccessor.set(builder, config.get(builderAccessor.name()));
                }

                return buildType.cast(buildHandler.invoke(List.of(builder)));
            } catch (ConfigMappingException ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new ConfigMappingException(
                        config.key(),
                        buildType,
                        "Builder java bean initialization has failed with an exception.",
                        ex);
            }
        }
    }

    /**
     * Single JavaBean property accessor used to set new value.
     */
    static class PropertyAccessor<T> {
        private final String name;
        private final HelidonMethodHandle handle;
        private final boolean hasValueAnnotation;
        private final PropertyWrapper<T> propertyWrapper;

        PropertyAccessor(String name, Class<T> propertyType,
                         Class<?> configAsType,
                         boolean list,
                         HelidonMethodHandle handle,
                         Value value) {
            this.name = name;
            this.handle = handle;

            hasValueAnnotation = value != null;
            propertyWrapper = new PropertyWrapper<>(name,
                                                    propertyType,
                                                    configAsType,
                                                    list,
                                                    createDefaultSupplier(name, value));
        }

        public String name() {
            return name;
        }

        void set(Object instance, Config configNode) {
            propertyWrapper.get(configNode)
                    .ifPresent(value -> setImpl(instance, value));
        }

        private void setImpl(Object instance, Object value) {
            try {
                handle.invoke(instance, value);
            } catch (ConfigException ex) {
                throw ex;
            } catch (Throwable throwable) {
                throw new ConfigException("Unable to set '" + name + "' property.", throwable);
            }
        }

        HelidonMethodHandle handle() {
            return handle;
        }

        boolean hasValueAnnotation() {
            return hasValueAnnotation;
        }

        void setValueAnnotation(Value value) {
            propertyWrapper.setDefaultSupplier(createDefaultSupplier(name, value));
        }
    }

    static <T> BiFunction<Class<T>, Config, T> createDefaultSupplier(String name, Value annotation) {
        if (annotation != null) {
            if (!annotation.withDefaultSupplier().equals(Value.None.class)) {
                return (type, mapperManager) -> {
                    try {
                        return type.cast(annotation
                                                 .withDefaultSupplier()
                                                 .getDeclaredConstructor()
                                                 .newInstance()
                                                 .get());
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                            | NoSuchMethodException ex) {
                        throw new ConfigException("Error creating default value supplier.", ex);
                    }
                };
            } else if (!annotation.withDefault().equals(Value.None.VALUE)) {
                return (type, config) -> {
                    try {
                        return config.convert(type, annotation.withDefault());
                    } catch (ConfigMappingException e) {
                        throw new ConfigMappingException(Config.Key.create(name),
                                                         "Provided default value \""
                                                                 + annotation.withDefault()
                                                                 + "\" cannot be converted to correct type \""
                                                                 + type.getName() + "\"",
                                                         e);
                    }
                };
            }
        }
        return null;
    }

    static <T> Collection<PropertyAccessor<?>> getBeanProperties(Class<T> type) {
        return getPropertyAccessors(type).values();
    }

    static <T> Map<String, PropertyAccessor<?>> getPropertyAccessors(Class<T> type) {
        Set<String> transientProps = new HashSet<>();
        Map<String, PropertyAccessor<?>> propertyAccessors = new HashMap<>();

        initMethods(type, transientProps, propertyAccessors);
        initFields(type, transientProps, propertyAccessors);

        return propertyAccessors;
    }

    static <T> void initMethods(Class<T> type,
                                Set<String> transientProps,
                                Map<String, PropertyAccessor<?>> propertyAccessors) {
        for (Method method : type.getMethods()) {
            if (!isSetter(type, method)) {
                continue;
            }

            String name = propertyName(method);
            if (isTransient(method, "single setter " + method.getName())) {
                transientProps.add(name);
                continue;
            }

            propertyAccessors.put(name, createPropertyAccessor(type, name, method));
        }
    }

    static <T> void initFields(Class<T> type, Set<String> transientProps,
                               Map<String, PropertyAccessor<?>> propertyAccessors) {
        for (Field field : type.getFields()) {
            if (!isAccessible(field)) {
                continue;
            }

            String name = propertyName(field, field::getName);
            if (isTransient(field, "single field " + field.getName())) {
                if (propertyAccessors.containsKey(name)) {
                    throw new ConfigException("Illegal use of both @Value (method) and @Transient (field) "
                                                      + "annotations on single '" + name + "' property.");
                }
                continue;
            }

            if (transientProps.contains(name)) {
                if (field.isAnnotationPresent(Value.class)) {
                    throw new ConfigException("Illegal use of both @Value (field) and @Transient (method) "
                                                      + "annotations on single '" + name + "' property.");
                }
                continue;
            }

            if (propertyAccessors.containsKey(name)) {
                //just try to use @Value on field (if not already used from method)
                if (field.getAnnotation(Value.class) != null) {
                    PropertyAccessor<?> propertyAccessor = propertyAccessors.get(name);
                    if (propertyAccessor.hasValueAnnotation()) {
                        LOGGER.fine(() -> "Annotation @Value on '" + name
                                + "' field is ignored because setter method already has one.");
                    } else {
                        propertyAccessor.setValueAnnotation(field.getAnnotation(Value.class));
                    }
                }
            } else {
                propertyAccessors.put(name, createPropertyAccessor(type, name, field));
            }
        }
    }

    @SuppressWarnings("unchecked")
    static <T> PropertyAccessor<T> createPropertyAccessor(Class<T> type,
                                                          String name,
                                                          Method method) {
        final Class<T> propertyType = (Class<T>) method.getParameterTypes()[0];
        Class<?> configAsType = propertyType;

        boolean list = List.class.isAssignableFrom(configAsType);
        if (list) {
            Type genType = method.getGenericParameterTypes()[0];
            if (genType instanceof ParameterizedType) {
                configAsType = (Class<?>) ((ParameterizedType) genType).getActualTypeArguments()[0];
            } else {
                throw new ConfigException("Unable to find generic type of List on setter parameter: " + method);
            }
        }

        return new PropertyAccessor<>(name,
                                      propertyType,
                                      configAsType,
                                      list,
                                      HelidonMethodHandle.create(type, method),
                                      method.getAnnotation(Value.class));

    }

    @SuppressWarnings("unchecked")
    static <T> PropertyAccessor<T> createPropertyAccessor(Class<T> type,
                                                          String name,
                                                          Field field) {
        try {
            final Class<T> propertyType = (Class<T>) field.getType();
            Class<?> configAsType = propertyType;

            boolean list = List.class.isAssignableFrom(configAsType);
            if (list) {
                Type genType = field.getGenericType();
                if (genType instanceof ParameterizedType) {
                    configAsType = (Class<?>) ((ParameterizedType) genType).getActualTypeArguments()[0];
                } else {
                    throw new ConfigException("Unable to find generic type of List on field type: " + field);
                }
            }

            return new PropertyAccessor<>(name,
                                          propertyType,
                                          configAsType,
                                          list,
                                          HelidonMethodHandle.create(type, field),
                                          field.getAnnotation(Value.class));
        } catch (ClassCastException ex) {
            throw new ConfigException("Cannot access field: " + field, ex);
        }
    }

    static boolean isTransient(AnnotatedElement annotated, String description) throws ConfigException {
        if (annotated.isAnnotationPresent(Transient.class)) {
            if (annotated.isAnnotationPresent(Value.class)) {
                throw new ConfigException("Illegal use of both @Value and @Transient annotations on '"
                                                  + description + "'");
            } else {
                return true;
            }
        }
        return false;
    }

    static boolean isAccessible(Field field) {
        return !Modifier.isFinal(field.getModifiers());
    }

    static boolean isSetter(Class<?> type, Method method) {
        if (method.getParameterCount() != 1) {
            // setter can only have a single parameter
            return false;
        }
        if (method.isAnnotationPresent(Value.class)) {
            // explicitly annotated with our Value annotation
            return true;
        }

        // we must ignore methods from Object itself
        if (method.getDeclaringClass().equals(Object.class)) {
            return false;
        }

        // we either look for "void setSometing(T t)" or "void something(T t)"
        if (method.getReturnType().equals(void.class)) {
            return true;
        }

        // Fluent API approach (return this or a new instance of the class)
        return method.getReturnType().equals(type);
    }

    static String propertyName(Method method) {
        return Optional.ofNullable(method.getAnnotation(Value.class))
                .map(Value::key)
                .filter(string -> !string.isEmpty())
                .orElseGet(() -> {
                    String result = method.getName();
                    if (result.startsWith("set") && (result.length() > 3)) {
                        result = decapitalize(result.substring("set".length()));
                    }
                    return result;
                });
    }

    static String propertyName(AnnotatedElement element, Supplier<String> nameSupplier) {
        return Optional.ofNullable(element.getAnnotation(Value.class))
                .map(Value::key)
                .filter(key -> !key.isEmpty())
                .orElseGet(nameSupplier);
    }

    static String decapitalize(String name) {
        if (Character.isLowerCase(name.charAt(0))) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /**
     * The class covers work with a single property.
     *
     * @param <T> property type
     */
    static class PropertyWrapper<T> {
        private final String name;
        private final Class<T> propertyType;
        private final Class<?> configAsType;
        private final boolean list;
        private BiFunction<Class<T>, Config, T> defaultSupplier;

        PropertyWrapper(String name,
                        Class<T> propertyType,
                        Class<?> configAsType,
                        boolean list,
                        BiFunction<Class<T>, Config, T> defaultSupplier) {
            this.name = name;
            this.propertyType = supportedType(propertyType);
            this.configAsType = configAsType;
            this.list = list;
            this.defaultSupplier = defaultSupplier;
        }

        Optional<T> get(Config configNode) {
            try {
                if (configNode.exists()) {
                    if (list) {
                        return Optional.of(propertyType.cast(configNode.asList(configAsType).get()));
                    } else {
                        return Optional.of(propertyType.cast(configNode.as(configAsType).get()));
                    }
                } else {
                    if (defaultSupplier != null) {
                        return Optional.ofNullable(defaultSupplier.apply(propertyType, configNode));
                    } else {
                        return Optional.empty();
                    }
                }
            } catch (ConfigException ex) {
                throw ex;
            } catch (Throwable throwable) {
                throw new ConfigException("Unable to set '" + name + "' property.", throwable);
            }
        }

        void setDefaultSupplier(BiFunction<Class<T>, Config, T> defaultSupplier) {
            this.defaultSupplier = defaultSupplier;
        }
    }

    static class FieldMethodHandle implements HelidonMethodHandle {
        private final Field field;
        private final Class<?> type;

        FieldMethodHandle(Class<?> type, Field field) {
            this.type = type;
            this.field = field;
        }

        @Override
        public Object invoke(List<Object> params) {
            try {
                field.set(params.get(0), params.get(1));
                return null;
            } catch (IllegalAccessException e) {
                throw new ConfigException("Field " + field + " is not accessible. Cannot set value", e);
            }
        }

        @Override
        public MethodType type() {
            return MethodType.methodType(Void.class, type, field.getType());
        }
    }

    static class StaticMethodHandle implements HelidonMethodHandle {
        private final Method method;

        StaticMethodHandle(Method method) {
            this.method = method;
        }

        @Override
        public Object invoke(List<Object> params) {
            try {
                return method.invoke(null, params.toArray(new Object[0]));
            } catch (IllegalAccessException e) {
                throw new ConfigException("Method " + method + " is not accessible. Cannot invoke", e);
            } catch (InvocationTargetException e) {
                throw new ConfigException("Failed to invoke method " + method, e);
            }
        }

        @Override
        public MethodType type() {
            return MethodType.methodType(method.getReturnType(), method.getParameterTypes());
        }
    }

    static class ConstructorMethodHandle implements HelidonMethodHandle {
        private final Class<?> type;
        private final Constructor<?> constructor;

        ConstructorMethodHandle(Class<?> type, Constructor<?> constructor) {
            this.type = type;
            this.constructor = constructor;
        }

        @Override
        public Object invoke(List<Object> params) {
            try {
                return constructor.newInstance(params.toArray(new Object[0]));
            } catch (IllegalAccessException e) {
                throw new ConfigException("Constructor " + constructor + " is not accessible. Cannot invoke", e);
            } catch (InvocationTargetException e) {
                throw new ConfigException("Failed to invoke constructor " + constructor, e);
            } catch (InstantiationException e) {
                throw new ConfigException("Failed to instantiate class using constructor " + constructor, e);
            } catch (IllegalArgumentException e) {
                throw new ConfigException("Parameters mismatch for constructor " + constructor, e);
            }
        }

        @Override
        public MethodType type() {
            return MethodType.methodType(type);
        }
    }

    public static class InstanceMethodHandle implements HelidonMethodHandle {
        private Class<?> type;
        private final Method method;

        InstanceMethodHandle(Class<?> type, Method method) {
            this.type = type;
            this.method = method;
        }

        @Override
        public Object invoke(List<Object> params) {
            try {
                // first is instance
                Object instance = params.get(0);
                List<Object> mutableParams = new ArrayList<>(params);
                mutableParams.remove(0);
                return method.invoke(params.get(0), mutableParams.toArray(new Object[0]));
            } catch (IllegalAccessException e) {
                throw new ConfigException("Method " + method + " is not accessible. Cannot invoke", e);
            } catch (InvocationTargetException e) {
                throw new ConfigException("Failed to invoke method " + method, e);
            }
        }

        @Override
        public MethodType type() {
            List<Class<?>> paramTypes = new ArrayList<>();
            paramTypes.add(type);
            paramTypes.addAll(Arrays.asList(method.getParameterTypes()));
            return MethodType.methodType(method.getReturnType(), paramTypes);
        }
    }
}
