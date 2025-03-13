/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.tests.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.data.Data;
import io.helidon.data.tests.model.Trainer;

import jakarta.persistence.EntityManager;

@Data.Repository
public interface TrainerRepository
        extends Data.CrudRepository<Trainer, Integer>,
                Data.SessionRepository<EntityManager> {

    @Data.Query("SELECT * FROM Keeper k")
    Stream<Trainer> selectAll();

    @Data.Query("SELECT k FROM Keeper k WHERE k.team.name = :name")
    List<Trainer> selectKeeperByTeamName(String name);

    @Data.Query("SELECT k FROM Keeper k WHERE k.team.id = $1")
    Collection<Trainer> selectKeeperByTeamId(int id);

    @Data.Query("SELECT k FROM Keeper k WHERE k.name = $2 AND k.id = $1")
    Optional<Trainer> selectKeeperByNameAndId(int id, String name);

    @Data.Query("SELECT k FROM Keeper k WHERE k.id = :id")
    Trainer getKeeperById(int id);

}
