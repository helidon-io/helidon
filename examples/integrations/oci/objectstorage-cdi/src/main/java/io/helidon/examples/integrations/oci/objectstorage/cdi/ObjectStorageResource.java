/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.Http;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetNamespaceResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * JAX-RS resource - REST API for the objecstorage example.
 */
@Path("/files")
public class ObjectStorageResource {
    private static final Logger LOGGER = Logger.getLogger(ObjectStorageResource.class.getName());
    private final ObjectStorage objectStorageClient;
    private final String namespaceName;
    private final String bucketName;

    @Inject
    ObjectStorageResource(ObjectStorage objectStorageClient,
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

        try (InputStream fileStream = getObjectResponse.getInputStream()) {
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
     * @return response
     */
    @POST
    @Path("/file/{fileName}")
    public Response upload(@PathParam("fileName") String fileName) {

        PutObjectRequest putObjectRequest = null;
        try (InputStream stream = new FileInputStream(System.getProperty("user.dir") + File.separator + fileName)) {
            byte[] contents = stream.readAllBytes();
            putObjectRequest =
                    PutObjectRequest.builder()
                            .namespaceName(namespaceName)
                            .bucketName(bucketName)
                            .objectName(fileName)
                            .putObjectBody(new ByteArrayInputStream(contents))
                            .contentLength(Long.valueOf(contents.length))
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
