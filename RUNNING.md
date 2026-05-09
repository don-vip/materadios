Running the scaffolded Matera exporter (Java + Spring Boot)

Prerequisites

- Java 25
- Maven (or use the included Maven wrapper: mvnw / mvnw.cmd)
- Environment variables:
  - MATERA_USERNAME and MATERA_PASSWORD
  - Either GOOGLE_ACCESS_TOKEN (recommended for quick runs) or GOOGLE_CREDENTIALS_PATH (path to Google credentials JSON)

Build and run (Windows)

1. Build: .\\mvnw -U clean package
2. Run: .\\mvnw spring-boot:run

Access UI

- Open http://localhost:8080

Notes

- Matera integration is left as TODOs. Google upload/send helpers are implemented and expect a valid access token in GOOGLE_ACCESS_TOKEN or to be extended to use GOOGLE_CREDENTIALS_PATH to obtain tokens.
- Database: H2 file at ./data/materadios-db
