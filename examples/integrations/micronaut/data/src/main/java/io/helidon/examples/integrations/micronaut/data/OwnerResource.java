/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.micronaut.data;

import io.helidon.examples.integrations.micronaut.data.model.Owner;

import jakarta.inject.Inject;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * JAX-RS resource, and the MicroProfile entry point to manage pet owners.
 * This resource used Micronaut data beans (repositories) to query database, and
 * bean validation as implemented by Micronaut.
 */
@Path("/owners")
public class OwnerResource {
    private final DbOwnerRepository ownerRepository;

    /**
     * Create a new instance with repository.
     *
     * @param ownerRepo owner repository from Micronaut data
     */
    @Inject
    public OwnerResource(DbOwnerRepository ownerRepo) {
        this.ownerRepository = ownerRepo;
    }

    /**
     * Gets all owners from the database.
     * @return all owners, using JSON-B to map them to JSON
     */
    @GET
    public Iterable<Owner> getAll() {
        return ownerRepository.findAll();
    }

    /**
     * Get a named owner from the database.
     *
     * @param name name of the owner to find, must be at least two characters long, may contain whitespace
     * @return a single owner
     * @throws jakarta.ws.rs.NotFoundException in case the owner is not in the database (to return 404 status)
     */
    @Path("/{name}")
    @GET
    @Timed
    public Owner owner(@PathParam("name") @Pattern(regexp = "\\w+[\\w+\\s?]*\\w") String name) {
        return ownerRepository.findByName(name)
                .orElseThrow(() -> new NotFoundException("Owner by name " + name + " does not exist"));
    }
}
