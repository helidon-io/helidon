/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.se.openapi.gen.server.api;

import io.helidon.docs.se.openapi.gen.model.Pet;
import io.helidon.webserver.http.ServerRequest;

// tag::classDecl[]
public class AddPetOpCustom extends PetService.AddPetOp {
// end::classDecl[]

// tag::petMethod[]
    @Override
    protected Pet pet(ServerRequest request, ValidatorUtils.Validator validator) {
        Pet result = request.content().hasEntity()          // <1>
                ? request.content().as(Pet.class)
                : null;

        /*
         Insist that pet names never start with a lower-case letter.
         */
        if (result != null) {
            validator.validatePattern("pet", result.getName(), "[^a-z].*"); // <2>
        }
        return result;                                      // <3>
    }
// end::petMethod[]
}
