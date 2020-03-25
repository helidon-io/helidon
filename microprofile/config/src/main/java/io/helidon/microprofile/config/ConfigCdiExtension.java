/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.config;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessObserverMethod;
import javax.inject.Provider;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.NativeImageHelper;
import io.helidon.config.Config;
import io.helidon.config.MissingValueException;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Extension to enable config injection in CDI container (all of {@link Config}, {@link org.eclipse.microprofile.config.Config}
 * and {@link ConfigProperty}).
 */
public class ConfigCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(ConfigCdiExtension.class.getName());
    private static final Annotation CONFIG_PROPERTY_LITERAL = new ConfigProperty() {
        @Override
        public String name() {
            return "";
        }

        @Override
        public String defaultValue() {
            return UNCONFIGURED_VALUE;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return ConfigProperty.class;
        }
    };

    // we must do manual boxing of primitive types, to make sure the injection points match
    // the producers
    private static final Map<Class<?>, Class<?>> REPLACED_TYPES = new HashMap<>();

    static {
        HelidonFeatures.register(HelidonFlavor.MP, "Config");

        // this code is duplicated in mapper manager in Config
        REPLACED_TYPES.put(byte.class, Byte.class);
        REPLACED_TYPES.put(short.class, Short.class);
        REPLACED_TYPES.put(int.class, Integer.class);
        REPLACED_TYPES.put(long.class, Long.class);
        REPLACED_TYPES.put(float.class, Float.class);
        REPLACED_TYPES.put(double.class, Double.class);
        REPLACED_TYPES.put(boolean.class, Boolean.class);
        REPLACED_TYPES.put(char.class, Character.class);
    }

    private final List<InjectionPoint> ips = new LinkedList<>();
    private Config runtimeHelidonConfig;

    /**
     * Constructor invoked by CDI container.
     */
    public ConfigCdiExtension() {
        LOGGER.fine("ConfigCdiExtension instantiated");
    }

    private void harvestConfigPropertyInjectionPointsFromEnabledBean(@Observes ProcessBean<?> event) {
        Bean<?> bean = event.getBean();
        Set<InjectionPoint> beanInjectionPoints = bean.getInjectionPoints();
        if (beanInjectionPoints != null) {
            for (InjectionPoint beanInjectionPoint : beanInjectionPoints) {
                if (beanInjectionPoint != null) {
                    Set<Annotation> qualifiers = beanInjectionPoint.getQualifiers();
                    assert qualifiers != null;
                    for (Annotation qualifier : qualifiers) {
                        if (qualifier instanceof ConfigProperty) {
                            ips.add(beanInjectionPoint);
                        }
                    }
                }
            }
        }
    }

    private <X> void harvestConfigPropertyInjectionPointsFromEnabledObserverMethod(@Observes ProcessObserverMethod<?, X> event,
                                                                                   BeanManager beanManager) {
        AnnotatedMethod<X> annotatedMethod = event.getAnnotatedMethod();
        List<AnnotatedParameter<X>> annotatedParameters = annotatedMethod.getParameters();
        if (annotatedParameters != null) {
            for (AnnotatedParameter<?> annotatedParameter : annotatedParameters) {
                if ((annotatedParameter != null)
                        && !annotatedParameter.isAnnotationPresent(Observes.class)) {
                    InjectionPoint injectionPoint = beanManager.createInjectionPoint(annotatedParameter);
                    Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                    assert qualifiers != null;
                    for (Annotation qualifier : qualifiers) {
                        if (qualifier instanceof ConfigProperty) {
                            ips.add(injectionPoint);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Register a config producer bean for each {@link org.eclipse.microprofile.config.inject.ConfigProperty} injection.
     *
     * @param abd event from CDI container
     */
    private void registerConfigProducer(@Observes AfterBeanDiscovery abd) {
        // we also must support injection of Config itself
        abd.addBean()
                .addTransitiveTypeClosure(org.eclipse.microprofile.config.Config.class)
                .beanClass(org.eclipse.microprofile.config.Config.class)
                .scope(ApplicationScoped.class)
                .createWith(creationalContext -> new SerializableConfig());

        abd.addBean()
                .addTransitiveTypeClosure(Config.class)
                .beanClass(Config.class)
                .scope(ApplicationScoped.class)
                .createWith(creationalContext -> (Config) ConfigProvider.getConfig());

        Set<Type> types = ips.stream()
                .map(InjectionPoint::getType)
                .map(it -> {
                    if (it instanceof Class) {
                        Class<?> clazz = (Class<?>) it;
                        if (clazz.isPrimitive()) {
                            return REPLACED_TYPES.getOrDefault(clazz, clazz);
                        }
                    }
                    return it;
                })
                .collect(Collectors.toSet());

        types.forEach(type -> {
            abd.addBean()
                    .addType(type)
                    .scope(Dependent.class)
                    .addQualifier(CONFIG_PROPERTY_LITERAL)
                    .produceWith(it -> produce(it.select(InjectionPoint.class).get()));
        });
    }

    /**
     * Validate all injection points are valid.
     *
     * @param add event from CDI container
     */
    private void validate(@Observes AfterDeploymentValidation add, BeanManager beanManager) {
        CreationalContext<?> cc = beanManager.createCreationalContext(null);
        try {
            ips.forEach(ip -> {
                try {
                    beanManager.getInjectableReference(ip, cc);
                } catch (Exception e) {
                    add.addDeploymentProblem(e);
                }
            });
        } finally {
            cc.release();
        }
        ips.clear();
    }

    private Object produce(InjectionPoint ip) {
        ConfigProperty annotation = ip.getAnnotated().getAnnotation(ConfigProperty.class);
        String injectedName = injectedName(ip);
        String fullPath = ip.getMember().getDeclaringClass().getName()
                + "." + injectedName;
        String configKey = configKey(annotation, fullPath);

        if (NativeImageHelper.isBuildTime()) {
            // this is build-time of native-image - e.g. run from command line or maven
            // logging may not be working/configured to deliver this message as it should
            System.err.println("You are accessing configuration key '" + configKey + "' during"
                                       + " container initialization. This will not work nice with Graal native-image");
        }

        Type type = ip.getType();
        /*
             Supported types
             group x:
                primitive types + String
                any java class (except for parameterized types)
             group y = group x + the ones listed here:
                List<x> - where x is one of the above
                x[] - where x is one of the above

             group z:
                Provider<y>
                Optional<y>
                Supplier<y>

             group z':
                Map<String, String> - a detached key/value mapping of whole subtree
             */
        FieldTypes fieldTypes = FieldTypes.create(type);
        Config config = (Config) ConfigProvider.getConfig();
        String defaultValue = defaultValue(annotation);
        Object value = configValue(config, fieldTypes, configKey, defaultValue);

        if (null == value) {
            throw new NoSuchElementException("Cannot find value for key: " + configKey);
        }
        return value;
    }

    private Object configValue(Config config, FieldTypes fieldTypes, String configKey, String defaultValue) {
        Class<?> type0 = fieldTypes.field0().rawType();
        Class<?> type1 = fieldTypes.field1().rawType();
        Class<?> type2 = fieldTypes.field2().rawType();
        if (type0.equals(type1)) {
            // not a generic
            return withDefault(config, configKey, type0, defaultValue);
        }

        // generic declaration
        return parameterizedConfigValue(config,
                                        configKey,
                                        defaultValue,
                                        type0,
                                        type1,
                                        type2);
    }

    private static <T> T withDefault(Config config, String key, Class<T> type, String defaultValue) {
        return config.get(key)
                .as(type)
                .orElseGet(() -> (null == defaultValue) ? null : config.convert(type, defaultValue));
    }

    private static Object parameterizedConfigValue(Config config,
                                                   String configKey,
                                                   String defaultValue,
                                                   Class<?> rawType,
                                                   Class<?> typeArg,
                                                   Class<?> typeArg2) {
        if (Optional.class.isAssignableFrom(rawType)) {
            if (typeArg.equals(typeArg2)) {
                return Optional.ofNullable(withDefault(config, configKey, typeArg, defaultValue));
            } else {
                return Optional
                        .ofNullable(parameterizedConfigValue(config,
                                                             configKey,
                                                             defaultValue,
                                                             typeArg,
                                                             typeArg2,
                                                             typeArg2));
            }
        } else if (List.class.isAssignableFrom(rawType)) {
            return asList(config, configKey, typeArg, defaultValue);
        } else if (Supplier.class.isAssignableFrom(rawType)) {
            if (typeArg.equals(typeArg2)) {
                return (Supplier<?>) () -> withDefault(config, configKey, typeArg, defaultValue);
            } else {
                return (Supplier<?>) () -> parameterizedConfigValue(config,
                                                                    configKey,
                                                                    defaultValue,
                                                                    typeArg,
                                                                    typeArg2,
                                                                    typeArg2);
            }
        } else if (Map.class.isAssignableFrom(rawType)) {
            return config.get(configKey).detach().asMap().get();
        } else if (Set.class.isAssignableFrom(rawType)) {
            return new LinkedHashSet<>(asList(config, configKey, typeArg, defaultValue));
        } else {
            throw new IllegalArgumentException("Cannot create config property for " + rawType + "<" + typeArg + ">, key: "
                                                       + configKey);
        }
    }

    private static <T> List<T> asList(Config config, String configKey, Class<T> typeArg, String defaultValue) {
        try {
            return config.get(configKey).asList(typeArg).get();
        } catch (MissingValueException e) {
            // if default
            if (null == defaultValue) {
                //noinspection ThrowInsideCatchBlockWhichIgnoresCaughtException
                throw new NoSuchElementException("Missing list value for key "
                                                         + configKey
                                                         + ", original message: "
                                                         + e.getMessage());
            } else {
                if (defaultValue.isEmpty()) {
                    return Collections.emptyList();
                }

                List<T> result = new LinkedList<>();

                String[] values = defaultValue.split(",");
                for (String value : values) {
                    result.add(config.convert(typeArg, value));
                }

                return result;
            }
        }
    }

    private String defaultValue(ConfigProperty annotation) {
        String defaultFromAnnotation = annotation.defaultValue();
        if (defaultFromAnnotation.equals(ConfigProperty.UNCONFIGURED_VALUE)) {
            return null;
        }
        return defaultFromAnnotation;
    }

    private String configKey(ConfigProperty annotation, String fullPath) {
        String keyFromAnnotation = annotation.name();

        if (keyFromAnnotation.isEmpty()) {
            return fullPath.replace('$', '.');
        }
        return keyFromAnnotation;
    }

    private Type actualType(Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            if (Provider.class.isAssignableFrom((Class<?>) paramType.getRawType())) {
                return paramType.getActualTypeArguments()[0];
            }
        }
        return type;
    }

    private static String injectedName(InjectionPoint ip) {
        Annotated annotated = ip.getAnnotated();
        if (annotated instanceof AnnotatedField) {
            AnnotatedField<?> f = (AnnotatedField<?>) annotated;
            return f.getJavaMember().getName();
        }

        if (annotated instanceof AnnotatedParameter) {
            AnnotatedParameter<?> p = (AnnotatedParameter<?>) annotated;

            Member member = ip.getMember();
            if (member instanceof Method) {
                return member.getName() + "_" + p.getPosition();
            }
            if (member instanceof Constructor) {
                return "new_" + p.getPosition();
            }
        }

        return ip.getMember().getName();
    }

    /**
     * A three tier description of a field type (main type, first
     * generic type, second generic type).
     */
    static final class FieldTypes {
        private TypedField field0;
        private TypedField field1;
        private TypedField field2;

        private static FieldTypes create(Type type) {
            FieldTypes ft = new FieldTypes();

            // if the first type is a Provider or an Instance, we do not want it and start from its child
            TypedField firstType = getTypedField(type);
            if (Provider.class.isAssignableFrom(firstType.rawType)) {
                ft.field0 = getTypedField(firstType);
                firstType = ft.field0;
            } else {
                ft.field0 = firstType;
            }

            ft.field1 = getTypedField(ft.field0);

            // now suppliers, optionals may have two levels deep
            if (firstType.rawType == Optional.class || firstType.rawType == Supplier.class) {
                ft.field2 = getTypedField(ft.field1);
            } else {
                ft.field2 = ft.field1;
            }

            return ft;
        }

        private static TypedField getTypedField(Type type) {
            if (type instanceof Class) {
                return new TypedField((Class<?>) type);
            } else if (type instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) type;

                return new TypedField((Class<?>) paramType.getRawType(), paramType);
            }

            throw new UnsupportedOperationException("No idea how to handle " + type);
        }

        private static TypedField getTypedField(TypedField field) {
            if (field.isParameterized()) {
                ParameterizedType paramType = field.paramType;
                Type[] typeArgs = paramType.getActualTypeArguments();

                if (typeArgs.length == 1) {
                    Type typeArg = typeArgs[0];
                    return getTypedField(typeArg);
                }

                if ((typeArgs.length == 2) && (field.rawType == Map.class)) {
                    if ((typeArgs[0] == typeArgs[1]) && (typeArgs[0] == String.class)) {
                        return new TypedField(String.class);
                    }
                }

                throw new DeploymentException("Cannot create config property for " + field.rawType + ", params: " + Arrays
                        .toString(typeArgs));
            }

            return field;
        }

        private TypedField field0() {
            return field0;
        }

        private TypedField field1() {
            return field1;
        }

        private TypedField field2() {
            return field2;
        }

        private static final class TypedField {
            private final Class<?> rawType;
            private ParameterizedType paramType;

            private TypedField(Class<?> rawType) {
                this.rawType = rawType;
            }

            private TypedField(Class<?> rawType, ParameterizedType paramType) {
                this.rawType = rawType;
                this.paramType = paramType;
            }

            private boolean isParameterized() {
                return paramType != null;
            }

            private Class<?> rawType() {
                return rawType;
            }

            private ParameterizedType getParamType() {
                return paramType;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if ((o == null) || (getClass() != o.getClass())) {
                    return false;
                }
                TypedField that = (TypedField) o;
                return Objects.equals(rawType, that.rawType)
                        && Objects.equals(paramType, that.paramType);
            }

            @Override
            public int hashCode() {
                return Objects.hash(rawType, paramType);
            }

            @Override
            public String toString() {
                return "TypedField{"
                        + "rawType=" + rawType
                        + ", paramType=" + paramType
                        + '}';
            }
        }
    }

    private static final class SerializableConfig implements org.eclipse.microprofile.config.Config, Serializable {

        private static final long serialVersionUID = 1;

        private transient org.eclipse.microprofile.config.Config theConfig;

        private SerializableConfig() {
            this.theConfig = ConfigProvider.getConfig();
        }

        @Override
        public <T> T getValue(String propertyName, Class<T> propertyType) {
            return theConfig.getValue(propertyName, propertyType);
        }

        @Override
        public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
            return theConfig.getOptionalValue(propertyName, propertyType);
        }

        @Override
        public Iterable<String> getPropertyNames() {
            return theConfig.getPropertyNames();
        }

        @Override
        public Iterable<ConfigSource> getConfigSources() {
            return theConfig.getConfigSources();
        }

        private void readObject(ObjectInputStream ios) throws ClassNotFoundException, IOException {
            ios.defaultReadObject();
            this.theConfig = ConfigProvider.getConfig();
        }
    }
}
