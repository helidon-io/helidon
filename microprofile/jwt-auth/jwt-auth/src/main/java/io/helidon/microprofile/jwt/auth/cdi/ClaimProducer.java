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

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Provider;
import javax.json.Json;
import javax.json.JsonValue;

import io.helidon.common.CollectionsHelper;
import io.helidon.microprofile.jwt.auth.JsonWebTokenImpl;

import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.ClaimValue;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Class MetricProducer.
 */
public class ClaimProducer implements Bean<Object> {
    private static final Annotation QUALIFIER = new Claim() {
        @Override
        public String value() {
            return "";
        }

        @Override
        public Claims standard() {
            return Claims.UNKNOWN;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Claim.class;
        }
    };

    private final JwtAuthCdiExtension.MpClaimQualifier qualifier;
    private final Type type;
    private final BeanManager bm;

    ClaimProducer(JwtAuthCdiExtension.MpClaimQualifier q, Type type, BeanManager bm) {
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

    static Object getClaimValue(String claimName,
                                JsonWebTokenImpl webToken,
                                JwtAuthCdiExtension.MpClaimQualifier q) {

        if (q.rawType() == q.typeArg()) {
            // not a generic
            //return config.getValue(q.getQualifier().rawType());
            //TODO
            if (JsonValue.class.isAssignableFrom(q.rawType())) {
                return Json.createValue((String)webToken.getClaim(claimName));
            }
            return webToken.getClaim(claimName);
        }
        // generic declaration
        return getParametrizedClaimValue(claimName,
                                         webToken,
                                         q.rawType(),
                                         q.typeArg(),
                                         q.typeArg2());
    }

    @SuppressWarnings("unchecked")
    static Object getParametrizedClaimValue(String claimName,
                                            JsonWebToken webToken,
                                            Class rawType,
                                            Class typeArg,
                                            Class typeArg2) {

        if (rawType.isAssignableFrom(ClaimValue.class)) {
            if (typeArg == typeArg2) {
                //ClaimValue
                return Optional.ofNullable(webToken.claim(claimName));
            } else {
                return Optional
                        .ofNullable(getParametrizedClaimValue(claimName,
                                                              webToken,
                                                              typeArg,
                                                              typeArg2,
                                                              typeArg2));
            }
        } else if (rawType.isAssignableFrom(Optional.class)) {
            if (typeArg == typeArg2) {
                return Optional.ofNullable(webToken.claim(claimName));
            } else {
                return Optional
                        .ofNullable(getParametrizedClaimValue(claimName,
                                                              webToken,
                                                              typeArg,
                                                              typeArg2,
                                                              typeArg2));
            }
        } else if (rawType.isAssignableFrom(Set.class)
                && typeArg.isAssignableFrom(String.class)) {
            return Stream.of(webToken.claim(claimName)).collect(Collectors.toSet());
        } else if (rawType.isAssignableFrom(String.class)
                || rawType.isAssignableFrom(Boolean.class)
                || rawType.isAssignableFrom(Long.class)) {
            return webToken.getClaim(claimName);
        } else {
            throw new DeploymentException("Type " + rawType + " is not supported.");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object create(CreationalContext<Object> context) {
        Object value = getClaimValue(context);
        if (null == value && qualifier.rawType().isPrimitive()) {
            // primitive field, not configured, no default
            throw new IllegalStateException("zmenit jeste");
        }

        return value;
    }

    private Object getClaimValue(CreationalContext<Object> context) {
        JsonWebTokenImpl token = CDI.current().select(JsonWebTokenImpl.class, new Impl(){
            @Override
            public Class<? extends Annotation> annotationType() {
                return Impl.class;
            }
        }).get();
        return getClaimValue(qualifier.name(), token, qualifier);
    }

    @Override
    public Class<?> getBeanClass() {
        return ClaimProducer.class;
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
        return qualifier.id();
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
        return "ClaimProducer{"
                + "qualifier=" + qualifier
                + '}';
    }
}
