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
package io.helidon.microprofile.jwt.auth;

import java.lang.reflect.Method;

import javax.annotation.security.PermitAll;

import io.helidon.config.Config;
import io.helidon.security.jersey.spi.AnnotationAnalyzer;

import org.eclipse.microprofile.auth.LoginConfig;

import static io.helidon.microprofile.jwt.auth.JwtAuthProviderService.PROVIDER_NAME;

/**
 * TODO javadoc.
 */
public class JwtAuthAnnotationAnalyzer implements AnnotationAnalyzer {
    static final String LOGIN_CONFIG_METHOD = "MP-JWT";

    private String authenticator = PROVIDER_NAME;

    static boolean isMpJwt(LoginConfig config) {
        return LOGIN_CONFIG_METHOD.equals(config.authMethod());
    }

    @Override
    public void init(Config config) {
        config.get(PROVIDER_NAME + ".auth-method-mapping")
                .asOptionalNodeList()
                .ifPresent(nl -> {
                    nl.forEach(conf -> {
                        conf.get("key").value().ifPresent(key -> {
                            if (LOGIN_CONFIG_METHOD.equals(key)) {
                                authenticator = conf.get("provider")
                                        .value()
                                        .orElse(authenticator);
                            }
                        });
                    });
                });
    }

    @Override
    public AnalyzerResponse analyze(Class<?> maybeAnnotated) {
        AnalyzerResponse.Builder builder = AnalyzerResponse.builder();

        LoginConfig annotation = maybeAnnotated.getAnnotation(LoginConfig.class);

        if (null == annotation) {
            return AnalyzerResponse.abstain();
        }

        if (isMpJwt(annotation)) {
            return builder.authenticationResponse(Flag.REQUIRED)
                    .authenticator(authenticator)
                    .build();
        }

        return builder.build();
    }

    @Override
    public AnalyzerResponse analyze(Class<?> maybeAnnotated, AnalyzerResponse previousResponse) {
        return analyze(maybeAnnotated.getAnnotation(PermitAll.class), previousResponse);
    }

    @Override
    public AnalyzerResponse analyze(Method maybeAnnotated, AnalyzerResponse previousResponse) {
        return analyze(maybeAnnotated.getAnnotation(PermitAll.class), previousResponse);

    }

    private static AnalyzerResponse analyze(PermitAll annotation, AnalyzerResponse previousResponse) {
        if (null == annotation) {
            return AnalyzerResponse.abstain();
        }

        return AnalyzerResponse.builder(previousResponse)
                .authenticationResponse(Flag.OPTIONAL)
                .build();
    }
}
