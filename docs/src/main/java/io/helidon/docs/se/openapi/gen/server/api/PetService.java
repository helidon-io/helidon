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

import java.util.List;
import java.util.Optional;

import io.helidon.docs.se.openapi.gen.model.Pet;
import io.helidon.http.media.multipart.ReadablePart;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

public class PetService {

    protected void handleAddPet(ServerRequest request, ServerResponse response, Pet pet) {
    }

    protected void handleDeletePet(ServerRequest request, ServerResponse response,
                                   Long petId,
                                   Optional<String> apiKey) {
    }

    protected void handleFindPetsByStatus(ServerRequest request, ServerResponse response,
                                          List<String> status) {
    }

    protected void handleFindPetsByTags(ServerRequest request, ServerResponse response,
                                        List<String> tags) {
    }

    protected AddPetOp createAddPetOp() {
        return null;
    }

    protected void handleGetPetById(ServerRequest request, ServerResponse response,
                                    Long petId) {
    }

    protected void handlePetInfoWithCookies(ServerRequest request, ServerResponse response,
                                            Long petId,
                                            Long transactionId,
                                            String label) {
    }

    protected void handleUpdatePet(ServerRequest request, ServerResponse response,
                                   Pet pet) {
    }

    protected void handleUpdatePetWithForm(ServerRequest request, ServerResponse response,
                                           Long petId,
                                           Optional<String> name,
                                           Optional<String> status) {
    }

    protected void handleUploadFile(ServerRequest request, ServerResponse response,
                                    Long petId,
                                    Optional<ReadablePart> additionalMetadata,
                                    Optional<ReadablePart> _file) {
    }


    public static class AddPetOp {

        protected Pet pet(ServerRequest request, ValidatorUtils.Validator validator) {
            return null;
        }

        public record Response405() {

            public static Response405.Builder builder() {
                return new Builder();
            }

            public static class Builder {
                void send(ServerResponse response) {
                }
            }
        }

        public record Response200(Pet response) {

            public static Response200.Builder builder() {
                return new Builder();
            }

            public static class Builder {
                void send(ServerResponse response) {

                }
            }
        }
    }

    public static class FindPetsByTagsOp {

        public record Response200() {

            public static Response200.Builder builder() {
                return new Response200.Builder();
            }

            public static class Builder {
                public void send(ServerResponse response) {
                }

                public Builder response(List<Pet> result) {
                    return this;
                }
            }
        }
    }
}
