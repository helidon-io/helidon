/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.config.cdi;

import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.Nonbinding;
import javax.inject.Provider;
import javax.inject.Qualifier;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.config.MissingValueException;
import io.helidon.microprofile.config.MpConfig;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Extension to enable config injection in CDI container (all of {@link Config}, {@link org.eclipse.microprofile.config.Config}
 * and {@link ConfigProperty}).
 */
public class ConfigCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(ConfigCdiExtension.class.getName());
    private static final Logger VALUE_LOGGER = Logger.getLogger(ConfigCdiExtension.class.getName() + ".VALUES");
    private final ConfigProviderResolver configResolver = ConfigProviderResolver.instance();

    private final List<IpConfig> qualifiers = new LinkedList<>();

    /**
     * Constructor invoked by CDI container.
     */
    public ConfigCdiExtension() {
        LOGGER.fine("ConfigCdiExtension instantiated");
    }

    /**
     * Process each injection point for {@link ConfigProperty}.
     *
     * @param pip event from CDI container
     */
    public void collectConfigProducer(@Observes ProcessInjectionPoint<?, ?> pip) {
        ConfigProperty configProperty = pip.getInjectionPoint().getAnnotated().getAnnotation(ConfigProperty.class);
        if (configProperty != null) {
            InjectionPoint ip = pip.getInjectionPoint();
            String fullPath = ip.getMember().getDeclaringClass().getName()
                    + "." + getFieldName(ip);

            Type type = ip.getType();
            /*
             Supported types
             group x:
                primitive types + String
                any java class (except for parametrized types)
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
            FieldTypes ft = FieldTypes.forType(type);

            ConfigQLiteral q = new ConfigQLiteral(
                    fullPath,
                    configProperty.name(),
                    configProperty.defaultValue(),
                    ft.getField0().getRawType(),
                    ft.getField1().getRawType(),
                    ft.getField2().getRawType());

            pip.configureInjectionPoint()
                    .addQualifier(q);

            qualifiers.add(new IpConfig(q, type));
        }
    }

    private String getFieldName(InjectionPoint ip) {
        Annotated annotated = ip.getAnnotated();
        if (annotated instanceof AnnotatedField) {
            AnnotatedField f = (AnnotatedField) annotated;
            return f.getJavaMember().getName();
        }

        if (annotated instanceof AnnotatedParameter) {
            AnnotatedParameter p = (AnnotatedParameter) annotated;

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
     * Register a config producer bean for each {@link ConfigProperty} injection.
     *
     * @param abd event from CDI container
     * @param bm  bean manager
     */
    public void registerConfigProducer(@Observes AfterBeanDiscovery abd, BeanManager bm) {
        // each injection point will have its own bean
        qualifiers.forEach(q -> abd.addBean(new ConfigPropertyProducer(q.qualifier, q.type, bm)));

        // we also must support injection of Config itself
        abd.addBean()
                .addType(Config.class)
                .createWith(creationalContext -> ((MpConfig) configResolver.getConfig()).getConfig());

        abd.addBean()
                .addType(org.eclipse.microprofile.config.Config.class)
                .createWith(creationalContext -> {
                    return new SerializableConfig();
                });
    }

    /**
     * Validate all injection points are valid.
     *
     * @param add event from CDI container
     */
    public void validate(@Observes AfterDeploymentValidation add) {
        LOGGER.entering(getClass().getName(), "validate");
        MpConfig mpConfig = (MpConfig) configResolver.getConfig();

        qualifiers.forEach(q -> {
            try {
                Class<?> propertyClass = getPropertyClass(q.qualifier);
                if (!mpConfig.hasConverter(propertyClass)) {
                    throw new DeploymentException("Config mapper for " + propertyClass.getName() + " does not exist");
                }

                Object configValue;
                if (q.qualifier.rawType().isArray()) {
                    ConfigQualifier qualifier = q.qualifier;
                    String configKey = qualifier.key().isEmpty()
                            ? qualifier.fullPath().replace('$', '.')
                            : qualifier.key();
                    // default values!!!
                    configValue = mpConfig.getValue(configKey, q.qualifier.rawType());
                } else {
                    configValue = ConfigPropertyProducer.getConfigValue(mpConfig, q.qualifier);
                }

                if (null == configValue) {
                    throw new DeploymentException("Config value for " + q.qualifier.key() + "(" + q.qualifier
                            .fullPath() + ") is not defined");
                }
                VALUE_LOGGER.finest(() -> "Config value for " + q.qualifier.key() + " (" + q.qualifier
                        .fullPath() + "), is " + configValue);

            } catch (Exception e) {
                add.addDeploymentProblem(e);
            }
        });

        LOGGER.exiting(getClass().getName(), "validate");
    }

    private Class<?> getPropertyClass(ConfigQualifier qualifier) {
        if (qualifier.rawType().isArray()) {
            return qualifier.rawType().getComponentType();
        }
        if (qualifier.rawType() == qualifier.typeArg()) {
            return qualifier.rawType();
        }
        if (qualifier.typeArg() == qualifier.typeArg2()) {
            return qualifier.typeArg();
        }

        return qualifier.typeArg2();
    }

    @Qualifier
    @Retention(RUNTIME)
    @Target({METHOD, FIELD, PARAMETER, TYPE})
    @interface ConfigQualifier {
        String fullPath();

        @Nonbinding
        String key();

        @Nonbinding
        String defaultValue();

        // e.g. String, Producer, Optional
        @Nonbinding
        Class rawType();

        // e.g. eq. to raw type, or type argument of Producer, Optional
        @Nonbinding
        Class typeArg();

        @Nonbinding
        Class typeArg2();
    }

    static class ConfigQLiteral extends AnnotationLiteral<ConfigQualifier> implements ConfigQualifier {
        private String fullPath;
        private String key;
        private String defaultValue;
        private Class rawType;
        private Class typeArg;
        private Class typeArg2;

        ConfigQLiteral(String fullPath, String key, String defaultValue, Class rawType, Class typeArg, Class typeArg2) {
            this.fullPath = fullPath;
            this.key = key;
            this.defaultValue = defaultValue;
            this.rawType = rawType;
            this.typeArg = typeArg;
            this.typeArg2 = typeArg2;
        }

        @Override
        public String fullPath() {
            return fullPath;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String defaultValue() {
            return defaultValue;
        }

        @Override
        public Class rawType() {
            return rawType;
        }

        @Override
        public Class typeArg() {
            return typeArg;
        }

        @Override
        public Class typeArg2() {
            return typeArg2;
        }

        @Override
        public String toString() {
            return "ConfigQLiteral{"
                    + "fullPath='" + fullPath + '\''
                    + ", key='" + key + '\''
                    + ", defaultValue='" + defaultValue + '\''
                    + ", rawType=" + rawType
                    + '}';
        }
    }

    static class IpConfig {
        private ConfigQualifier qualifier;
        private Type type;

        IpConfig(ConfigQualifier qualifier, Type type) {
            this.qualifier = qualifier;
            this.type = type;
        }
    }

    /**
     * A Bean to create {@link ConfigProperty} values for each injection point.
     */
    public static class ConfigPropertyProducer implements Bean<Object> {
        private static final Annotation QUALIFIER = new ConfigProperty() {
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

        private final ConfigQualifier qualifier;
        private final Type type;
        private final BeanManager bm;

        ConfigPropertyProducer(ConfigQualifier q, Type type, BeanManager bm) {
            this.qualifier = q;
            this.bm = bm;
            Type actualType = type;
            if (type instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) type;
                if (Provider.class.equals(paramType.getRawType())) {
                    actualType = paramType.getActualTypeArguments()[0];
                }
            }

            this.type = actualType;
        }

        static Object getConfigValue(MpConfig config, ConfigQualifier q) {
            String configKey = q.key().isEmpty() ? q.fullPath().replace('$', '.') : q.key();
            String defaultValue = ConfigProperty.UNCONFIGURED_VALUE.equals(q.defaultValue()) ? null : q.defaultValue();

            if (q.rawType() == q.typeArg()) {
                // not a generic
                return config.getValueWithDefault(configKey, q.rawType(), defaultValue);
            }
            // generic declaration
            return getParametrizedConfigValue(config,
                                              configKey,
                                              defaultValue,
                                              q.rawType(),
                                              q.typeArg(),
                                              q.typeArg2());
        }

        @SuppressWarnings("unchecked")
        static Object getParametrizedConfigValue(MpConfig mpConfig,
                                                 String configKey,
                                                 String defaultValue,
                                                 Class rawType,
                                                 Class typeArg,
                                                 Class typeArg2) {

            if (rawType.isAssignableFrom(Optional.class)) {
                if (typeArg == typeArg2) {
                    return Optional.ofNullable(mpConfig.getValueWithDefault(configKey, typeArg, defaultValue));
                } else {
                    return Optional
                            .ofNullable(getParametrizedConfigValue(mpConfig,
                                                                   configKey,
                                                                   defaultValue,
                                                                   typeArg,
                                                                   typeArg2,
                                                                   typeArg2));
                }
            } else if (rawType.isAssignableFrom(List.class)) {
                try {
                    return mpConfig.asList(configKey, typeArg);
                } catch (MissingValueException e) {
                    // if default
                    if (null == defaultValue) {
                        throw e;
                    } else {
                        if (defaultValue.isEmpty()) {
                            return CollectionsHelper.listOf();
                        }

                        List result = new LinkedList();

                        String[] values = defaultValue.split(",");
                        for (String value : values) {
                            result.add(mpConfig.convert(typeArg, value));
                        }

                        return result;
                    }
                }
            } else if (rawType.isAssignableFrom(Supplier.class)) {
                if (typeArg == typeArg2) {
                    return (Supplier) () -> mpConfig.getValueWithDefault(configKey, typeArg, defaultValue);
                } else {
                    return (Supplier) () -> getParametrizedConfigValue(mpConfig,
                                                                       configKey,
                                                                       defaultValue,
                                                                       typeArg,
                                                                       typeArg2,
                                                                       typeArg2);
                }
            } else if (rawType.isAssignableFrom(Map.class)) {
                return mpConfig.getConfig().get(configKey).detach().asMap();
            } else if (rawType.isAssignableFrom(Set.class)) {
                return mpConfig.asSet(configKey, typeArg);
            } else {
                throw new DeploymentException("Cannot create config property for " + rawType + "<" + typeArg + ">, key: "
                                                      + configKey);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object create(CreationalContext<Object> context) {
            Object value = getConfigValue(context);
            if (null == value && qualifier.rawType().isPrimitive()) {
                // primitive field, not configured, no default
                throw MissingValueException.forKey(Config.Key.of(qualifier.key()));
            }

            return value;
        }

        private Object getConfigValue(CreationalContext<Object> context) {
            return getConfigValue((MpConfig) ConfigProviderResolver.instance().getConfig(), qualifier);
        }

        @Override
        public Class<?> getBeanClass() {
            return ConfigPropertyProducer.class;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return CollectionsHelper.setOf();
        }

        @Override
        public boolean isNullable() {
            return false;
        }

        @Override
        public void destroy(Object instance, CreationalContext<Object> creationalContext) {

        }

        @Override
        public Set<Type> getTypes() {
            return CollectionsHelper.setOf(type);
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return CollectionsHelper.setOf(qualifier, QUALIFIER);
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return Dependent.class;
        }

        @Override
        public String getName() {
            return qualifier.fullPath();
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return CollectionsHelper.setOf();
        }

        @Override
        public boolean isAlternative() {
            return false;
        }

        @Override
        public String toString() {
            return "ConfigPropertyProducer{"
                    + "qualifier=" + qualifier
                    + '}';
        }
    }

    /**
     * A three tier description of a field type (main type, first generic type, second generic type).
     */
    static class FieldTypes {
        private TypedField field0;
        private TypedField field1;
        private TypedField field2;

        static FieldTypes forType(Type type) {
            FieldTypes ft = new FieldTypes();

            // if the first type is a provider, we do not want it and start from its child
            TypedField firstType = getTypedField(type);
            if (firstType.rawType.equals(Provider.class)) {
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

        static TypedField getTypedField(Type type) {
            if (type instanceof Class) {
                return new TypedField((Class) type);
            } else if (type instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) type;

                return new TypedField((Class) paramType.getRawType(), paramType);
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

        TypedField getField0() {
            return field0;
        }

        TypedField getField1() {
            return field1;
        }

        TypedField getField2() {
            return field2;
        }

        static final class TypedField {
            private final Class rawType;
            private ParameterizedType paramType;

            private TypedField(Class rawType) {
                this.rawType = rawType;
            }

            private TypedField(Class rawType, ParameterizedType paramType) {
                this.rawType = rawType;
                this.paramType = paramType;
            }

            boolean isParameterized() {
                return paramType != null;
            }

            Class getRawType() {
                return rawType;
            }

            ParameterizedType getParamType() {
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

    private static class SerializableConfig implements org.eclipse.microprofile.config.Config, Serializable {
        private static final long serialVersionUID = 1;

        private transient org.eclipse.microprofile.config.Config theConfig;

        SerializableConfig() {
            this.theConfig = ConfigProviderResolver.instance().getConfig();
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

        private void readObject(ObjectInputStream ios) {
            this.theConfig = ConfigProviderResolver.instance().getConfig();
        }
    }
}
