/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.examples.abac;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Grant;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.SecurityLevel;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.SynchronousProvider;

/**
 * Example authentication provider that reads annotation to create a subject.
 */
public class AtnProvider extends SynchronousProvider implements AuthenticationProvider {
    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        List<SecurityLevel> securityLevels = providerRequest.endpointConfig().securityLevels();
        ListIterator<SecurityLevel> listIterator = securityLevels.listIterator(securityLevels.size());
        Subject user = null;
        Subject service = null;
        while (listIterator.hasPrevious()) {
            SecurityLevel securityLevel = listIterator.previous();
            List<Authentications> authenticationAnnots = securityLevel
                    .filterAnnotations(Authentications.class, EndpointConfig.AnnotationScope.METHOD);

            List<Authentication> authentications = new LinkedList<>();
            authenticationAnnots.forEach(atn -> authentications.addAll(Arrays.asList(atn.value())));


            if (!authentications.isEmpty()) {
                for (Authentication authentication : authentications) {
                    if (authentication.type() == SubjectType.USER) {
                        user = buildSubject(authentication);
                    } else {
                        service = buildSubject(authentication);
                    }
                }
                break;
            }
        }
        return AuthenticationResponse.success(user, service);
    }

    private Subject buildSubject(Authentication authentication) {
        Subject.Builder subjectBuilder = Subject.builder();

        subjectBuilder.principal(Principal.create(authentication.value()));
        for (String role : authentication.roles()) {
            subjectBuilder.addGrant(Role.create(role));
        }
        for (String scope : authentication.scopes()) {
            subjectBuilder.addGrant(Grant.builder()
                                            .name(scope)
                                            .type("scope")
                                            .build());
        }

        return subjectBuilder.build();
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of(Authentication.class);
    }

    /**
     * Authentication annotation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    @Documented
    @Inherited
    @Repeatable(Authentications.class)
    public @interface Authentication {
        /**
         * Name of the principal.
         *
         * @return principal name
         */
        String value();

        /**
         * Type of the subject, defaults to user.
         *
         * @return type
         */
        SubjectType type() default SubjectType.USER;

        /**
         * Granted roles.
         * @return array of roles
         */
        String[] roles() default "";

        /**
         * Granted scopes.
         * @return array of scopes
         */
        String[] scopes() default "";
    }

    /**
     * Repeatable annotation for {@link Authentication}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    @Documented
    @Inherited
    public @interface Authentications {
        /**
         * Repeating annotation.
         * @return annotations
         */
        Authentication[] value();
    }
}
