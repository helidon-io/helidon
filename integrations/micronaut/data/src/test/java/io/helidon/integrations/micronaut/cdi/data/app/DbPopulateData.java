package io.helidon.integrations.micronaut.cdi.data.app;

import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.runtime.event.annotation.EventListener;

@Singleton
@TypeHint(typeNames = {"org.h2.Driver", "org.h2.mvstore.db.MVTableEngine"})
public class DbPopulateData {
    private final DbOwnerRepository ownerRepository;
    private final DbPetRepository petRepository;

    @Inject
    DbPopulateData(DbOwnerRepository ownerRepository, DbPetRepository petRepository) {
        this.ownerRepository = ownerRepository;
        this.petRepository = petRepository;
    }

    @EventListener
    void init(StartupEvent event) {
        Owner fred = new Owner("Fred");
        fred.setAge(45);
        Owner barney = new Owner("Barney");
        barney.setAge(40);
        ownerRepository.saveAll(Arrays.asList(fred, barney));

        Pet dino = new Pet("Dino", fred);
        Pet bp = new Pet("Baby Puss", fred);
        bp.setType(Pet.PetType.CAT);
        Pet hoppy = new Pet("Hoppy", barney);

        petRepository.saveAll(Arrays.asList(dino, bp, hoppy));
    }
}
