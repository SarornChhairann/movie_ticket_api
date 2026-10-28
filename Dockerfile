# syntax=docker/dockerfile:1.7
# Production image for the API. Three stages:
#   build   - compile the WAR (Maven repo cached between builds by BuildKit)
#   extract - split it into Spring Boot layers, so a code change only rebuilds the
#             small "application" layer and the ~100 MB of dependencies stay cached
#   runtime - JRE only, non-root, no build tools

FROM mirror.gcr.io/library/maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/*.war app.war

FROM mirror.gcr.io/library/eclipse-temurin:17-jre-jammy AS extract
WORKDIR /extract
COPY --from=build /workspace/app.war app.war
RUN java -Djarmode=tools -jar app.war extract --layers --launcher --destination layers

FROM mirror.gcr.io/library/eclipse-temurin:17-jre-jammy AS runtime

ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="ticket-management-system" \
      org.opencontainers.image.description="Movie ticket booking API (Spring Boot)" \
      org.opencontainers.image.version="${APP_VERSION}"

RUN groupadd --system --gid 10001 spring \
    && useradd --system --uid 10001 --gid spring --home-dir /app --no-create-home --shell /usr/sbin/nologin spring \
    && mkdir -p /app/uploads/movies /app/uploads/food-items /tmp/app \
    && chown -R spring:spring /app /tmp/app

WORKDIR /app
# Least to most often changed, one layer each.
COPY --from=extract --chown=spring:spring /extract/layers/dependencies/ ./
COPY --from=extract --chown=spring:spring /extract/layers/spring-boot-loader/ ./
COPY --from=extract --chown=spring:spring /extract/layers/snapshot-dependencies/ ./
COPY --from=extract --chown=spring:spring /extract/layers/application/ ./

USER spring:spring

# Container-aware heap (a share of the memory limit, not of the host), fail fast on
# OOM so the restart policy takes over, and a writable temp dir when the root
# filesystem is read-only. Extra flags go in JAVA_OPTS.
ENV SERVER_PORT=8010 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+ExitOnOutOfMemoryError -XX:-UsePerfData -Djava.io.tmpdir=/tmp/app -Djava.security.egd=file:/dev/./urandom" \
    JAVA_OPTS=""

EXPOSE 8010

# Readiness over plain bash (/dev/tcp): the JRE image ships no curl or wget.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT} && printf "GET /actuator/health/readiness HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" >&3 && grep -q "\"UP\"" <&3'

# exec form through sh so JAVA_OPTS expands and java is PID 1's direct child
# (compose runs it under tini via `init: true`, which forwards SIGTERM).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.WarLauncher"]
