/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.microprofile.oci.tls.certificates;

import java.security.Security;

import io.helidon.logging.common.LogConfig;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class Main {

    // TODO: is it ok to have a BC dependency in our examples?
    public static void main(String args[]) {
        // OCI uses BC, we need it for decryptAesKey
        // https://stackoverflow.com/a/23859386/626826
        // https://bugs.openjdk.org/browse/JDK-7038158
        Security.addProvider(new BouncyCastleProvider());

        LogConfig.configureRuntime();

    }

}
