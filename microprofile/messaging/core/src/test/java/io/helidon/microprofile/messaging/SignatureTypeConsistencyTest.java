/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.messaging;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SignatureTypeConsistencyTest {

    /**
     * To keep consistency between {@link io.helidon.microprofile.messaging.MethodSignatureType}
     * and {@link io.helidon.microprofile.messaging.MethodSignatureResolver}
     */
    @Test
    void isTypeUsedByResolver() throws IOException {
        String srcFileName = String.format("%s.java", MethodSignatureResolver.class.getName().replaceAll("\\.", "\\" + File.separator));
        Path resolverSrcPath = Paths.get("src", "main", "java", srcFileName);
        String resolverSrc = new String(Files.readAllBytes(resolverSrcPath));

        Arrays.stream(MethodSignatureType.values())
                .map(signatureType -> MethodSignatureType.class.getSimpleName() + "." + signatureType.name())
                .filter(token -> !resolverSrc.contains(token))
                .map(token -> String.format("Unused signature type, token %s not found in file %s", token, resolverSrcPath))
                .forEach(Assertions::fail);
    }
}
