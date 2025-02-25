```shell
curl -X POST -H "Content-Type: application/json" \
 -d '{"ssn" : "123-45-6789", "firstName" : "Frank", "lastName" : "Helidon", "dateOfBirth" : "02/14/2019"}' \
  http://localhost:8080/creditscore
```

You'll notice a short delay as the application computes the credit score.
Now repeat the same request. You'll see the score is returned instantly as it is retrieved from the cache.
