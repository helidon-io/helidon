# Functional Tests

Functional tests for Helidon. Based on a simple CRUD service
that models a book store.

# REST API

|Verb|Path|Description
|----|----|-----------|
|GET|/books|Returns list of books|
|POST|/books|Adds a new book|
|GET|/books/{isbn}|Returns book with the given isbn number|
|PUT|/books/{isbn}|Updates book with the given isbn number|
|DELETE|/books/{isbn}|Deletes book with the given isbn number|

# Sample JSON

See `helidon-mp/src/test/book.json`

# Example Curl Commands

```bash
curl -H "Content-Type: application/json" \
 -X POST http://localhost:8080/books \
 --data @helidon-mp/target/test-classes/book.json 
 
curl -H 'Accept: application/json' -X GET http://localhost:8080/books

curl -H 'Accept: application/json' -X GET http://localhost:8080/books/123456

curl -H "Content-Type: application/json" \
 -X PUT http://localhost:8080/books/1234 \
 --data @helidon-mp/target/test-classes/book.json 
 
curl -X DELETE http://localhost:8080/books/123456
```
