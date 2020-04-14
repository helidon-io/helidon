# BookApplication

1. Run `mvn clean install` to build your application
2. Start application with `java -jar target/bookstore-mp.jar`. Set system property `bookstore.size` to a value 
greater than 0 to initialize book store.
3. To check that your application is running enter url `http://localhost:8080/books`

# JPMS

You can run using modules like this:

`java --module-path target/bookstore-mp.jar:target/libs -m io.helidon.tests.apps.bookstore.mp/io.helidon.tests.apps.bookstore.mp.Main`
