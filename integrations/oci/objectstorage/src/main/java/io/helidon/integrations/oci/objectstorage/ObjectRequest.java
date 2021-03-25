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

package io.helidon.integrations.oci.objectstorage;

import java.util.Optional;

import io.helidon.integrations.oci.connect.OciApiException;
import io.helidon.integrations.oci.connect.OciRequestBase;

/**
 * Object store base request class.
 *
 * @param <T> type of the subclass
 */
class ObjectRequest<T extends ObjectRequest<T>> extends OciRequestBase<T> {
    private String namespace;
    private String bucket;
    private String objectName;

    /**
     * The Object Storage namespace used for the request.
     * Override the default namespace (if one is configured).
     *
     * @param namespace namespace
     * @return updated request
     */
    public T namespace(String namespace) {
        this.namespace = namespace;
        return me();
    }

    /**
     * The name of the bucket. Avoid entering confidential information.
     * Required.
     *
     * @param bucket bucket name
     * @return updated requst
     */
    public T bucket(String bucket) {
        this.bucket = bucket;
        return me();
    }

    /**
     * The name of the object. Avoid entering confidential information.
     * Required.
     *
     * @param objectName name of the object
     * @return updated request
     */
    public T objectName(String objectName) {
        this.objectName = objectName;
        return me();
    }

    /**
     * Namespace if configured on this request.
     *
     * @return namespace or empty if not configured per request
     */
    public Optional<String> namespace() {
        return Optional.ofNullable(namespace);
    }

    /**
     * Name of the bucket, required.
     *
     * @return bucket name
     */
    public String bucket() {
        if (bucket == null) {
            throw new OciApiException("Bucket name must be defined for PutObject request.");
        }
        return bucket;
    }

    /**
     * Object name, required.
     *
     * @return object name
     */
    public String objectName() {
        if (objectName == null) {
            throw new OciApiException("Object name must be defined for PutObject request.");
        }
        return objectName;
    }
}
