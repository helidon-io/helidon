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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import io.helidon.common.reactive.Multi;
import io.helidon.config.Config;

import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;

/**
 * JAXB persistable Lra registry.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class LraJaxbPersistentRegistry implements LraPersistentRegistry {

    private static final Logger LOGGER = Logger.getLogger(LraJaxbPersistentRegistry.class.getName());

    @XmlTransient
    private java.nio.file.Path registryDirPath;
    @XmlTransient
    private Config config;

    private final Map<String, Lra> lraMap = Collections.synchronizedMap(new HashMap<>());

    /**
     * @param config
     */
    public LraJaxbPersistentRegistry(Config config) {
        this.config = config;
        registryDirPath = Paths.get(this.config.get("mp.lra.coordinator.registry")
                .asString()
                .orElse("mock-coordinator/lra-registry"));
    }

    LraJaxbPersistentRegistry() {
    }

    /**
     * Get Lra by id.
     *
     * @param lraId to look for
     * @return lra if exist
     */
    public Lra get(String lraId) {
        return lraMap.get(lraId);
    }

    /**
     * Add new Lra.
     *
     * @param lraId id of new lra
     * @param lra   Lra
     */
    public void put(String lraId, Lra lra) {
        lraMap.put(lraId, lra);
    }

    /**
     * Remove lra by id.
     *
     * @param lraId of the Lra to be removed
     */
    public void remove(String lraId) {
        lraMap.remove(lraId);
    }

    /**
     * Stream of all Lras.
     *
     * @return stream of all the Lras
     */
    public Multi<Lra> stream() {
        return Multi.create(new HashSet<>(lraMap.values()));
    }

    /**
     * Load persisted Lras.
     */
    public void load() {
        try {
            if (Files.exists(registryDirPath)) {
                JAXBContext context = JAXBContext.newInstance(
                        LraJaxbPersistentRegistry.class,
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
                LraJaxbPersistentRegistry lraPersistentRegistry =
                        (LraJaxbPersistentRegistry) unmarshaller.unmarshal(registryDirPath.toFile());
                lraMap.putAll(lraPersistentRegistry.lraMap);
            }
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Error when loading persisted coordinator registry.", e);
        }
    }

    /**
     * Persist Lras.
     */
    public void save() {
        try {
            JAXBContext context = JAXBContext.newInstance(
                    LraJaxbPersistentRegistry.class,
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
            mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            if (!Files.exists(registryDirPath.getParent())) Files.createDirectories(registryDirPath.getParent());
            Files.deleteIfExists(registryDirPath);
            Files.createFile(registryDirPath);
            LraJaxbPersistentRegistry lraPersistentRegistry = new LraJaxbPersistentRegistry();
            lraPersistentRegistry.lraMap.putAll(lraMap);
            mar.marshal(lraPersistentRegistry, registryDirPath.toFile());
        } catch (JAXBException | IOException e) {
            LOGGER.log(Level.SEVERE, "Error when persisting coordinator registry.", e);
        }
    }
}
