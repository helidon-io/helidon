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

package io.helidon.examples.integrations.vault.hcp.reactive;

final class VaultPolicy {
    static final String POLICY = "# Enable and manage authentication methods\n"
            + "path \"auth/*\"\n"
            + "{\n"
            + "  capabilities = [\"create\", \"update\", \"delete\", \"sudo\"]\n"
            + "}\n"
            + "\n"
            + "# Create, update, and delete auth methods\n"
            + "path \"sys/auth/*\"\n"
            + "{\n"
            + "  capabilities = [\"create\", \"update\", \"delete\", \"sudo\"]\n"
            + "}\n"
            + "\n"
            + "# List auth methods\n"
            + "path \"sys/auth\"\n"
            + "{\n"
            + "  capabilities = [\"read\"]\n"
            + "}\n"
            + "\n"
            + "# Enable and manage the key/value secrets engine at `secret/` path\n"
            + "\n"
            + "# List, create, update, and delete key/value secrets\n"
            + "path \"secret/*\"\n"
            + "{\n"
            + "  capabilities = [\"create\", \"read\", \"update\", \"delete\", \"list\", \"sudo\"]\n"
            + "}\n"
            + "\n"
            + "path \"kv1/*\"\n"
            + "{\n"
            + "  capabilities = [\"create\", \"read\", \"update\", \"delete\", \"list\", \"sudo\"]\n"
            + "}\n"
            + "\n"
            + "path \"cubbyhole/*\"\n"
            + "{\n"
            + "  capabilities = [\"create\", \"read\", \"update\", \"delete\", \"list\", \"sudo\"]\n"
            + "}\n"
            + "\n"
            + "path \"database/*\"\n"
            + "{\n"
            + "  capabilities = [\"create\", \"read\", \"update\", \"delete\", \"list\", \"sudo\"]\n"
            + "}\n"
            + "\n"
            + "path \"kv/*\"\n"
            + "{\n"
            + "  capabilities = [\"create\", \"read\", \"update\", \"delete\", \"list\", \"sudo\"]\n"
            + "}\n"
            + "\n"
            + "# Manage secrets engines\n"
            + "path \"sys/mounts/*\"\n"
            + "{\n"
            + "  capabilities = [\"create\", \"read\", \"update\", \"delete\", \"list\", \"sudo\"]\n"
            + "}\n"
            + "\n"
            + "# List existing secrets engines.\n"
            + "path \"sys/mounts\"\n"
            + "{\n"
            + "  capabilities = [\"read\"]\n"
            + "}\n";
}
