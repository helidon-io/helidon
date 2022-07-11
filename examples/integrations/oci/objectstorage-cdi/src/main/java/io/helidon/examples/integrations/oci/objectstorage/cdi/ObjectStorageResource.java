/*
 * Copyright (c) 2021,2022 Oracle and/or its affiliates.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
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

import io.helidon.common.http.Http;

import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetNamespaceResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * JAX-RS resource - REST API for the objecstorage example.
 */
@Path("/files")
public class ObjectStorageResource {
    private static final Logger LOGGER = Logger.getLogger(ObjectStorageResource.class.getName());
    private final ObjectStorageClient objectStorageClient;
    private final String namespaceName;
    private final String bucketName;

    @Inject
    ObjectStorageResource(ObjectStorageClient objectStorageClient,
                          @ConfigProperty(name = "oci.objectstorage.bucketName")
                          String bucketName) {
        this.objectStorageClient = objectStorageClient;
        this.bucketName = bucketName;
        GetNamespaceResponse namespaceResponse =
                this.objectStorageClient.getNamespace(GetNamespaceRequest.builder().build());
        this.namespaceName = namespaceResponse.getValue();
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
        GetObjectResponse getObjectResponse =
                objectStorageClient.getObject(
                        GetObjectRequest.builder()
                                .namespaceName(namespaceName)
                                .bucketName(bucketName)
                                .objectName(fileName)
                                .build());

        if (getObjectResponse.getContentLength() == 0) {
            LOGGER.log(Level.SEVERE, "GetObjectResponse is empty");
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        try (final InputStream fileStream = getObjectResponse.getInputStream()) {
            byte[] objectContent = fileStream.readAllBytes();
            Response.ResponseBuilder ok = Response.ok(objectContent)
                    .header(Http.Header.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .header("opc-request-id", getObjectResponse.getOpcRequestId())
                    .header("request-id", getObjectResponse.getOpcClientRequestId())
                    .header(Http.Header.CONTENT_LENGTH, getObjectResponse.getContentLength());

            return ok.build();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing GetObjectResponse", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
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

        PutObjectRequest putObjectRequest = null;
        try {
            putObjectRequest =
                    PutObjectRequest.builder()
                            .namespaceName(namespaceName)
                            .bucketName(bucketName)
                            .objectName(fileName)
                            .contentLength(contentLength)
                            .putObjectBody(
                                    new ByteArrayInputStream(entity.readAllBytes()))
                            .build();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error creating PutObjectRequest", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        PutObjectResponse putObjectResponse = objectStorageClient.putObject(putObjectRequest);

        Response.ResponseBuilder ok = Response.ok()
                .header("opc-request-id", putObjectResponse.getOpcRequestId())
                .header("request-id", putObjectResponse.getOpcClientRequestId());

        return ok.build();
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
        DeleteObjectResponse deleteObjectResponse = objectStorageClient.deleteObject(DeleteObjectRequest.builder()
                                                                                             .namespaceName(namespaceName)
                                                                                             .bucketName(bucketName)
                                                                                             .objectName(fileName)
                                                                                             .build());
        Response.ResponseBuilder ok = Response.ok()
                .header("opc-request-id", deleteObjectResponse.getOpcRequestId())
                .header("request-id", deleteObjectResponse.getOpcClientRequestId());

        return ok.build();
    }
}
