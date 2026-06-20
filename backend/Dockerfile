# Java 23 isn't covered by Render's native Java buildpack (which lags LTS
# releases), so this builds and runs the app in containers we control instead.

FROM maven:3.9-eclipse-temurin-23 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:23-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render injects PORT at runtime; application.properties binds to it via
# server.port=${PORT:8081}.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
