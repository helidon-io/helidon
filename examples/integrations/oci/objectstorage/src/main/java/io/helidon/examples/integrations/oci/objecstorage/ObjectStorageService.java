/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.oci.objecstorage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.http.Http;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetNamespaceResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;

/**
 * REST API for the objecstorage example.
 */
public class ObjectStorageService implements HttpService {
    private static final Logger LOGGER = Logger.getLogger(ObjectStorageService.class.getName());
    private final ObjectStorage objectStorageClient;
    private final String namespaceName;
    private final String bucketName;


    ObjectStorageService(Config config) {
        try {
            ConfigFileReader.ConfigFile configFile = ConfigFileReader.parseDefault();
            AuthenticationDetailsProvider authProvider = new ConfigFileAuthenticationDetailsProvider(configFile);
            this.objectStorageClient = ObjectStorageClient.builder().build(authProvider);
            this.bucketName = config.get("oci.objectstorage.bucketName")
                    .asString()
                    .orElseThrow(() -> new IllegalStateException("Missing bucket name!!"));
            GetNamespaceResponse namespaceResponse =
                    this.objectStorageClient.getNamespace(GetNamespaceRequest.builder().build());
            this.namespaceName = namespaceResponse.getValue();
        } catch (IOException e) {
            throw new ConfigException("Failed to read configuration properties", e);
        }
    }

    /**
     * A service registers itself by updating the routine rules.
     *
     * @param rules the routing rules.
     */
    public void routing(HttpRules rules) {
        rules.get("/file/{file-name}", this::download);
        rules.post("/file/{fileName}", this::upload);
        rules.delete("/file/{file-name}", this::delete);
    }

    /**
     * Download a file from object storage.
     *
     * @param request  request
     * @param response response
     */
    public void download(ServerRequest request, ServerResponse response) {
        String fileName = request.path().pathParameters().get("file-name");
        GetObjectResponse getObjectResponse =
                objectStorageClient.getObject(
                        GetObjectRequest.builder()
                                .namespaceName(namespaceName)
                                .bucketName(bucketName)
                                .objectName(fileName)
                                .build());

        if (getObjectResponse.getContentLength() == 0) {
            LOGGER.log(Level.SEVERE, "GetObjectResponse is empty");
            response.status(Http.Status.NOT_FOUND_404).send();
            return;
        }

        try (InputStream fileStream = getObjectResponse.getInputStream()) {
            byte[] objectContent = fileStream.readAllBytes();
            response
                    .status(Http.Status.OK_200)
                    .header(Http.HeaderNames.CONTENT_DISPOSITION.defaultCase(), "attachment; filename=\"" + fileName + "\"")
                    .header("opc-request-id", getObjectResponse.getOpcRequestId())
                    .header(Http.HeaderNames.CONTENT_LENGTH.defaultCase(), getObjectResponse.getContentLength().toString());

            response.send(objectContent);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing GetObjectResponse", e);
            response.status(Http.Status.INTERNAL_SERVER_ERROR_500).send();
        }
    }

    /**
     * Upload a file to object storage.
     *
     * @param request  request
     * @param response response
     */
    public void upload(ServerRequest request, ServerResponse response) {
        String fileName = request.path().pathParameters().get("fileName");
        PutObjectRequest putObjectRequest;
        try (InputStream stream = new FileInputStream(System.getProperty("user.dir") + File.separator + fileName)) {
            byte[] contents = stream.readAllBytes();
            putObjectRequest =
                    PutObjectRequest.builder()
                            .namespaceName(namespaceName)
                            .bucketName(bucketName)
                            .objectName(fileName)
                            .putObjectBody(new ByteArrayInputStream(contents))
                            .contentLength((long) contents.length)
                            .build();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error creating PutObjectRequest", e);
            response.status(Http.Status.INTERNAL_SERVER_ERROR_500).send();
            return;
        }
        PutObjectResponse putObjectResponse = objectStorageClient.putObject(putObjectRequest);

        response.status(Http.Status.OK_200).header("opc-request-id", putObjectResponse.getOpcRequestId());

        response.send();
    }

    /**
     * Delete a file from object storage.
     *
     * @param request  request
     * @param response response
     */
    public void delete(ServerRequest request, ServerResponse response) {
        String fileName = request.path().pathParameters().get("file-name");
        DeleteObjectResponse deleteObjectResponse = objectStorageClient.deleteObject(DeleteObjectRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(bucketName)
                .objectName(fileName)
                .build());
        response.status(Http.Status.OK_200).header("opc-request-id", deleteObjectResponse.getOpcRequestId());

        response.send();
    }
}
