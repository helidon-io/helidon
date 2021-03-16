/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.BodyPartEntity;
import org.glassfish.jersey.media.multipart.MultiPart;

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
