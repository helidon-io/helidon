# BookApplication

1. Run `mvn clean install` to build your application
2. Start application with `java -Dbookstore.size=10 -jar target/bookstore-nima.jar`.
3. To check that your application is running enter url `http://localhost:8080/books`

# JPMS

You can run using modules like this:

`java --module-path target/bookstore-nima.jar:target/libs -m io.helidon.tests.apps.bookstore.nima/io.helidon.tests.apps.bookstore.nima.Main`
