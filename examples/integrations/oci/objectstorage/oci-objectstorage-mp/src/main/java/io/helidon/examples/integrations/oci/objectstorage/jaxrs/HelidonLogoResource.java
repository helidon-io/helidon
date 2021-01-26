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
package io.helidon.examples.integrations.oci.objectstorage.jaxrs;

import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A JAX-RS resource class rooted at {@code /logo}.
 *
 * @see #getLogo(String, String, String)
 */
@Path("/logo")
@ApplicationScoped
public class HelidonLogoResource {

    private final ObjectStorage client;

    private final String namespaceName;

    /**
     * Creates a new {@link HelidonLogoResource}.
     *
     * @param client an {@link ObjectStorage} client; must not be {@code
     * null}
     *
     * @param namespaceName the name of an OCI object storage namespace that will be used; must not be {@code null}
     *
     * @exception NullPointerException if either parameter is {@code
     * null}
     */
    @Inject
    public HelidonLogoResource(final ObjectStorage client,
            @ConfigProperty(name = "oci.objectstorage.namespace") final String namespaceName) {
        super();
        this.client = Objects.requireNonNull(client);
        this.namespaceName = Objects.requireNonNull(namespaceName);
    }

    /**
     * Returns a non-{@code null} {@link Response} which, if successful, will contain the object stored under the supplied {@code
     * namespaceName}, {@code bucketName} and {@code objectName}.
     *
     * @param namespaceName the OCI object storage namespace to use; must not be {@code null}
     *
     * @param bucketName the OCI object storage bucket name to use; must not be {@code null}
     *
     * @param objectName the OCI object storage object name to use; must not be {@code null}
     *
     * @return a non-{@code null} {@link Response} describing the operation
     *
     * @exception NullPointerException if any of the parameters is {@code null}
     */
    @GET
    @Path("/{namespaceName}/{bucketName}/{objectName}")
    @Produces(MediaType.WILDCARD)
    public Response getLogo(@PathParam("namespaceName") String namespaceName,
            @PathParam("bucketName") final String bucketName,
            @PathParam("objectName") final String objectName) {
        final Response returnValue;
        if (bucketName == null || bucketName.isEmpty() || objectName == null || objectName.isEmpty()) {
            returnValue = Response.status(400)
                    .build();
        } else {
            if (namespaceName == null || namespaceName.isEmpty()) {
                namespaceName = this.namespaceName;
            }
            Response temp = null;
            try {
                final GetObjectRequest request = GetObjectRequest.builder()
                        .namespaceName(namespaceName)
                        .bucketName(bucketName)
                        .objectName(objectName)
                        .build();
                assert request != null;
                final GetObjectResponse response = this.client.getObject(request);
                assert response != null;
                final Long contentLength = response.getContentLength();
                assert contentLength != null;
                if (contentLength <= 0L) {
                    temp = Response.noContent()
                            .build();
                } else {
                    temp = Response.ok()
                            .type(response.getContentType())
                            .entity(response.getInputStream())
                            .build();
                }
            } catch (final BmcException bmcException) {
                final int statusCode = bmcException.getStatusCode();
                temp = Response.status(statusCode)
                        .build();
            } finally {
                returnValue = temp;
            }
        }
        return returnValue;
    }

}
