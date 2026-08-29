# API contract

`openapi.yaml` is generated from the Spring controllers, not hand-written.

```bash
cd backend && ./gradlew generateOpenApiSpec
```

The drift check is an ordinary backend test (`OpenApiSpecTest`), so `./gradlew test` fails whenever
the committed document no longer matches the controllers. That check is the only thing preventing
the Angular, Swift, and Kotlin clients from silently falling behind the API, since none of them
share a compiler with it.

## Generating the clients

```bash
cd api-contract
npm install
npm run generate:web      # Angular, into web/src/app/api
```

Generated clients are committed, so nobody needs Java and the generator just to build the app. The
generator version is pinned in `openapitools.json`; changing it will reformat every generated file,
so treat it as its own change.

`generate:ios` and `generate:android` exist but are not wired into any build: there is no Xcode or
Gradle project to generate into until Phase 5. Their configuration is committed now so that when
those apps arrive their clients come from this same document rather than being written by hand.

## Changing the API

1. Change the controller.
2. `cd backend && ./gradlew generateOpenApiSpec`
3. `cd api-contract && npm run generate:web`
4. Commit the controller, `openapi.yaml`, and the regenerated client together.
