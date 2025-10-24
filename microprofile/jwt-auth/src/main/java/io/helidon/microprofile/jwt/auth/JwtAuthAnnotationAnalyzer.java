/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

import java.lang.reflect.Method;
import java.util.List;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.metadata.reflection.AnnotationFactory;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer;

import jakarta.annotation.security.RolesAllowed;
import org.eclipse.microprofile.auth.LoginConfig;

import static io.helidon.microprofile.jwt.auth.JwtAuthProviderService.PROVIDER_NAME;

/**
 * Implementation of {@link AnnotationAnalyzer} which checks for {@link LoginConfig} annotation if
 * JWT Authentication should be enabled.
 */
// weighted to run before RoleAnnotationAnalyzer, so we do not have to handle PermitAll
@Weight(Weighted.DEFAULT_WEIGHT + 100) // Helidon service loader loaded and ordered
public class JwtAuthAnnotationAnalyzer implements AnnotationAnalyzer {
    static final String LOGIN_CONFIG_METHOD = "MP-JWT";
    static final TypeName LOGIN_CONFIG = TypeName.create(LoginConfig.class);
    private static final TypeName ROLES_ALLOWED = TypeName.create(RolesAllowed.class);

    private String authenticator = PROVIDER_NAME;
    private boolean secureByDefault;

    static boolean isMpJwt(Annotation config) {
        return config.stringValue("authMethod")
                .orElse("")
                .equals(LOGIN_CONFIG_METHOD);
    }

    @SuppressWarnings("removal")
    @Override
    public void init(Config config) {
        config.get(PROVIDER_NAME + ".auth-method-mapping")
                .asNodeList()
                .ifPresent(nl -> {
                    nl.forEach(conf -> {
                        conf.get("key").asString().ifPresent(key -> {
                            if (LOGIN_CONFIG_METHOD.equals(key)) {
                                authenticator = conf.get("provider")
                                        .asString()
                                        .orElse(authenticator);
                            }
                        });
                    });
                });
        secureByDefault = config.get("jwt.secure-by-default")
                .asBoolean()
                .orElse(true);
    }

    @Override
    public AnalyzerResponse analyze(TypeName applicationType, List<Annotation> annotations) {
        AnalyzerResponse.Builder builder = AnalyzerResponse.builder();

        var loginConfigAnnotation = Annotations.findFirst(LOGIN_CONFIG, annotations);

        if (loginConfigAnnotation.isEmpty() || !isMpJwt(loginConfigAnnotation.get())) {
            builder.register(new RegisterMpJwt(false));
            return AnalyzerResponse.abstain();
        }

        // triggered!
        builder.register(new RegisterMpJwt(true));

        // now if there is a RolesAllowed on application class, automatically required, otherwise optional
        // bugfix #455
        // we may want to only authenticate requests that hit a @RolesAllowed annotated endpoint
        Flag atnFlag = (secureByDefault ? Flag.REQUIRED : Flag.OPTIONAL);

        if (isRolesAllowed(annotations)) {
            atnFlag = Flag.REQUIRED;
        }

        return builder.authenticationResponse(atnFlag)
                .authenticator(authenticator)
                .build();
    }

    @Override
    public AnalyzerResponse analyze(TypeName endpointType, List<Annotation> annotations, AnalyzerResponse previousResponse) {
        return AnalyzerResponse.builder(previousResponse)
                .build();
    }

    @Override
    public AnalyzerResponse analyze(TypeName typeName, TypedElementInfo method, AnalyzerResponse previousResponse) {
        if (method.hasAnnotation(ROLES_ALLOWED)) {
            return AnalyzerResponse.builder(previousResponse)
                    .authenticationResponse(Flag.REQUIRED)
                    .build();
        }
        return AnalyzerResponse.builder(previousResponse)
                .build();
    }

    // application class analysis
    @Override
    public AnalyzerResponse analyze(Class<?> maybeAnnotated) {
        return analyze(TypeName.create(maybeAnnotated), AnnotationFactory.create(maybeAnnotated));
    }

    // resource class analysis
    @SuppressWarnings("removal")
    @Override
    public AnalyzerResponse analyze(Class<?> maybeAnnotated, AnalyzerResponse previousResponse) {
        return AnalyzerResponse.builder(previousResponse)
                .build();
    }

    // resource method analysis
    @SuppressWarnings("removal")
    @Override
    public AnalyzerResponse analyze(Method maybeAnnotated, AnalyzerResponse previousResponse) {
        return analyze(TypeName.create(maybeAnnotated.getDeclaringClass()),
                       AnnotationFactory.create(maybeAnnotated),
                       previousResponse);
    }

    @Override
    public String toString() {
        return "JwtAuthAnnotationAnalyzer{"
                + "authenticator='" + authenticator + '\''
                + ", secureByDefault=" + secureByDefault
                + '}';
    }

    private boolean isRolesAllowed(List<Annotation> annotations) {
        return Annotations.findFirst(ROLES_ALLOWED, annotations)
                .isPresent();
    }

    private static final class RegisterMpJwt {
        private final boolean isMpJwt;

        private RegisterMpJwt(boolean isMpJwt) {
            this.isMpJwt = isMpJwt;
        }

        public boolean isMpJwt() {
            return isMpJwt;
        }
    }
}
