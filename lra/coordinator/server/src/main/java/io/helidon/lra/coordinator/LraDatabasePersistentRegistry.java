/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.lra.coordinator;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbTransaction;

import org.eclipse.microprofile.lra.annotation.LRAStatus;

class LraDatabasePersistentRegistry implements LraPersistentRegistry {

    private final Map<String, Lra> lraMap = Collections.synchronizedMap(new HashMap<>());
    private final Config config;
    private final DbClient dbClient;
    private static final Pattern LRA_ID_PATTERN = Pattern.compile(".*/([^/?]+).*");

    LraDatabasePersistentRegistry(Config config) {
        this.config = config;
        this.dbClient = DbClient.builder()
                .config(config.get("db"))
                .build();

        DbTransaction tx = dbClient.transaction();
        tx.namedDml("create-lra-table");
        tx.namedDml("create-participant-table");
        tx.commit();
    }

    @Override
    public Lra get(String lraId) {
        return lraMap.get(lraId);
    }

    @Override
    public void put(String key, Lra lra) {
        lraMap.put(key, lra);
    }

    @Override
    public void remove(String key) {
        lraMap.remove(key);
    }

    @Override
    public Stream<Lra> stream() {
        return lraMap.values().stream();
    }

    @Override
    public void load(CoordinatorService coordinatorService) {
        DbExecute tx = dbClient.execute();
        tx.namedQuery("load").forEach(row -> {
            String lraId = row.column("ID").get(String.class);
            String parentId = row.column("PARENT_ID").get(String.class);
            Long timeout = row.column("TIMEOUT").get(Long.class);
            String lraStatus = row.column("STATUS").get(String.class);
            Boolean isChild = row.column("IS_CHILD").get(Boolean.class);
            Long whenReadyToDelete = row.column("WHEN_READY_TO_DELETE").get(Long.class);

            String completeLink = row.column("COMPLETE_LINK").get(String.class);
            String compensateLink = row.column("COMPENSATE_LINK").get(String.class);
            String afterLink = row.column("AFTER_LINK").get(String.class);
            String forgetLink = row.column("FORGET_LINK").get(String.class);
            String statusLink = row.column("STATUS_LINK").get(String.class);
            String participantStatus = row.column("PARTICIPANT_STATUS").get(String.class);
            String compensateStatus = row.column("COMPENSATE_STATUS").get(String.class);
            String forgetStatus = row.column("FORGET_STATUS").get(String.class);
            String afterStatus = row.column("AFTER_LRA_STATUS").get(String.class);
            String sendingStatus = row.column("SENDING_STATUS").get(String.class);

            Integer remainingCloseAttempts = row.column("REMAINING_CLOSE_ATTEMPTS").get(Integer.class);
            Integer remainingAfterAttempts = row.column("REMAINING_AFTER_ATTEMPTS").get(Integer.class);
            Lra lra = lraMap.get(lraId);
            if (lra == null) {
                lra = new Lra(coordinatorService, lraId,
                        Optional.ofNullable(parentId).map(URI::create).orElse(null),
                        config);
                lra.setTimeout(timeout);
                lra.setStatus(LRAStatus.valueOf(lraStatus));
                lra.setChild(isChild);
                lra.setWhenReadyToDelete(whenReadyToDelete);
            }

            if (participantStatus != null) {
                Participant participant = new Participant(config);
                participant.setCompleteURI(Optional.ofNullable(completeLink).map(URI::create).orElse(null));
                participant.setCompensateURI(Optional.ofNullable(compensateLink).map(URI::create).orElse(null));
                participant.setAfterURI(Optional.ofNullable(afterLink).map(URI::create).orElse(null));
                participant.setForgetURI(Optional.ofNullable(forgetLink).map(URI::create).orElse(null));
                participant.setStatusURI(Optional.ofNullable(statusLink).map(URI::create).orElse(null));
                participant.setStatus(Participant.Status.valueOf(participantStatus));
                participant.setCompensateStatus(Participant.CompensateStatus.valueOf(compensateStatus));
                participant.setForgetStatus(Participant.ForgetStatus.valueOf(forgetStatus));
                participant.setAfterLraStatus(Participant.AfterLraStatus.valueOf(afterStatus));
                participant.setSendingStatus(Participant.SendingStatus.valueOf(sendingStatus));
                participant.setRemainingCloseAttempts(remainingCloseAttempts);
                participant.setRemainingAfterAttempts(remainingAfterAttempts);

                List<Participant> participants = lra.getParticipants();
                participants.add(participant);
            }
            lraMap.put(lraId, lra);
        });
        lraMap.values()
                .forEach(lra -> Optional.ofNullable(lra.parentId())
                        .ifPresent(parentId -> {
                            var parentLra = lraMap.get(parseLRAId(parentId));
                            if (parentLra != null) {
                                parentLra.addChild(lra);
                            }
                        }));
    }

    @Override
    public void save() {
        DbTransaction tx = dbClient.transaction();
        cleanUp(tx);
        saveAll(tx);
        tx.commit();
    }

    private void saveAll(DbTransaction tx) {
        lraMap.values().forEach(lra -> insertLra(tx, lra));
    }

    private void insertLra(DbTransaction tx, Lra lra) {
        tx.namedInsert("insert-lra",
                lra.lraId(),
                lra.parentId(),
                lra.getTimeout(),
                lra.status().get().name(),
                lra.isChild(),
                lra.getWhenReadyToDelete());

        // save all participants of the lra
        lra.getParticipants().forEach(participant -> insertParticipant(tx, lra, participant));
    }

    private void insertParticipant(DbTransaction tx, Lra lra, Participant p) {
        tx.namedInsert("insert-participant",
                lra.lraId(),
                p.state().name(),
                p.getCompensateStatus().name(),
                p.getForgetStatus().name(),
                p.getAfterLraStatus().name(),
                p.getSendingStatus().name(),
                p.getRemainingCloseAttempts(),
                p.getRemainingAfterAttempts(),
                p.getCompleteURI().map(URI::toASCIIString).orElse(null),
                p.getCompensateURI().map(URI::toASCIIString).orElse(null),
                p.getAfterURI().map(URI::toASCIIString).orElse(null),
                p.getForgetURI().map(URI::toASCIIString).orElse(null),
                p.getStatusURI().map(URI::toASCIIString).orElse(null));
    }

    private void cleanUp(DbTransaction tx) {
        tx.namedDelete("delete-all-lra");
        tx.namedDelete("delete-all-participants");
    }

    static String parseLRAId(String lraUri) {
        Matcher m = LRA_ID_PATTERN.matcher(lraUri);
        if (!m.matches()) {
            //LRA uri format
            throw new RuntimeException("Error when parsing lraUri: " + lraUri);
        }
        return m.group(1);
    }

}
