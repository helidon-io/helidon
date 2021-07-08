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
 *
 */

package io.helidon.microprofile.lra.coordinator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.ws.rs.core.Link;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
class LraPersistentRegistry {

    private static final Logger LOGGER = Logger.getLogger(LraPersistentRegistry.class.getName());

    private static final java.nio.file.Path REGISTRY_DIR_PATH = Paths.get("target/mock-coordinator/lra-registry");

    private final Map<String, Lra> lraMap = new HashMap<>();

    synchronized Lra get(String lraId) {
        return lraMap.get(lraId);
    }

    synchronized void put(String key, Lra lra) {
        lraMap.put(key, lra);
    }

    int size() {
        return lraMap.size();
    }

    synchronized void remove(String key) {
        lraMap.remove(key);
    }

    synchronized Stream<Lra> stream() {
        return new HashSet<>(lraMap.values()).stream();
    }

    synchronized void load() {
        try {
            if (Files.exists(REGISTRY_DIR_PATH)) {
                JAXBContext context = JAXBContext.newInstance(
                        LraPersistentRegistry.class,
                        Lra.class,
                        Participant.class,
                        LRAStatus.class,
                        Participant.CompensateStatus.class,
                        Participant.ForgetStatus.class,
                        Participant.AfterLraStatus.class,
                        Participant.SendingStatus.class,
                        Participant.Status.class,
                        ParticipantStatus.class);
                Unmarshaller unmarshaller = context.createUnmarshaller();
                unmarshaller.setAdapter(new Link.JaxbAdapter());
                LraPersistentRegistry lraPersistentRegistry =
                        (LraPersistentRegistry) unmarshaller.unmarshal(REGISTRY_DIR_PATH.toFile());
                lraMap.putAll(lraPersistentRegistry.lraMap);
            }
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Error when loading persisted coordinator registry.");
        }
    }

    synchronized void save() {
        try {
            JAXBContext context = JAXBContext.newInstance(
                    LraPersistentRegistry.class,
                    Lra.class,
                    Participant.class,
                    LRAStatus.class,
                    Participant.CompensateStatus.class,
                    Participant.ForgetStatus.class,
                    Participant.AfterLraStatus.class,
                    Participant.SendingStatus.class,
                    Participant.Status.class,
                    ParticipantStatus.class);
            Marshaller mar = context.createMarshaller();
            mar.setAdapter(new Link.JaxbAdapter());
            mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            if (!Files.exists(REGISTRY_DIR_PATH.getParent())) Files.createDirectories(REGISTRY_DIR_PATH.getParent());
            Files.deleteIfExists(REGISTRY_DIR_PATH);
            Files.createFile(REGISTRY_DIR_PATH);
            LraPersistentRegistry lraPersistentRegistry = new LraPersistentRegistry();
            lraPersistentRegistry.lraMap.putAll(lraMap);
            mar.marshal(lraPersistentRegistry, REGISTRY_DIR_PATH.toFile());
        } catch (JAXBException | IOException e) {
            LOGGER.log(Level.SEVERE, "Error when persisting coordinator registry.");
        }
    }
}
