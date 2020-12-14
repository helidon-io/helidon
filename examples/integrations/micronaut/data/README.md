# Helidon Micronaut Data Example

This example shows integration with Micronaut Data into Helidon MP.

## Sources

This example combines Micronaut Data and CDI.

### CDI classes

The following classes are CDI and JAX-RS classes that use injected Micronaut beans:

- `PetResource` - JAX-RS resource exposing Pet REST API
- `OwnerResource` - JAX-RS resource exposing Owner REST API
- `BeanValidationExceptionMapper` - JAX-RS exception mapper to return correct status code
        in case of validation failure

### Micronaut classes

The following classes are pure Micronaut beans (and cannot have CDI injected into them)

- `DbPetRepository` - Micronaut Data repository extending an abstract class
- `DbOwnerRepository` - Micronaut Data repository implementing an interface
- `DbPopulateData` - Micronaut startup event listener to initialize the database
- package `model` - data model of the database

## Build and run

Start the application:

```bash
mvn package
java -jar target/helidon-examples-integrations-micronaut-data.jar
```

Access endpoints

```bash
# Get all pets
curl -i http://localhost:8080/pets
# Get all owners
curl -i http://localhost:8080/owners
# Get a single pet
curl -i http://localhost:8080/pets/Dino
# Get a single owner
curl -i http://localhost:8080/owners/Barney
# To fail input validation
curl -i http://localhost:8080/pets/s
```