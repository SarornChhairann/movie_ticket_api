FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package && cp target/*.war app.war

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S spring \
    && adduser -S spring -G spring \
    && mkdir -p /app/uploads/movies /app/uploads/food-items \
    && chown -R spring:spring /app

COPY --from=build /workspace/app.war /app/app.war

USER spring

ENV SERVER_PORT=8010

EXPOSE 8010

ENTRYPOINT ["java", "-jar", "/app/app.war"]
