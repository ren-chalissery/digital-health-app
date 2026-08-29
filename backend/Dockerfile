# Build inside the image so a release never depends on what happened to be installed on the
# machine that produced it.
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /build

# Wrapper and dependency declarations first: these change far less often than the source, so the
# dependency download stays cached across ordinary commits.
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle build.gradle ./
RUN ./gradlew --no-daemon dependencies --quiet || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Nothing in the container needs root, and a compromised process should not be able to write to it.
RUN addgroup -S app && adduser -S -G app app
USER app

COPY --from=build --chown=app:app /build/build/libs/*.jar app.jar

EXPOSE 8080

# Without this the JVM sizes the heap from the host's memory rather than the task's, and gets
# killed by ECS the first time traffic arrives.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
