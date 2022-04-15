# JPA Pokemons Example

With Java:
```bash
mvn package
java -jar target/helidon-integrations-examples-pokemons-jpa.jar
```

## Exercise the application

```
curl -X GET http://localhost:8080/pokemon
[{"id":1,"type":12,"name":"Bulbasaur"}, ...]
curl -X GET http://localhost:8080/type
[{"id":1,"name":"Normal"}, ...]
curl -H "Content-Type: application/json" --request POST --data '{"id":100, "type":1, "name":"Test"}' http://localhost:8080/pokemon
```

---

Pokémon, and Pokémon character names are trademarks of Nintendo.
