/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.examples.microprofile.multipart;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.BodyPartEntity;
import org.glassfish.jersey.media.multipart.MultiPart;

/**
 * File service.
 */
@Path("/api")
@ApplicationScoped
public class FileService {

    private static final JsonBuilderFactory JSON_FACTORY = Json.createBuilderFactory(Map.of());

    private final FileStorage storage;

    @Inject
    FileService(FileStorage storage) {
        this.storage = storage;
    }

    /**
     * Upload a file to the storage.
     * @param multiPart multipart entity
     * @return Response
     * @throws IOException if an IO error occurs
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(MultiPart multiPart) throws IOException {
        for (BodyPart part : multiPart.getBodyParts()) {
            if ("file[]".equals(part.getContentDisposition().getParameters().get("name"))) {
                Files.copy(part.getEntityAs(BodyPartEntity.class).getInputStream(),
                        storage.create(part.getContentDisposition().getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return Response.seeOther(URI.create("ui")).build();
    }

    /**
     * Download a file from the storage.
     * @param fname file name of the file to download
     * @return Response
     */
    @GET
    @Path("{fname}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@PathParam("fname") String fname) {
        return Response.ok()
                       .header("Content-Disposition", "attachment; filename=\"" + fname + "\"")
                       .entity((StreamingOutput) output -> Files.copy(storage.lookup(fname), output))
                       .build();
    }

    /**
     * List the files in the storage.
     * @return JsonObject
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject list() {
        JsonArrayBuilder arrayBuilder = JSON_FACTORY.createArrayBuilder();
        storage.listFiles().forEach(arrayBuilder::add);
        return JSON_FACTORY.createObjectBuilder().add("files", arrayBuilder).build();
    }
}
