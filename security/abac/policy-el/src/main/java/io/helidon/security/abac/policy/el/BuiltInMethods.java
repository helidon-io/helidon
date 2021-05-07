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

package io.helidon.security.abac.policy.el;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.security.Grant;
import io.helidon.security.Security;
import io.helidon.security.Subject;

/**
 * Built-in methods for expression language evaluation.
 * These methods are bound in default namespace (without a prefix). Name to use in an expression is the name of the method.
 */
public final class BuiltInMethods {
    private BuiltInMethods() {

    }

    /**
     * Check if subject has the specified role.
     *
     * @param subject subject of a user or a service
     * @param role    role the subject should be in
     * @return true if the subject is in the role
     */
    public static boolean inRole(Subject subject, String role) {
        return inRoles(subject, role);
    }

    /**
     * Check if subject has the specified roles (must have all of them).
     * If you need to check that subject is in EITHER of the roles, you should combine this will or operator (e.g.
     * inRole(user, "manager") || inRole(user, "admin"))
     *
     * @param subject subject of a user or a service
     * @param roles   roles the subject should be in
     * @return true if the subject is in all the specified roles
     */
    public static boolean inRoles(Subject subject, String... roles) {
        Set<String> grants = Security.getRoles(subject);

        for (String role : roles) {
            if (!grants.contains(role)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if subject has the specified scope.
     *
     * @param subject subject of a user
     * @param scope   scope the subject should have
     * @return true if the subject has all the specified scopes
     */
    public static boolean inScope(Subject subject, String scope) {
        List<String> grants = subject.grantsByType("scope").stream().map(Grant::getName).collect(Collectors.toList());

        return grants.contains(scope);
    }

    /**
     * Check if subject has the specified scopes (must have all of them).
     *
     * @param subject subject of a user
     * @param scopes  scopes the subject should have
     * @return true if the subject has all the specified scopes
     */
    public static boolean inScopes(Subject subject, String... scopes) {
        List<String> grants = subject.grantsByType("scope").stream().map(Grant::getName).collect(Collectors.toList());
        for (String scope : scopes) {
            if (!grants.contains(scope)) {
                return false;
            }
        }
        return true;
    }
}
