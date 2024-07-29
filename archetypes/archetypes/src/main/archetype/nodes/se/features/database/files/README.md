Database:
```
# List all Pokémon
curl http://localhost:8080/db/pokemon

# List all Pokémon types
curl http://localhost:8080/db/type

# Get a single Pokémon by id
curl http://localhost:8080/db/pokemon/2

# Get a single Pokémon by name
curl http://localhost:8080/db/pokemon/name/Squirtle

# Add a new Pokémon Rattata
curl -i -X POST -d '{"id":7,"name":"Rattata","idType":1}' http://localhost:8080/db/pokemon

# Rename Pokémon with id 7 to Raticate
curl -i -X PUT -d '{"id":7,"name":"Raticate","idType":2}' http://localhost:8080/db/pokemon

# Delete Pokémon with id 7
curl -i -X DELETE http://localhost:8080/db/pokemon/7
```
