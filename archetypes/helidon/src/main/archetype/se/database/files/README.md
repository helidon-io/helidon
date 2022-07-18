```
curl -X GET http://localhost:8080/pokemon
[{"id":1,"idType":12,"name":"Bulbasaur"}, ...]

curl -X GET http://localhost:8080/type
[{"id":1,"name":"Normal"}, ...]

curl -H "Content-Type: application/json" --request POST --data '{"id":100, "idType":1, "name":"Test"}' http://localhost:8080/pokemon
```

