# Corretto rather than Temurin because Temurin publishes no arm64 Alpine image for 17, which
# leaves the build unable to run on an Apple Silicon laptop. This one is multi-arch, and it matches
# the JDK the README asks developers to install.
FROM amazoncorretto:17-alpine AS build
WORKDIR /build

# Wrapper and dependency declarations first: these change far less often than the source, so the
# dependency download stays cached across ordinary commits.
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle build.gradle ./
RUN ./gradlew --no-daemon dependencies --quiet || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# Shipping the whole JDK costs about 400 MB, and Fargate pulls the image on every task start rather
# than caching it. Every module is kept, because narrowing them by hand is how a reflective Spring
# lookup fails in production and not in CI; the saving comes from dropping debug symbols, headers,
# and man pages.
RUN apk add --no-cache binutils \
    && "$JAVA_HOME/bin/jlink" \
      --add-modules ALL-MODULE-PATH \
      --strip-debug --no-man-pages --no-header-files --compress=2 \
      --output /runtime

FROM alpine:3.22 AS runtime
WORKDIR /app

RUN apk add --no-cache libstdc++ \
    # Nothing in the container needs root, and a compromised process should not write to it.
    && addgroup -S app && adduser -S -G app app

COPY --from=build /runtime /opt/java
ENV PATH="/opt/java/bin:${PATH}"

COPY --from=build --chown=app:app /build/build/libs/*.jar app.jar

USER app
EXPOSE 8080

# Without this the JVM sizes the heap from the host's memory rather than the task's, and gets
# killed by ECS the first time traffic arrives.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
