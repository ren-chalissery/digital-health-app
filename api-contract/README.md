# API contract

`openapi.yaml` is generated from the Spring controllers, not hand-written. Regenerate it with:

```bash
cd backend && ./gradlew generateOpenApiSpec
```

CI runs the same task and fails if the committed file differs from what the code produces. That
check is the only thing preventing the Angular, Swift, and Kotlin clients from silently drifting
away from the API, since none of them share a compiler with it.

Client generation configuration lives in `generator/`.
