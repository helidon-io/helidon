/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.grpc.server;

import java.util.List;
import java.util.Optional;

record GrpcSecurityDefinition(Optional<Boolean> authenticate,
                              boolean authenticationOptional,
                              Optional<String> authenticator,
                              Optional<Boolean> authorize,
                              Optional<String> authorizer,
                              List<String> rolesAllowed) {
    private static final GrpcSecurityDefinition EMPTY = new GrpcSecurityDefinition(Optional.empty(),
                                                                                  false,
                                                                                  Optional.empty(),
                                                                                  Optional.empty(),
                                                                                  Optional.empty(),
                                                                                  List.of());

    static GrpcSecurityDefinition empty() {
        return EMPTY;
    }

    boolean isEmpty() {
        return authenticate.isEmpty()
                && !authenticationOptional
                && authenticator.isEmpty()
                && authorize.isEmpty()
                && authorizer.isEmpty()
                && rolesAllowed.isEmpty();
    }
}
