/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

/**
 * Module supporting encryption of secrets in configuration files.
 */
module io.helidon.config.encryption {
    requires java.logging;

    // for RSA encrypted keys
    requires transitive io.helidon.common.pki;
    requires transitive io.helidon.config;

    exports io.helidon.config.encryption;

    provides io.helidon.config.spi.ConfigFilter with io.helidon.config.encryption.EncryptionFilterService;
}
