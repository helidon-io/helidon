/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.data.sql.testing;

import org.testcontainers.utility.DockerImageName;

/**
 * Docker images used by SQL test containers.
 */
public final class SqlTestContainerImages {

    private static final DockerImageName MYSQL = DockerImageName.parse(
            "container-registry.oracle.com/mysql/community-server:9.7.3")
            .asCompatibleSubstituteFor("mysql");

    private static final DockerImageName ORACLE = DockerImageName.parse(
            "container-registry.oracle.com/database/free:23.26.3.0-lite");

    private SqlTestContainerImages() {
    }

    /**
     * MySQL Docker image.
     *
     * @return MySQL Docker image
     */
    public static DockerImageName mySqlImageReference() {
        return MYSQL;
    }

    /**
     * Oracle Database Docker image.
     *
     * @return Oracle Database Docker image
     */
    public static DockerImageName oracleImageReference() {
        return ORACLE;
    }
}
