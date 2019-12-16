/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.inject.spi.ProcessObserverMethod;
import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.Nonbinding;
import javax.inject.Provider;
import javax.inject.Qualifier;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.MissingValueException;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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

    private final Set<IpConfig> ipConfigs = new HashSet<>();

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
     *
     * @deprecated This method was not intended to be {@code public}
     * and may be removed without notice.
     */
    @Deprecated
    public void collectConfigProducer(@Observes ProcessInjectionPoint<?, ?> pip) {
        ConfigProperty configProperty = pip.getInjectionPoint().getAnnotated().getAnnotation(ConfigProperty.class);
        if (configProperty != null) {
            InjectionPoint ip = pip.getInjectionPoint();
            String fullPath = ip.getMember().getDeclaringClass().getName()
                    + "." + getFieldName(ip);

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
            FieldTypes ft = FieldTypes.forType(ip.getType());

            ConfigQLiteral q = new ConfigQLiteral(
                    fullPath,
                    configProperty.name(),
                    configProperty.defaultValue(),
                    ft.getField0().getRawType(),
                    ft.getField1().getRawType(),
                    ft.getField2().getRawType());

            pip.configureInjectionPoint()
                    .addQualifier(q);
        }
    }

    private void harvestConfigPropertyInjectionPointsFromEnabledBean(@Observes ProcessBean<?> event) {
        Bean<?> bean = event.getBean();
        Set<InjectionPoint> beanInjectionPoints = bean.getInjectionPoints();
        if (beanInjectionPoints != null && !beanInjectionPoints.isEmpty()) {
            for (InjectionPoint beanInjectionPoint : beanInjectionPoints) {
                if (beanInjectionPoint != null) {
                    Type type = beanInjectionPoint.getType();
                    Set<Annotation> qualifiers = beanInjectionPoint.getQualifiers();
                    assert qualifiers != null;
                    for (Annotation qualifier : qualifiers) {
                        if (qualifier instanceof ConfigQualifier) {
                            ipConfigs.add(new IpConfig((ConfigQualifier) qualifier, type));
                            break;
                        }
                    }
                }
            }
        }
    }

    private <X> void harvestConfigPropertyInjectionPointsFromEnabledObserverMethod(@Observes ProcessObserverMethod<?, X> event,
                                                                                   BeanManager beanManager) {
        AnnotatedMethod<X> annotatedMethod = event.getAnnotatedMethod();
        if (annotatedMethod != null) {
            List<AnnotatedParameter<X>> annotatedParameters = annotatedMethod.getParameters();
            if (annotatedParameters != null && annotatedParameters.size() > 1) {
                for (AnnotatedParameter<?> annotatedParameter : annotatedParameters) {
                    if (annotatedParameter != null
                            && !annotatedParameter.isAnnotationPresent(Observes.class)) {
                        InjectionPoint injectionPoint = beanManager.createInjectionPoint(annotatedParameter);
                        Type type = injectionPoint.getType();
                        Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                        assert qualifiers != null;
                        for (Annotation qualifier : qualifiers) {
                            if (qualifier instanceof ConfigQualifier) {
                                ipConfigs.add(new IpConfig((ConfigQualifier) qualifier, type));
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private static String getFieldName(InjectionPoint ip) {
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
     *
     * @deprecated This method was not intended to be {@code public}
     * and may be removed without notice.
     */
    @Deprecated
    public void registerConfigProducer(@Observes AfterBeanDiscovery abd, BeanManager bm) {
        // each injection point will have its own bean
        ipConfigs.forEach(ipc -> abd.addBean(new ConfigPropertyProducer(ipc.qualifier, ipc.type, bm)));

        // we also must support injection of Config itself
        abd.addBean()
                .addType(Config.class)
                .createWith(creationalContext -> (Config) ConfigProvider.getConfig());

        abd.addBean()
                .addType(org.eclipse.microprofile.config.Config.class)
                .createWith(creationalContext -> new SerializableConfig());
    }

    /**
     * Validate all injection points are valid.
     *
     * @param add event from CDI container
     *
     * @deprecated This method was not intended to be {@code public}
     * and may be removed without notice.
     */
    @Deprecated
    public void validate(AfterDeploymentValidation add) {
        this.validate(add, CDI.current().getBeanManager());
    }

    private void validate(@Observes AfterDeploymentValidation add, BeanManager beanManager) {
        LOGGER.entering(getClass().getName(), "validate");
        CreationalContext<?> cc = beanManager.createCreationalContext(null);
        try {
            ipConfigs.forEach(ipc -> {
                try {
                    Object configValue = beanManager.getInjectableReference(new InjectionPointTemplate(ipc.type, ipc.qualifier),
                                                                            cc);
                    VALUE_LOGGER.finest(() -> "Config value for " + ipc.qualifier.key()
                            + " (" + ipc.qualifier.fullPath() + "), is "
                            + configValue);
                } catch (Exception e) {
                    add.addDeploymentProblem(e);
                }
            });
        } finally {
            cc.release();
        }
        ipConfigs.clear();

        LOGGER.exiting(getClass().getName(), "validate");
    }

    private static Class<?> getPropertyClass(ConfigQualifier qualifier) {
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
        Class<?> rawType();

        // e.g. eq. to raw type, or type argument of Producer, Optional
        @Nonbinding
        Class<?> typeArg();

        @Nonbinding
        Class<?> typeArg2();
    }

    static final class ConfigQLiteral extends AnnotationLiteral<ConfigQualifier> implements ConfigQualifier {
        private String fullPath;
        private String key;
        private String defaultValue;
        private Class<?> rawType;
        private Class<?> typeArg;
        private Class<?> typeArg2;

        private ConfigQLiteral(String fullPath,
                               String key,
                               String defaultValue,
                               Class<?> rawType,
                               Class<?> typeArg,
                               Class<?> typeArg2) {
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
        public Class<?> rawType() {
            return rawType;
        }

        @Override
        public Class<?> typeArg() {
            return typeArg;
        }

        @Override
        public Class<?> typeArg2() {
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

    static final class IpConfig {
        private ConfigQualifier qualifier;
        private Type type;

        private IpConfig(ConfigQualifier qualifier, Type type) {
            this.qualifier = qualifier;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            IpConfig ipConfig = (IpConfig) o;
            return Objects.equals(qualifier, ipConfig.qualifier) && Objects.equals(type, ipConfig.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(qualifier, type);
        }

        @Override
        public String toString() {
            return qualifier + " (" + type + ")";
        }
    }

    /**
     * A {@link Bean} to create {@link ConfigProperty} values for each
     * injection point.
     *
     * @deprecated This class was not intended to be {@code public}
     * and may be removed without notice.
     */
    @Deprecated
    public static final class ConfigPropertyProducer implements Bean<Object> {
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

        private ConfigPropertyProducer(ConfigQualifier q, Type type, BeanManager bm) {
            this.qualifier = q;
            this.bm = bm;
            Type actualType = type;
            if (type instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) type;
                if (Provider.class.isAssignableFrom((Class<?>) paramType.getRawType())) {
                    actualType = paramType.getActualTypeArguments()[0];
                }
            }

            this.type = actualType;
        }

        @SuppressWarnings({"CheckStyle", "ObjectEquality", "unchecked"})
        private static Object getConfigValue(Config helidonConfig, ConfigQualifier q) {
            try {
                String configKey = q.key().isEmpty() ? q.fullPath().replace('$', '.') : q.key();
                String defaultValue = ConfigProperty.UNCONFIGURED_VALUE.equals(q.defaultValue()) ? null : q.defaultValue();

                if (q.rawType() == q.typeArg()) {
                    // not a generic
                    Optional<Object> configValue = ((org.eclipse.microprofile.config.Config) helidonConfig)
                            .getOptionalValue(configKey, (Class<Object>) q.rawType());

                    return configValue
                            .orElseGet(() -> {
                                if (null == defaultValue) {
                                    return null;
                                }
                                return helidonConfig.get(configKey)
                                        .convert(q.rawType(), defaultValue);
                            });
                }
                // generic declaration
                return getParameterizedConfigValue(helidonConfig,
                                                   configKey,
                                                   defaultValue,
                                                   q.rawType(),
                                                   q.typeArg(),
                                                   q.typeArg2());
            } catch (IllegalArgumentException e) {
                if (e.getCause() instanceof ConfigException) {
                    throw new DeploymentException("Config value for " + q.key() + "(" + q.fullPath() + ") is not defined", e);
                } else {
                    throw e;
                }
            }
        }

        @SuppressWarnings({"ObjectEquality", "unchecked", "rawtypes"})
        private static <X> Object getParameterizedConfigValue(Config helidonConfig,
                                                              String configKey,
                                                              String defaultValue,
                                                              Class<?> rawType,
                                                              Class<X> typeArg,
                                                              Class<?> typeArg2) {

            org.eclipse.microprofile.config.Config mpConfig = (org.eclipse.microprofile.config.Config) helidonConfig;

            if (Optional.class.isAssignableFrom(rawType)) {
                if (typeArg == typeArg2) {
                    Optional<X> optionalValue = mpConfig.getOptionalValue(configKey, typeArg);
                    if (optionalValue.isPresent()) {
                        return optionalValue;
                    }
                    if (null == defaultValue) {
                        return Optional.empty();
                    }
                    return helidonConfig.get(configKey).convert(typeArg, defaultValue);
                } else {
                    return Optional
                            .ofNullable(getParameterizedConfigValue(helidonConfig,
                                                                    configKey,
                                                                    defaultValue,
                                                                    typeArg,
                                                                    typeArg2,
                                                                    typeArg2));
                }
            } else if (List.class.isAssignableFrom(rawType)) {
                try {
                    return helidonConfig.get(configKey)
                            .asList(typeArg)
                            .get();
                } catch (MissingValueException e) {
                    // if default
                    if (null == defaultValue) {
                        throw e;
                    } else {
                        if (defaultValue.isEmpty()) {
                            return List.of();
                        }

                        List<X> result = new LinkedList<>();

                        String[] values = defaultValue.split(",");
                        for (String value : values) {
                            result.add(helidonConfig.convert(typeArg, value));
                        }

                        return result;
                    }
                }
            } else if (Supplier.class.isAssignableFrom(rawType)) {
                if (typeArg == typeArg2) {
                    return (Supplier<?>) () -> {
                        Optional<?> opt = mpConfig.getOptionalValue(configKey, typeArg);
                        if (opt.isPresent()) {
                            return opt;
                        }
                        return helidonConfig.get(configKey)
                                .convert(typeArg, defaultValue);
                    };
                } else {
                    return (Supplier<?>) () -> getParameterizedConfigValue(helidonConfig,
                                                                           configKey,
                                                                           defaultValue,
                                                                           typeArg,
                                                                           typeArg2,
                                                                           typeArg2);
                }
            } else if (Map.class.isAssignableFrom(rawType)) {
                return helidonConfig.get(configKey).detach().asMap();
            } else if (Set.class.isAssignableFrom(rawType)) {
                return new HashSet(helidonConfig.get(configKey).asList(typeArg)
                                           .get());
            } else {
                throw new DeploymentException("Cannot create config property for " + rawType + "<" + typeArg + ">, key: "
                                                      + configKey);
            }
        }

        @Override
        public Object create(CreationalContext<Object> context) {
            Object value = getConfigValue(context);
            if (null == value) {
                throw MissingValueException.create(Config.Key.create(qualifier.key()));
            }

            return value;
        }

        private Object getConfigValue(CreationalContext<Object> context) {
            return getConfigValue((Config) ConfigProvider.getConfig(), qualifier);
        }

        @Override
        public Class<?> getBeanClass() {
            return ConfigPropertyProducer.class;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Set.of();
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
            return Set.of(type);
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return Set.of(qualifier, QUALIFIER);
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
            return Set.of();
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
     * A three tier description of a field type (main type, first
     * generic type, second generic type).
     */
    static final class FieldTypes {
        private TypedField field0;
        private TypedField field1;
        private TypedField field2;

        private static FieldTypes forType(Type type) {
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

        private TypedField getField0() {
            return field0;
        }

        private TypedField getField1() {
            return field1;
        }

        private TypedField getField2() {
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

            private Class<?> getRawType() {
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

    private static final class InjectionPointTemplate implements InjectionPoint {

        private final Type type;

        private final Set<Annotation> qualifiers;

        private InjectionPointTemplate(Type type, Annotation soleQualifier) {
            this(type, Collections.singleton(soleQualifier));
        }

        private InjectionPointTemplate(Type type, Set<Annotation> qualifiers) {
            super();
            this.type = type;
            this.qualifiers = qualifiers;
        }

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return this.qualifiers;
        }

        @Override
        public Bean<?> getBean() {
            return null;
        }

        @Override
        public Annotated getAnnotated() {
            return null;
        }

        @Override
        public Member getMember() {
            return null;
        }

        @Override
        public boolean isTransient() {
            return false;
        }

        @Override
        public boolean isDelegate() {
            return false;
        }

    }
}
