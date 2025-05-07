# Helidon Data MP Tests

Tests to validate Helidon Data and MP JPA modules interoperability

* Works with shared JTA (Narayana)
  * DataSource from Helidon Data config (application.xml) can't be used in JPA CDI support modules
* Tested both positive and negative JTA scenarios
  * both inner transaction are passing
  * one of the inner transactions throws an exception and is rolled back

* There are some issues with persistence unit initialization with Hibernate
  * database got erased after persistence context init with `jakarta.persistence.schema-generation.database.action` set to `none`,
    but that's not an issue on our side

