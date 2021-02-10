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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

import javax.annotation.Priority;
import javax.annotation.security.RolesAllowed;

import io.helidon.common.Prioritized;
import io.helidon.config.Config;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer;

import org.eclipse.microprofile.auth.LoginConfig;

import static io.helidon.microprofile.jwt.auth.JwtAuthProviderService.PROVIDER_NAME;

/**
 * Implementation of {@link AnnotationAnalyzer} which checks for {@link LoginConfig} annotation if
 * JWT Authentication should be enabled.
 */
// prioritized to run before RoleAnnotationAnalyzer, so we do not have to handle PermitAll
@Priority(Prioritized.DEFAULT_PRIORITY - 100)
public class JwtAuthAnnotationAnalyzer implements AnnotationAnalyzer {
    static final String LOGIN_CONFIG_METHOD = "MP-JWT";

    private String authenticator = PROVIDER_NAME;
    private boolean secureByDefault;

    static boolean isMpJwt(LoginConfig config) {
        return LOGIN_CONFIG_METHOD.equals(config.authMethod());
    }

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

    // application class analysis
    @Override
    public AnalyzerResponse analyze(Class<?> maybeAnnotated) {
        AnalyzerResponse.Builder builder = AnalyzerResponse.builder();

        LoginConfig annotation = maybeAnnotated.getAnnotation(LoginConfig.class);

        if (null == annotation) {
            builder.register(new RegisterMpJwt(false));
            return AnalyzerResponse.abstain();
        }

        if (isMpJwt(annotation)) {
            // triggered!
            builder.register(new RegisterMpJwt(true));

            // now if there is a RolesAllowed on application class, automatically required, otherwise optional
            // bugfix #455
            // we may want to only authenticate requests that hit a @RolesAllowed annotated endpoint
            Flag atnFlag = (secureByDefault ? Flag.REQUIRED : Flag.OPTIONAL);

            if (isRolesAllowed(maybeAnnotated)) {
                atnFlag = Flag.REQUIRED;
            }

            return builder.authenticationResponse(atnFlag)
                    .authenticator(authenticator)
                    .build();
        }

        builder.register(new RegisterMpJwt(false));
        return builder.build();
    }

    // resource class analysis
    @Override
    public AnalyzerResponse analyze(Class<?> maybeAnnotated, AnalyzerResponse previousResponse) {
        return AnalyzerResponse.builder(previousResponse)
                .build();
    }

    // resource method analysis
    @Override
    public AnalyzerResponse analyze(Method maybeAnnotated, AnalyzerResponse previousResponse) {
        if (isRolesAllowed(maybeAnnotated)) {
            return AnalyzerResponse.builder(previousResponse)
                    .authenticationResponse(Flag.REQUIRED)
                    .build();
        }
        return AnalyzerResponse.builder(previousResponse)
                .build();
    }

    private boolean isRolesAllowed(AnnotatedElement maybeAnnotated) {
        RolesAllowed ra = maybeAnnotated.getAnnotation(RolesAllowed.class);
        return null != ra;
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
