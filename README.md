# nager-holiday-service

Spring Boot (Java 21) service using **Reactive WebClient** for efficient parallel calls to the Nager.Date API.

A small service that wraps the Nager.Date API to provide:

1. **Last 3 celebrated holidays** for a country (date + English name)
2. **Count of non-weekend public holidays** for given countries in a year (sorted desc)
3. **Common holiday dates** between two countries (date + local names)

Github links: https://github.com/sagnikrouth/nager-holiday-service.git

**Endpoints**

- `GET /api/holidays/last-3/AD`
- `GET /api/holidays/weekday-count?year=2025&countries=US,GB,AU`
- `GET /api/holidays/common-dates?year=2024&countryA=US&countryB=GB`

## Highlights
- **Reactive WebClient** only with **timeouts** & **light retry** configured via `application.yml`
- **Resilience4j RateLimiter** (reactive operator) configured via `application.yml`
- **Caffeine cache** (async) — cache names/spec in `application.yml`
- **Weekend rules**: country overrides in `application.yml`, default Saturday/Sunday
- **WireMock** integration tests (fake server) against WebClient
- **Actuator** health endpoint
- **Springdoc OpenAPI** (no inline examples; auto docs at `/v3/api-docs`)

## Run locally
```bash
mvn -q -DskipTests package
java -jar target/nager-holiday-service-1.0.0.jar
```

Swagger UI: `http://localhost:8080/swagger-ui.html`
OpenAPI JSON: `http://localhost:8080/v3/api-docs`
OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`
Health: `http://localhost:8080/actuator/health`


## Prereqs
- JDK 21
- Maven 3.9+

## Build & Run

```bash
mvn clean package

Run all tests:
mvn test

##Country specific weekend rules which are config-driven under `holiday.weekend.overrides`. 
Can be modified in `application.yml`.

