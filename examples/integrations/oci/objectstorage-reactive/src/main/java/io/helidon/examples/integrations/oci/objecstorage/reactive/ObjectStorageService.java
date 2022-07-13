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

package io.helidon.examples.integrations.oci.objecstorage.reactive;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import com.oracle.bmc.objectstorage.ObjectStorageAsync;
import com.oracle.bmc.objectstorage.model.RenameObjectDetails;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.requests.RenameObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetNamespaceResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import com.oracle.bmc.objectstorage.responses.RenameObjectResponse;
import com.oracle.bmc.responses.AsyncHandler;

class ObjectStorageService implements Service {
    private static final Logger LOGGER = Logger.getLogger(ObjectStorageService.class.getName());
    private final ObjectStorageAsync objectStorageAsyncClient;
    private final String bucketName;
    private final String namespaceName;

    ObjectStorageService(ObjectStorageAsync objectStorageAsyncClient, String bucketName) throws Exception {
        this.objectStorageAsyncClient = objectStorageAsyncClient;
        this.bucketName = bucketName;
        ResponseHandler<GetNamespaceRequest, GetNamespaceResponse> namespaceHandler =
                new ResponseHandler<>();
        this.objectStorageAsyncClient.getNamespace(GetNamespaceRequest.builder().build(), namespaceHandler);
        GetNamespaceResponse namespaceResponse = namespaceHandler.waitForCompletion();
        this.namespaceName = namespaceResponse.getValue();
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/file/{file-name}", this::download)
                .post("/file/{file-name}", this::upload)
                .delete("/file/{file-name}", this::delete)
                .get("/rename/{old-name}/{new-name}", this::rename);
    }

    private void delete(ServerRequest req, ServerResponse res) {
        String objectName = req.path().param("file-name");

        ResponseHandler<DeleteObjectRequest, DeleteObjectResponse> deleteObjectHandler =
                new ResponseHandler<>();

        objectStorageAsyncClient.deleteObject(DeleteObjectRequest.builder()
                                                      .namespaceName(namespaceName)
                                                      .bucketName(bucketName)
                                                      .objectName(objectName).build(), deleteObjectHandler);
        try {
            DeleteObjectResponse deleteObjectResponse = deleteObjectHandler.waitForCompletion();
            res.status(Http.Status.OK_200)
                    .send();
            return;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error deleting object", e);
            res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send();
            return;
        }
    }

    private void rename(ServerRequest req, ServerResponse res) {
        String oldName = req.path().param("old-name");
        String newName = req.path().param("new-name");

        RenameObjectRequest renameObjectRequest = RenameObjectRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(bucketName)
                .renameObjectDetails(RenameObjectDetails.builder()
                                             .newName(newName)
                                             .sourceName(oldName)
                                             .build())
                .build();

        ResponseHandler<RenameObjectRequest, RenameObjectResponse> renameObjectHandler =
                new ResponseHandler<>();

        try {
            objectStorageAsyncClient.renameObject(renameObjectRequest, renameObjectHandler);
            RenameObjectResponse renameObjectResponse = renameObjectHandler.waitForCompletion();
            res.status(Http.Status.OK_200)
                    .send();
            return;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error renaming object", e);
            res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send();
            return;
        }
    }

    private void upload(ServerRequest req, ServerResponse res) {
        String objectName = req.path().param("file-name");
        PutObjectRequest putObjectRequest = null;
        try (InputStream stream = new FileInputStream(System.getProperty("user.dir") + File.separator + objectName)) {
            byte[] contents = stream.readAllBytes();
            putObjectRequest =
                    PutObjectRequest.builder()
                            .namespaceName(namespaceName)
                            .bucketName(bucketName)
                            .objectName(objectName)
                            .putObjectBody(new ByteArrayInputStream(contents))
                            .contentLength(Long.valueOf(contents.length))
                            .build();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating PutObjectRequest", e);
            res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send();
            return;
        }

        ResponseHandler<PutObjectRequest, PutObjectResponse> putObjectHandler =
                new ResponseHandler<>();

        try {
            objectStorageAsyncClient.putObject(putObjectRequest, putObjectHandler);
            PutObjectResponse putObjectResponse = putObjectHandler.waitForCompletion();
            res.status(Http.Status.OK_200).send();
            return;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error uploading object", e);
            res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send();
            return;
        }
    }

    private void download(ServerRequest req, ServerResponse res) {
        String objectName = req.path().param("file-name");
        ResponseHandler<GetObjectRequest, GetObjectResponse> objectHandler =
                new ResponseHandler<>();
        GetObjectRequest getObjectRequest =
                GetObjectRequest.builder()
                        .namespaceName(namespaceName)
                        .bucketName(bucketName)
                        .objectName(objectName)
                        .build();
        GetObjectResponse getObjectResponse = null;
        try {
            objectStorageAsyncClient.getObject(getObjectRequest, objectHandler);
            getObjectResponse = objectHandler.waitForCompletion();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting object", e);
            res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send();
            return;
        }

        if (getObjectResponse.getContentLength() == 0) {
            LOGGER.log(Level.SEVERE, "GetObjectResponse is empty");
            res.status(Http.Status.NOT_FOUND_404).send();
            return;
        }

        try (InputStream fileStream = getObjectResponse.getInputStream()) {
            byte[] objectContent = fileStream.readAllBytes();
            res.addHeader(Http.Header.CONTENT_DISPOSITION, "attachment; filename=\"" + objectName + "\"")
                    .status(Http.Status.OK_200).send(objectContent);
            return;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing GetObjectResponse", e);
            res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send();
            return;
        }
    }

    private static class ResponseHandler<IN, OUT> implements AsyncHandler<IN, OUT> {
        private OUT item;
        private Throwable failed = null;
        private CountDownLatch latch = new CountDownLatch(1);

        private OUT waitForCompletion() throws Exception {
            latch.await();
            if (failed != null) {
                if (failed instanceof Exception) {
                    throw (Exception) failed;
                }
                throw (Error) failed;
            }
            return item;
        }

        @Override
        public void onSuccess(IN request, OUT response) {
            item = response;
            latch.countDown();
        }

        @Override
        public void onError(IN request, Throwable error) {
            failed = error;
            latch.countDown();
        }
    }
}
