This module contains all the API and SPI types that are applicable to a Helidon Pico based application.

The API can logically be broken up into two categories - declarative types and imperative/programmatic types. The declarative form is the most common approach for using Pico.

The declarative API is small and based upon annotations. This is because most of the supporting annotation types actually come directly from both of the standard javax/jakarta inject and javax/jakarta annotation modules. These standard annotations are supplemented with these proprietary annotation types offered here from Pico:

* [@Contract](src/main/java/io/helidon/pico/Contract.java)
* [@ExteralContracts](src/main/java/io/helidon/pico/ExternalContracts.java)
* [@RunLevel](src/main/java/io/helidon/pico/RunLevel.java)

The programmatic API is typically used to manually lookup and activate services (those that are typically annotated with <i>@jakarta.inject.Singleton</i> for example) directly. The main entry points for programmatic access can start from one of these two types:

* [PicoServices](src/main/java/io/helidon/pico/PicoServices.java)
* [Services](src/main/java/io/helidon/pico/Services.java)

Note that this module only contains the common types for a Helidon Pico services provider. See the <b>pico-services</b> module for the default reference implementation for this API / SPI.
