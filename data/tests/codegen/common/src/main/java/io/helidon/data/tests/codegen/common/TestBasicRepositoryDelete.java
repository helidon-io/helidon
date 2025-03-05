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
package io.helidon.data.tests.codegen.common;

import java.util.List;

import io.helidon.data.api.DataRegistry;
import io.helidon.data.tests.codegen.model.League;
import io.helidon.data.tests.codegen.repository.LeagueRepository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.data.tests.codegen.common.InitialData.LEAGUES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestBasicRepositoryDelete {

    private static LeagueRepository leagueRepository;

    // Delete League with specific ID and verify that it does no more exist in the database
    @Test
    public void testDeleteById() {
        League league = LEAGUES[1];
        assertThat(leagueRepository.existsById(league.getId()), is(true));
        long result = leagueRepository.deleteById(league.getId());
        assertThat(result, is(1L));
        boolean exists = leagueRepository.existsById(league.getId());
        assertThat(exists, is(false));
    }

    // Delete specific League instance and verify that it does no more exist in the database
    @Test
    public void testDeleteByInstance() {
        League league = LEAGUES[2];
        assertThat(leagueRepository.existsById(league.getId()), is(true));
        leagueRepository.delete(league);
        boolean exists = leagueRepository.existsById(league.getId());
        assertThat(exists, is(false));
    }

    // Delete specific League instances and verify that they do no more exist in the database
    @Test
    public void testDeleteAllEntities() {
        List<League> leagues = List.of(LEAGUES[3], LEAGUES[4], LEAGUES[5]);
        // Verify that League instances exist before delete
        long countBefore = leagueRepository.count();
        for (League league : leagues) {
            assertThat(leagueRepository.existsById(league.getId()), is(true));
        }
        leagueRepository.deleteAll(leagues);
        // Verify that League instances do not exist after delete
        long countAfter = leagueRepository.count();
        for (League league : leagues) {
            assertThat(leagueRepository.existsById(league.getId()), is(false));
        }
        // Verify that count of League instances before and after delete differs by deleted List size
        assertThat(countBefore, is(countAfter + leagues.size()));
    }

    // Delete all League instances and verify that table is empty
    @Test
    public void testDeleteAll() {
        long countBefore = leagueRepository.count();
        assertThat(countBefore, is((long) (LEAGUES.length - 1)));
        leagueRepository.deleteAll();
        // Verify that all League instances were deleted
        long countAfter = leagueRepository.count();
        for (int i = 1; i < LEAGUES.length; i++) {
            boolean exists = leagueRepository.existsById(LEAGUES[i].getId());
            assertThat(exists, is(false));
        }
        assertThat(countAfter, is(0L));
    }

    // Restore League instances before each test
    @BeforeEach
    public void beforeEach() {
        leagueRepository.deleteAll();
        for (int i = 1; i < LEAGUES.length; i++) {
            leagueRepository.save(LEAGUES[i]);
        }
    }

    @BeforeAll
    public static void before(DataRegistry data) {
        leagueRepository = data.repository(LeagueRepository.class);
    }

    @AfterAll
    public static void after() {
        // League instances are used for delete tests, so they must be restored
        leagueRepository.deleteAll();
        for (int i = 1; i < LEAGUES.length; i++) {
            leagueRepository.save(LEAGUES[i]);
        }
    }

}
