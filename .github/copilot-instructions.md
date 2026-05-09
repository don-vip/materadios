Copilot instructions — materadios

Purpose

This repository is a small utility for exporting contents from a Matera.eu account to Gmail / Google Drive (per README). At the moment the repository contains only documentation and no application code or CI configuration.

Build, test, and lint commands (detected)

- No build, test, or lint commands or configurations were detected in the repository root.
- No test runner or package manifest (package.json, pom.xml, build.gradle, requirements.txt, pyproject.toml, etc.) is present. If/when those files are added, list exact commands here (examples are not authoritative):
  - Node: npm test, npm run lint, npm run test -- <testname>
  - Python/pytest: pytest tests/test_foo.py::test_bar
  - Java/Maven: mvn -Dtest=ClassName#method test

How to run a single test (if a test framework is added)

- Run the test runner with the framework's single-test selector (e.g., pytest <path>::<testname>, npm test -- -t "test name", mvn -Dtest=ClassName#method test). Add the concrete command to this document when the project picks a language/tooling.

High-level architecture (big picture)

- Intended flow for this project type:
  1. Authentication + account access (Matera.eu auth flow or API credentials).
  2. Data exporter/fetcher: iterate user resources and download/export items.
  3. Transformer/packager: adapt fetched data into formats acceptable to Google APIs (mail attachments, drive files, metadata).
  4. Delivery connectors: Gmail API client (send or import), Google Drive client (upload and set folders/permissions).
  5. CLI / orchestrator / scheduler: entrypoint that runs the export once or on a schedule, retries, logging and status reporting.

- Typical locations (add these folders/files as features are implemented):
  - src/ or app/ — application source code
  - tests/ or src/test/ — automated tests
  - config/ or .env template — credentials/configuration examples (do NOT commit real secrets)
  - scripts/ — CLI wrappers or helper scripts

Key conventions and repo-specific notes

- Currently minimal: there are no language-specific conventions enforced. The .gitignore contains common Java ignore entries — if adding Java code, follow standard Maven/Gradle layout.
- Never commit credentials or tokens. Use environment variables, a secrets manager, or a local .env file excluded by .gitignore.
- Prefer adding a small README section for any chosen runtime/tooling that documents exact build/test/lint commands; Copilot will reference that first.

Where Copilot should start when helping in this repo

- Search for (in order): manifests (package.json, pom.xml, requirements.txt, pyproject.toml), src/ or app/ folders, CI in .github/workflows/, and tests/.
- If no code exists, propose a minimal scaffold (language choice + skeleton for auth, fetcher, transformer, uploader) and include exact commands to run and test locally.

Existing AI/assistant configs found

- No assistant rule files (CLAUDE.md, AGENTS.md, .cursorrules, .windsurfrules, etc.) were found.

Adding this file

- If you add tooling or CI, update this file with exact commands for build/test/lint and where the main entrypoint lives.

Notes for maintainers

- Keep this file small and authoritative: Copilot will surface these commands and paths when asked to implement or modify features.

Java + Spring Boot (Maven) scaffold (recommended)

This repository is a good fit for a Java + Spring Boot implementation. The guidance below assumes use of the latest stable Java (use the current LTS or latest GA), the latest Maven release, and the latest Spring Boot release at the time of development. Pin exact versions in pom.xml when required by CI or enterprise policy.

Project generation

- Use start.spring.io or the Spring Initializr to generate a Maven project with the latest Spring Boot and the desired Java version (LTS recommended).
- Add dependencies: spring-boot-starter-web, spring-boot-starter-actuator, spring-boot-starter-test (test scope), and Google's Java client libraries for Gmail/Drive.

Recommended dependencies (pom.xml)

- org.springframework.boot:spring-boot-starter-web
- org.springframework.boot:spring-boot-starter-actuator
- org.springframework.boot:spring-boot-starter-test (scope=test)
- com.google.api-client:google-api-client
- com.google.apis:google-api-services-gmail (pick current artifactId/groupId)
- com.google.oauth-client:google-oauth-client-jetty (or appropriate OAuth client)
- Optional: Lombok, Spring Retry

Project layout conventions

- src/main/java/com/example/materadios/  — main packages
  - controller/  — REST endpoints or CLI entrypoints
  - service/     — core business logic (auth, exporter, uploader)
  - model/       — DTOs and domain models
  - config/      — bean configuration (Google clients, credentials)
  - persistence/ — optional state/repository code
- src/main/resources/
  - application.yml (profiles: application-dev.yml, application-prod.yml)
  - credentials/ (templates only; never commit secrets)
- src/test/ — unit and integration tests

Build, run, test, and lint commands (Maven, Windows examples)

- Build: .\mvnw -U clean package  (use .\mvnw if wrapper is committed; otherwise mvn -U clean package)
- Run locally: .\mvnw spring-boot:run  (or mvn spring-boot:run)
- Run tests: .\mvnw test
- Run a single test class: .\mvnw -Dtest=com.example.materadios.service.ExportServiceTest test
- Run a single test method: .\mvnw -Dtest=com.example.materadios.service.ExportServiceTest#exportsData test
- Lint (if Checkstyle/Spotless configured): .\mvnw checkstyle:check or .\mvnw spotless:check

Notes on versions

- Rely on Spring Boot dependency management for Spring-managed dependencies; pin only when necessary.
- Use the Maven wrapper (.mvnw/.mvnw.cmd) to ensure consistent Maven versions across environments.

Google API specifics

- Keep credential JSON files out of source control. Reference their path with an environment variable (e.g., GOOGLE_APPLICATION_CREDENTIALS) or a filesystem path injected by CI.
- Provide a credentials template under src/main/resources/credentials/README.md describing required env vars and scopes.
- Implement a GoogleAuthService Spring bean that constructs OAuth2 credentials and provides injectable Gmail/Drive client beans.

Secrets and configuration

- Use application-{profile}.yml and environment variables for secrets. Example properties: google.credentials.path, google.oauth.clientId, google.oauth.clientSecret.
- Ensure credentials directories are in .gitignore. Prefer externalized secrets for CI/production.

Testing and CI pointers

- Unit tests: mock Google APIs (Mockito, MockWebServer/WireMock) and keep tests fast.
- Integration tests: use a profile that loads fixtures or recorded responses; avoid depending on live Google services in CI.
- In CI, inject credentials via secure variables and set GOOGLE_APPLICATION_CREDENTIALS to a temporary path prior to running integration tests.

Copilot scaffolding expectations

When asked to scaffold, Copilot should propose:
- A pom.xml with dependency management for Spring Boot and the Google API clients
- A minimal Application class (SpringBootApplication) and a CommandLineRunner or REST controller to trigger exports
- GoogleAuthService, ExportService, and simple unit tests showcasing mocking of Google clients
- Exact Windows commands to build/run/test as listed above

Updating this file

- When upgrading Java, Maven, or Spring Boot, update the commands and any referenced tooling (e.g., wrapper instructions).

(End)
