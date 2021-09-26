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

package io.helidon.examples.integrations.oci.objectstorage.cdi;

import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.Optional;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import io.helidon.common.http.Http;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.objectstorage.DeleteObject;
import io.helidon.integrations.oci.objectstorage.GetObject;
import io.helidon.integrations.oci.objectstorage.OciObjectStorage;
import io.helidon.integrations.oci.objectstorage.PutObject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * JAX-RS resource - REST API for the objecstorage example.
 */
@Path("/files")
public class ObjectStorageResource {
    private final OciObjectStorage objectStorage;
    private final String bucketName;

    @Inject
    ObjectStorageResource(OciObjectStorage objectStorage,
                          @ConfigProperty(name = "oci.properties.objectstorage-bucket")
                                  String bucketName) {
        this.objectStorage = objectStorage;
        this.bucketName = bucketName;
    }

    /**
     * Download a file from object storage.
     *
     * @param fileName name of the object
     * @return response
     */
    @GET
    @Path("/file/{file-name}")
    public Response download(@PathParam("file-name") String fileName) {
        ApiOptionalResponse<GetObject.Response> ociResponse = objectStorage.getObject(GetObject.Request.builder()
                                                                                                      .bucket(bucketName)
                                                                                                      .objectName(fileName));
        Optional<GetObject.Response> entity = ociResponse.entity();

        if (entity.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        GetObject.Response response = entity.get();

        StreamingOutput stream = output -> response.writeTo(Channels.newChannel(output));

        Response.ResponseBuilder ok = Response.ok(stream, MediaType.APPLICATION_OCTET_STREAM_TYPE)
                .header(Http.Header.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header("opc-request-id", ociResponse.headers().first("opc-request-id").orElse(""))
                .header("request-id", ociResponse.requestId());

        ociResponse.headers()
                .first(Http.Header.CONTENT_TYPE)
                .ifPresent(ok::type);

        ociResponse.headers()
                .first(Http.Header.CONTENT_LENGTH)
                .ifPresent(it -> ok.header(Http.Header.CONTENT_LENGTH, it));

        return ok.build();
    }

    /**
     * Upload a file to object storage.
     *
     * @param fileName name of the object
     * @param contentLength content length (required)
     * @param type content type
     * @param entity the entity used for upload
     * @return response
     */
    @POST
    @Path("/file/{fileName}")
    public Response upload(@PathParam("fileName") String fileName,
                         @HeaderParam("Content-Length") long contentLength,
                         @HeaderParam("Content-Type") @DefaultValue("application/octet-stream") String type,
                         InputStream entity) {
        PutObject.Response response = objectStorage.putObject(PutObject.Request.builder()
                                                                      .contentLength(contentLength)
                                                                      .bucket(bucketName)
                                                                      .requestMediaType(io.helidon.common.http.MediaType
                                                                                                .parse(type))
                                                                      .objectName(fileName),
                                                              Channels.newChannel(entity));

        return Response.status(response.status().code())
                .header("opc-request-id", response.headers().first("opc-request-id").orElse(""))
                .header("request-id", response.requestId())
                .build();
    }

    /**
     * Delete a file from object storage.
     *
     * @param fileName object name
     * @return response
     */
    @DELETE
    @Path("/file/{file-name}")
    public Response delete(@PathParam("file-name") String fileName) {
        DeleteObject.Response response = objectStorage.deleteObject(DeleteObject.Request.builder()
                                                                            .bucket(bucketName)
                                                                            .objectName(fileName));

        return Response.status(response.status().code())
                .header("opc-request-id", response.headers().first("opc-request-id").orElse(""))
                .header("request-id", response.requestId())
                .build();
    }
}
