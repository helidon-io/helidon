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
package io.helidon.microprofile.jwt.auth;

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

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
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
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.security.SecurityCdiExtension;
import io.helidon.microprofile.server.JaxRsApplication;
import io.helidon.microprofile.server.JaxRsCdiExtension;

import org.eclipse.microprofile.auth.LoginConfig;
import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.ClaimValue;
import org.eclipse.microprofile.jwt.Claims;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * JWT Authentication CDI extension class.
 */
public class JwtAuthCdiExtension implements Extension {
    private final List<ClaimIP> qualifiers = new LinkedList<>();
    private Config config;

    /**
     * Initializes the extension prior to bean discovery.
     *
     * @param discovery bean discovery event
     */
    void before(@Observes BeforeBeanDiscovery discovery) {
        // Register beans manually
        discovery.addAnnotatedType(JsonWebTokenProducer.class, "TokenProducer");
    }

    /**
     * Process each injection point for {@link Claim}.
     *
     * @param pip event from CDI container
     */
    void collectClaimProducer(@Observes ProcessInjectionPoint<?, ?> pip) {
        Claim claim = pip.getInjectionPoint().getAnnotated().getAnnotation(Claim.class);
        if (claim != null) {
            if ((claim.standard() != Claims.UNKNOWN) && !claim.value().isEmpty()) {
                throw new DeploymentException("Claim annotation should not have both values at value and standard! "
                                                      + "@Claim(value=" + claim.value()
                                                      + ", standard=Claims." + claim.standard().name() + ")");
            }
            InjectionPoint ip = pip.getInjectionPoint();
            Type type = ip.getType();
            FieldTypes ft = FieldTypes.forType(type);

            ClaimLiteral q = new ClaimLiteral(
                    (claim.standard() == Claims.UNKNOWN)
                            ? claim.value()
                            : claim.standard().name(),
                    ip.getMember().getDeclaringClass().getName() + "." + getFieldName(ip),
                    ft.isOptional(),
                    ft.isClaimValue(),
                    ft.getField0().getRawType(),
                    ft.getField1().getRawType(),
                    ft.getField2().getRawType(),
                    ft.getField3().getRawType(),
                    type.toString());

            pip.configureInjectionPoint()
                    .addQualifier(q);

            qualifiers.add(new ClaimIP(q, type));
        }
    }

    /**
     * Register a claim producer bean for each {@link Claim} injection.
     *
     * @param abd event from CDI container
     * @param bm  bean manager
     */
    void registerClaimProducers(@Observes AfterBeanDiscovery abd, BeanManager bm) {
        // each injection point will have its own bean
        qualifiers.forEach(q -> abd.addBean(new ClaimProducer(q.qualifier, q.type, bm)));
    }

    /**
     * Validate all injection points are valid.
     *
     * @param add event from CDI container
     */
    void validate(@Observes AfterDeploymentValidation add) {
        qualifiers.forEach(q -> {
            ClaimLiteral claimLiteral = q.getQualifier();
            validate(claimLiteral);
        });
    }

    void configured(@Observes @RuntimeStart Config config) {
        this.config = config;
    }

    void registerProvider(@Observes
                          @Initialized(ApplicationScoped.class)
                          @Priority(PLATFORM_BEFORE + 5) Object event,
                          BeanManager bm) {
        // Security extension to update and check builder
        SecurityCdiExtension security = bm.getExtension(SecurityCdiExtension.class);

        if (security.securityBuilder().hasProvider(JwtAuthProviderService.PROVIDER_NAME)) {
            return;
        }

        // JAX-RS extension to get to applications to see if we are needed
        JaxRsCdiExtension jaxrs = bm.getExtension(JaxRsCdiExtension.class);
        boolean notNeeded = jaxrs.applicationsToRun()
                .stream()
                .map(JaxRsApplication::applicationClass)
                .flatMap(Optional::stream)
                .map(clazz -> clazz.getAnnotation(LoginConfig.class))
                .filter(Objects::nonNull)
                .map(LoginConfig::authMethod)
                .noneMatch("MP-JWT"::equals);

        if (notNeeded) {
            return;
        }

        security.securityBuilder()
                .addProvider(JwtAuthProvider.create(config), JwtAuthProviderService.PROVIDER_NAME);
    }

    @SuppressWarnings("Duplicates")
    private String getFieldName(InjectionPoint ip) {
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

    private void validate(ClaimLiteral claimLiteral) {
        Class<?> rawType = claimLiteral.rawType();
        if (ClaimValue.class.equals(rawType)) {
            validateClaimValue(claimLiteral, claimLiteral.typeArg(), claimLiteral.typeArg2(), claimLiteral.typeArg3());
        } else if (Optional.class.equals(rawType)) {
            validateOptional(claimLiteral, claimLiteral.typeArg(), claimLiteral.typeArg2());
        } else if ((Set.class.equals(rawType)) || (JsonArray.class.equals(rawType))) {
            validateSet(claimLiteral, rawType, claimLiteral.typeArg());
        } else {
            validateBaseType(claimLiteral, rawType);
        }
    }

    private void validateClaimValue(ClaimLiteral claimLiteral, Class<?> parameter, Class<?> parameter2, Class<?> parameter3) {
        if (ClaimValue.class.equals(parameter)) {
            throw new DeploymentException(
                    "ClaimValue has to be used as top level wrapper type. It cannot be parameter as it is in "
                            + "the field " + claimLiteral.id + " of type " + claimLiteral.fieldTypeString);
        } else if (Optional.class.equals(parameter)) {
            validateOptional(claimLiteral, parameter2, parameter3);
        } else if ((Set.class.equals(parameter)) || (JsonArray.class.equals(parameter))) {
            validateSet(claimLiteral, parameter, parameter2);
        } else {
            validateBaseType(claimLiteral, parameter);
        }
    }

    private void validateOptional(ClaimLiteral claimLiteral, Class<?> parameter, Class<?> parameter2) {
        if (ClaimValue.class.equals(parameter)) {
            throw new DeploymentException(
                    "ClaimValue has to be used as top level wrapper type. It cannot be parameter of Optional as it is in "
                            + "the field " + claimLiteral.id + " of type " + claimLiteral.fieldTypeString);
        } else if (Optional.class.equals(parameter)) {
            throw new DeploymentException(
                    "Optional has to be used as top/second level wrapper type. It cannot be parameter of another Optional as it"
                            + " is in the field " + claimLiteral.id + " of type " + claimLiteral.fieldTypeString);
        } else if ((Set.class.equals(parameter)) || (JsonArray.class.equals(parameter))) {
            validateSet(claimLiteral, parameter, parameter2);
        } else {
            validateBaseType(claimLiteral, parameter);
        }
    }

    private void validateSet(ClaimLiteral claimLiteral, Class<?> parent, Class<?> parameter) {
        if (!String.class.equals(parameter) && !NoType.class.equals(parameter)) {
            throw new DeploymentException("Set<" + parameter
                    .getName() + "> is not supported type. Field has to have a Set with a String parameter.");
        }
        try {
            Claims claims = Claims.valueOf(claimLiteral.name);
            if (!Set.class.isAssignableFrom(claims.getType())
                    && !JsonArray.class.isAssignableFrom(claims.getType())) {
                throw new DeploymentException("Cannot assign value of claim " + claimLiteral.name
                                                      + " (claim type: " + claims.getType().getName() + ") "
                                                      + " to the field " + claimLiteral.id + " of type "
                                                      + claimLiteral.fieldTypeString);
            }
        } catch (IllegalArgumentException ignored) {
            //if claim is custom, it has to be JsonArray in case of Set
            if (!JsonArray.class.equals(parent)) {
                throw new DeploymentException(
                        "Field type has to be JsonArray (instead of Set<String>) while using custom claim name. "
                                + "Field " + claimLiteral.id + " can not be type: " + claimLiteral.fieldTypeString);
            }
        }
    }

    private void validateBaseType(ClaimLiteral claimLiteral, Class<?> clazz) {
        if (NoType.class.equals(clazz)) {
            return;
        }
        try {
            Claims claims = Claims.valueOf(claimLiteral.name);
            //check if field type and claim type are compatible
            if ((clazz.equals(Long.class) || clazz.equals(long.class) || JsonNumber.class.isAssignableFrom(clazz))
                    && (Long.class.equals(claims.getType()) || JsonNumber.class.isAssignableFrom(claims.getType()))) {
                return;
            }

            if ((clazz.equals(String.class) || JsonString.class.isAssignableFrom(clazz))
                    && (String.class.equals(claims.getType()) || JsonString.class.isAssignableFrom(claims.getType()))) {
                return;
            }

            if ((clazz.equals(Boolean.class) || clazz.equals(boolean.class) || JsonValue.class.isAssignableFrom(clazz))
                    && (Boolean.class.equals(claims.getType()) || JsonValue.class.isAssignableFrom(claims.getType()))) {
                return;
            }

            if ((clazz.equals(JsonObject.class) && JsonObject.class.isAssignableFrom(claims.getType()))) {
                return;
            }

            if ((clazz.equals(JsonArray.class) && Set.class.isAssignableFrom(claims.getType()))) {
                return;
            }

            throw new DeploymentException("Cannot assign value of claim " + claimLiteral.name
                                                  + " (claim type: " + claims.getType().getName() + ") "
                                                  + " to the field " + claimLiteral.id
                                                  + " of type " + claimLiteral.fieldTypeString);

        } catch (IllegalArgumentException ignored) {
            //If claim requested claim is the custom claim, its unwrapped field type has to be JsonValue or its subtype
            if (!JsonValue.class.isAssignableFrom(clazz)) {
                throw new DeploymentException("Field type has to be JsonValue or its subtype while using custom claim name. "
                                                      + "Field " + claimLiteral.id + " can not be type: "
                                                      + claimLiteral.fieldTypeString);
            }
        }
    }

    @Qualifier
    @Retention(RUNTIME)
    @Target({METHOD, FIELD, PARAMETER, TYPE})
    @interface MpClaimQualifier {
        @Nonbinding
        String name();

        String id();

        @Nonbinding
        boolean optional();

        @Nonbinding
        boolean claimValue();

        @Nonbinding
        Class<?> rawType();

        @Nonbinding
        Class<?> typeArg();

        @Nonbinding
        Class<?> typeArg2();

        @Nonbinding
        Class<?> typeArg3();
    }

    static class ClaimLiteral extends AnnotationLiteral<MpClaimQualifier> implements MpClaimQualifier {
        private final String name;
        private final String id;
        private final boolean optional;
        private final boolean claimValue;
        private final Class<?> rawType;
        private final Class<?> typeArg;
        private final Class<?> typeArg2;
        private final Class<?> typeArg3;
        private final String fieldTypeString;

        ClaimLiteral(String name,
                     String id,
                     boolean optional,
                     boolean claimValue,
                     Class<?> rawType,
                     Class<?> typeArg,
                     Class<?> typeArg2,
                     Class<?> typeArg3,
                     String fieldTypeString) {
            this.name = name;
            this.id = id;
            this.optional = optional;
            this.claimValue = claimValue;
            this.rawType = rawType;
            this.typeArg = typeArg;
            this.typeArg2 = typeArg2;
            this.typeArg3 = typeArg3;
            this.fieldTypeString = fieldTypeString;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean optional() {
            return optional;
        }

        @Override
        public boolean claimValue() {
            return claimValue;
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
        public Class<?> typeArg3() {
            return typeArg3;
        }

        @Override
        public String toString() {
            return "ClaimLiteral{"
                    + "rawType=" + rawType
                    + ", name=" + name
                    + ", id=" + id
                    + '}';
        }
    }

    static class ClaimIP {
        private final ClaimLiteral qualifier;
        private final Type type;

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
        private boolean optional = false;
        private boolean claimValue = false;
        private TypedField field0;
        private TypedField field1;
        private TypedField field2;
        private TypedField field3;

        static FieldTypes forType(Type type) {
            FieldTypes ft = new FieldTypes();

            // if the first type is a Instace.class, we do not want it and start from its child
            //Fields can have 3 parametes in total -> ClaimValue<Optional<Set<String>>>. That is why we need 4 fields in total.
            TypedField firstType = getTypedField(type);
            if (firstType.rawType.equals(Instance.class) || firstType.rawType.equals(Provider.class)) {
                ft.field0 = getTypedField(firstType);
            } else {
                ft.field0 = firstType;
            }
            ft.field1 = getTypedField(ft.field0);
            ft.field2 = getTypedField(ft.field1);
            ft.field3 = getTypedField(ft.field2);

            //check for claim value and optional wrappers
            if (ft.field0.getRawType().equals(ClaimValue.class)) {
                ft.claimValue = true;
            }
            if (ft.field0.getRawType().equals(Optional.class) || ft.field1.getRawType().equals(Optional.class)) {
                ft.optional = true;
            }

            return ft;
        }

        static TypedField getTypedField(Type type) {
            if (type instanceof Class) {
                return new TypedField((Class<?>) type);
            }

            if (type instanceof ParameterizedType) {
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

                if ((typeArgs.length == 2) && (field.rawType.equals(Map.class))) {
                    if ((typeArgs[0].equals(typeArgs[1])) && (typeArgs[0].equals(String.class))) {
                        return new TypedField(String.class);
                    }
                }

                throw new DeploymentException("Cannot create config property for " + field.rawType + ", params: " + Arrays
                        .toString(typeArgs));
            }

            return new TypedField(NoType.class);
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

        TypedField getField3() {
            return field3;
        }

        public boolean isOptional() {
            return optional;
        }

        public boolean isClaimValue() {
            return claimValue;
        }

        static final class TypedField {
            private final Class<?> rawType;
            private ParameterizedType paramType;

            private TypedField(Class<?> rawType) {
                this.rawType = rawType;
            }

            private TypedField(Class<?> rawType, ParameterizedType paramType) {
                this.rawType = rawType;
                this.paramType = paramType;
            }

            boolean isParameterized() {
                return paramType != null;
            }

            Class<?> getRawType() {
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
                if ((o == null) || (!getClass().equals(o.getClass()))) {
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

    private static final class NoType {
        private NoType() {
        }
    }

}
