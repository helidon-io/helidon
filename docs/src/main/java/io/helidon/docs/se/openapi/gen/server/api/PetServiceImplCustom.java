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

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.io.File;
import java.util.HashMap;

import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import java.util.HexFormat;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import io.helidon.http.media.multipart.MultiPart;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import io.helidon.common.parameters.Parameters;
import io.helidon.docs.se.openapi.gen.server.model.Pet;
import io.helidon.http.media.multipart.ReadablePart;
import java.util.Set;
import io.helidon.http.Status;
import java.io.UncheckedIOException;
import io.helidon.common.mapper.Value;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

// Use a wrapper class so we can use the actual name of the generated class on the
// inner class. If we name this outer class PetServiceImpl that conflicts with the
// file of the same name (and in the same package) that the generator created.
public class PetServiceImplCustom {
// tag::class-declaration[]
// tag::class-header[]
public class PetServiceImpl extends PetService {
// end::class-header[]
// tag::addedFields[]

    private final Map<Long, Pet> pets = new HashMap<>(); // <1>

// end::addedFields[]
// tag::handleAddPetDecl[]
    @Override
    protected void handleAddPet(ServerRequest request, ServerResponse response,
                                Pet pet) {
// end::handleAddPetDecl[]
// tag::handleAddPetGenerated[]
        response.status(Status.NOT_IMPLEMENTED_501).send();
// end::handleAddPetGenerated[]
// tag::handleAddPetCustom[]
        if (pets.containsKey(pet.getId())) {            // <2>
            AddPetOp.Response405.builder().send(response);
        }
        pets.put(pet.getId(), pet);                     // <3>
        AddPetOp.Response200.builder().send(response);  // <4>
// end::handleAddPetCustom[]
// tag::handleAddPetEnd[]
    }
// end::handleAddPetEnd[]

    @Override
    protected void handleDeletePet(ServerRequest request, ServerResponse response,
                                   Long petId,
                                   Optional<String> apiKey) {

        response.status(Status.NOT_IMPLEMENTED_501).send();
    }

    @Override
    protected void handleFindPetsByStatus(ServerRequest request, ServerResponse response,
                                          List<String> status) {

        response.status(Status.NOT_IMPLEMENTED_501).send();
    }

// tag::handleFindPetsByTagsDecl[]
    @Override
    protected void handleFindPetsByTags(ServerRequest request, ServerResponse response,
                                       List<String> tags) { // <2>
// end::handleFindPetsByTagsDecl[]
// tag::handleFindPetsByTagsCustom[]

        List<Pet> result = pets.values().stream()
                .filter(pet -> pet.getTags()
                        .stream()
                        .anyMatch(petTag -> tags.contains(petTag.getName())))
                .toList();                                  // <3>

        FindPetsByTagsOp.Response200.builder()              // <4>
                .response(result)                           // <5>
                .send(response);                            // <6>

// end::handleFindPetsByTagsCustom[]
        response.status(Status.NOT_IMPLEMENTED_501).send();
// tag::handleFindPetsByTagsEnd[]
    }
// end::handleFindPetsByTagsEnd[]

// tag::createAddPetOp[]
    @Override
    protected AddPetOp createAddPetOp() {
        return new AddPetOpCustom();
    }
// end::createAddPetOp[]
    @Override
    protected void handleGetPetById(ServerRequest request, ServerResponse response,
                                    Long petId) {

        response.status(Status.NOT_IMPLEMENTED_501).send();
    }

    @Override
    protected void handlePetInfoWithCookies(ServerRequest request, ServerResponse response,
                                            Long petId,
                                            Long transactionId,
                                            String label) {

        response.status(Status.NOT_IMPLEMENTED_501).send();
    }

    @Override
    protected void handleUpdatePet(ServerRequest request, ServerResponse response,
                                   Pet pet) {

        response.status(Status.NOT_IMPLEMENTED_501).send();
    }

    @Override
    protected void handleUpdatePetWithForm(ServerRequest request, ServerResponse response,
                                           Long petId,
                                           Optional<String> name,
                                           Optional<String> status) {

        response.status(Status.NOT_IMPLEMENTED_501).send();
    }

    @Override
    protected void handleUploadFile(ServerRequest request, ServerResponse response,
                                    Long petId,
                                    Optional<ReadablePart> additionalMetadata,
                                    Optional<ReadablePart> _file) {

        response.status(Status.NOT_IMPLEMENTED_501).send();
    }

}}


// end::class-declaration[]
