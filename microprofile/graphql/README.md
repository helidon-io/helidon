# Helidon Microprofile GraphQL

This is a temporary development README

## Building

From Helidon root.

```bash
mvn clean install -DskipTests
```               

## Run GraphiQL against the TCK codebase

```bash
cd microprofile/graphql/runner

nvn exec:exec -P runner
```                    

Access the UI via http://127.0.0.1:7001/ui/

Click on `Docs` link on top right for schema.

Paste the following in and then click on the `Run` button and choose the operation.

```bash
query allHeros  {
  allHeroes {
  idNumber
    name
    bankBalance
    birthday
    dateOfLastCheckin
    
  }
}

query allTeams
{
  allTeams
  {
    ...teamFields
  }
}

mutation addStarLordToXMenTeam
{
addHeroToTeam(hero: "Starlord" team: "X-Men")
  {
    ...teamFields
  }
}

mutation updateBankBalance
{
  updateBankBalance(name:"Iron Man", bankBalance:100) {
    name
    bankBalance
  }
}

fragment teamFields on Team {
  name
  dailyStandupMeeting
  members
  {
    name
  }
}
```

1. Run the `allHeros` query to see all the Hero's.

1. Run the `updateBankBalance` and re-run `allHeros` to see update bank balance.

1. Run `allTeams` and notice it uses a fragment which is where you can specify common fields. 

1. Run `addStarLordToXMenTeam` to add Star Lord to the X-Men.  Notice it returns the updated team.

1. Re-run `allTeams`
