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
package io.helidon.webserver.examples.mtls;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class CertificateHelper {

    private static final Pattern CN_PATTERN = Pattern.compile("(.*)CN=(.*?)(,.*)?");

    private CertificateHelper() {
    }

    static Optional<String> clientCertificateName(String name) {
        Matcher matcher = CN_PATTERN.matcher(name);
        if (matcher.matches()) {
            String cn = matcher.group(2);
            if (!cn.isBlank()) {
                return Optional.of(cn);
            }
        }
        return Optional.empty();
    }
}
