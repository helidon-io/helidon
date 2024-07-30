/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.se.integrations;

import java.io.IOException;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.ConfigFileReader.ConfigFile;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageAsync;
import com.oracle.bmc.objectstorage.ObjectStorageAsyncClient;

@SuppressWarnings("ALL")
class OciSnippets {

    void snippet_1() throws IOException {
        // tag::snippet_1[]
        ConfigFile config = ConfigFileReader.parse("~/.oci/config", "DEFAULT");
        AuthenticationDetailsProvider authProvider = new ConfigFileAuthenticationDetailsProvider(config);
        // end::snippet_1[]
    }

    void snippet_2() throws IOException {
        // tag::snippet_2[]
        ConfigFile config = ConfigFileReader.parse("~/.oci/config", "DEFAULT");
        AuthenticationDetailsProvider authProvider = new ConfigFileAuthenticationDetailsProvider(config);
        ObjectStorageAsync objectStorageAsyncClient = new ObjectStorageAsyncClient(authProvider);
        // end::snippet_2[]
    }
}
