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

import java.lang.reflect.Type;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.integrations.oci.connect.OciRestApi;
import io.helidon.integrations.oci.connect.spi.InjectionProvider;

public class OciBlockingObjectStorageInjectionProvider implements InjectionProvider<OciObjectStorage> {
    @Override
    public Set<Type> types() {
        return Set.of(OciObjectStorage.class);
    }

    @Override
    public OciObjectStorage createInstance(OciRestApi restApi, Config ociConfig) {
        return BlockingOciObjectStorageImpl.create(restApi, ociConfig);
    }
}
