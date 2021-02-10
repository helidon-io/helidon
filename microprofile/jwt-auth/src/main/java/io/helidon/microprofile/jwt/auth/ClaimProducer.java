/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Provider;

import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.Claims;

/**
 * Class ClaimProducer.
 */
class ClaimProducer implements Bean<Object> {
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
    private final Class<? extends Annotation> scope;

    ClaimProducer(JwtAuthCdiExtension.MpClaimQualifier q,
                  Type type,
                  Class<? extends Annotation> scope) {
        this.qualifier = q;
        this.scope = scope;
        Type actualType = type;
        if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            if (Provider.class.equals(paramType.getRawType())
                    || Instance.class.equals(paramType.getRawType())) {
                actualType = paramType.getActualTypeArguments()[0];
            }
        }

        this.type = actualType;
    }

    static Object getClaimValue(String claimName,
                                JsonWebTokenImpl webToken,
                                JwtAuthCdiExtension.MpClaimQualifier q) {
        return getParametrizedClaimValue(claimName,
                                         webToken,
                                         q);
    }

    @SuppressWarnings("unchecked")
    static Object getParametrizedClaimValue(String claimName,
                                            JsonWebTokenImpl webToken,
                                            JwtAuthCdiExtension.MpClaimQualifier claimLiteral) {

        if (null == webToken) {
            // not in MP-JWT scope
            return null;
        }

        Object result;
        if (claimLiteral.claimValue()) {
            if (claimLiteral.optional()) {
                result = webToken.getClaim(claimName, claimLiteral.typeArg2());
            } else {
                result = webToken.getClaim(claimName, claimLiteral.typeArg());
            }
        } else if (claimLiteral.optional()) {
            result = webToken.getClaim(claimName, claimLiteral.typeArg());
        } else {
            result = webToken.getClaim(claimName, claimLiteral.rawType());
        }

        if (claimLiteral.optional()) {
            result = Optional.ofNullable(result);
        }
        if (claimLiteral.claimValue()) {
            result = new ClaimValueWrapper<>(claimName, result);
        }
        return result;
    }

    @Override
    public Object create(CreationalContext<Object> context) {
        return getClaimValue(context);
    }

    private Object getClaimValue(CreationalContext<Object> context) {
        JsonWebTokenImpl token = CDI.current().select(JsonWebTokenImpl.class, new Impl() {
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
        return scope;
    }

    @Override
    public String getName() {
        return qualifier.id();
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
        return "ClaimProducer{"
                + "qualifier=" + qualifier
                + '}';
    }
}
