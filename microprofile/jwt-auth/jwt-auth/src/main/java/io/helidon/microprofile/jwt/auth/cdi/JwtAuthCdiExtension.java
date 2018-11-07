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
package io.helidon.microprofile.jwt.auth.cdi;

import io.helidon.config.Config;
import io.helidon.microprofile.config.MpConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.Claim;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.Nonbinding;
import javax.inject.Provider;
import javax.inject.Qualifier;
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
import java.util.function.Supplier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * TODO javadoc.
 */
public class JwtAuthCdiExtension implements Extension {

    private final List<ClaimIP> qualifiers = new LinkedList<>();

    public void before(@Observes BeforeBeanDiscovery discovery) {
        // Register beans manually
        discovery.addAnnotatedType(JsonWebTokenProducer.class, "TokenProducer");
        //discovery.addAnnotatedType(ClaimProducer.class, "ClaimProducer");
    }

    /**
     * Process each injection point for {@link Claim}.
     *
     * @param pip event from CDI container
     */
    public void collectClaimProducer(@Observes ProcessInjectionPoint<?, ?> pip) {
        Claim configProperty = pip.getInjectionPoint().getAnnotated().getAnnotation(Claim.class);
        if (configProperty != null) {
            InjectionPoint ip = pip.getInjectionPoint();

            Type type = ip.getType();
            FieldTypes ft = FieldTypes.forType(type);

            //TODO
            ClaimLiteral q = new ClaimLiteral(
                    configProperty.value()==null || configProperty.value().isEmpty() ? configProperty.standard().name() : configProperty.value(),
                    ft.getField0().getRawType(),
                    ft.getField1().getRawType(),
                    ft.getField2().getRawType());

            pip.configureInjectionPoint()
                    .addQualifier(q);

            qualifiers.add(new ClaimIP(q, type));
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
     * Register a config producer bean for each {@link Claim} injection.
     *
     * @param abd event from CDI container
     * @param bm  bean manager
     */
    public void registerConfigProducer(@Observes AfterBeanDiscovery abd, BeanManager bm) {
        // each injection point will have its own bean
        qualifiers.forEach(q -> abd.addBean(new ClaimProducer(q.qualifier, q.type, bm)));

        // we also must support injection of Config itself
        /*abd.addBean()
                .addType(Config.class)
                .createWith(creationalContext -> ((MpConfig) configResolver.getConfig()).getConfig());

        abd.addBean()
                .addType(org.eclipse.microprofile.config.Config.class)
                .createWith(creationalContext -> {
                    return new SerializableConfig();
                });*/
    }

    /**
     * Validate all injection points are valid.
     *
     * @param add event from CDI container
     */
    public void validate(@Observes AfterDeploymentValidation add) {

        qualifiers.forEach(q -> {
            System.out.println();
            /*try {
                Class<?> propertyClass = getPropertyClass(q.qualifier);
                if (!mpConfig.hasConverter(propertyClass)) {
                    throw new DeploymentException("Config mapper for " + propertyClass.getName() + " does not exist");
                }

                Object configValue;
                if (q.qualifier.rawType().isArray()) {
                    ClaimInternal qualifier = q.qualifier;
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
            }*/
        });

        //LOGGER.exiting(getClass().getName(), "validate");
    }

    private Class<?> getPropertyClass(ClaimInternal qualifier) {
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
    @interface ClaimInternal {
        @Nonbinding
        String name();

        // e.g. String, Producer, Optional
        @Nonbinding
        Class rawType();

        // e.g. eq. to raw type, or type argument of Producer, Optional
        @Nonbinding
        Class typeArg();

        @Nonbinding
        Class typeArg2();
    }

    static class ClaimLiteral extends AnnotationLiteral<ClaimInternal> implements ClaimInternal {
        private String name;
        private Class rawType;
        private Class typeArg;
        private Class typeArg2;

        ClaimLiteral(String name, Class rawType, Class typeArg, Class typeArg2) {
            this.name = name;
            this.rawType = rawType;
            this.typeArg = typeArg;
            this.typeArg2 = typeArg2;
        }

        @Override
        public String name() {
            return name;
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
            return "ClaimLiteral{"
                    + "rawType=" + rawType
                    + '}';
        }
    }

    static class ClaimIP {
        private ClaimLiteral qualifier;
        private Type type;

        ClaimIP(ClaimLiteral qualifier, Type type) {
            this.qualifier = qualifier;
            this.type = type;
        }

        public ClaimLiteral getQualifier() {
            return qualifier;
        }

        public Type getType() {
            return type;
        }
    }



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

}
