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

package io.helidon.integrations.oci.objectstorage.blocking;

import java.nio.channels.ReadableByteChannel;

import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.objectstorage.DeleteObject;
import io.helidon.integrations.oci.objectstorage.GetObject;
import io.helidon.integrations.oci.objectstorage.PutObject;
import io.helidon.integrations.oci.objectstorage.RenameObject;

/**
 * Blocking OCI Vault API.
 */
public interface OciObjectStorage {
    /**
     * Gets the metadata and body of an object.
     *
     * @param request get object request
     * @return future with response or error
     */
    ApiOptionalResponse<BlockingGetObject.Response> getObject(GetObject.Request request);

    /**
     * Creates a new object or overwrites an existing object with the same name. The maximum object size allowed by PutObject
     * is 50 GiB.
     *
     * @param request put object request
     * @param channel to read data from
     * @return future with response or error
     */
    PutObject.Response putObject(PutObject.Request request, ReadableByteChannel channel);

    /**
     * Deletes an object.
     * @param request delete object request
     * @return future with response or error
     */
    DeleteObject.Response deleteObject(DeleteObject.Request request);

    /**
     * Rename an object in the given Object Storage namespace.
     * See <a href="https://docs.oracle.com/iaas/Content/Object/Tasks/managingobjects.htm#namerequirements">Object Names</a>.
     *
     * @param request rename object request
     * @return future with response or error
     */
    RenameObject.Response renameObject(RenameObject.Request request);
}
