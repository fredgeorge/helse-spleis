# Spleis
![Bygg og deploy app](https://github.com/navikt/helse-spleis/workflows/Bygg%20og%20deploy%20app/badge.svg)
![Bygg og deploy api](https://github.com/navikt/helse-spleis/workflows/Bygg%20og%20deploy%20api/badge.svg)

## Beskrivelse

Tar inn søknader og inntektsmeldinger for en person og foreslår utbetalinger.

## Regler
Dagturnering: https://github.com/navikt/helse-spleis/blob/master/sykepenger-model/src/main/resources/dagturnering.csv

## Bygge prosjektet
For å bygge trenger man å oppgi en Github-bruker med lesetilgang til Github-repoet.
Dette gjøres enklest ved å opprette et personal access token for Github-brukeren din:

På Github: gå til Settings/Developer settings/Personal access tokens,
og opprett et nytt token med scope "read:packages"

Legg inn tokenet i din `.gradle/gradle.properties` fil slik:

```
githubUser=x-access-token
githubPassword=<tokenet ditt>
```

## Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.
### For NAV-ansatte
Interne henvendelser kan sendes via Slack i kanalen #område-helse.
