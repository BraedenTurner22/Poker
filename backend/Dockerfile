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

# Render's instance has 512MB. With no flags the JVM caps only the heap (25%
# of RAM); metaspace, the JIT code cache (240MB reserved), thread stacks and
# glibc's per-thread malloc arenas all grow unbounded, so resident memory
# creeps up over days until Render kills the instance. Cap each region so the
# whole process stays well inside the limit:
#   heap <=128MB (live data is ~40MB) + metaspace <=128MB (~75MB used)
#   + code cache <=64MB (~25MB used) + direct buffers <=32MB + stacks/GC/misc.
# MALLOC_ARENA_MAX stops glibc creating up to 8 x (host CPUs) 64MB arenas —
# it counts the host's cores, not the container's CPU quota.
# TieredStopAtLevel=1 (C1 JIT only) skips C2's large transient compiler
# arenas and keeps the code cache smaller; C1 code is plenty fast for this
# app on a fraction of a CPU.
ENV MALLOC_ARENA_MAX=2
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=25 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"

# Render injects PORT at runtime; application.properties binds to it via
# server.port=${PORT:8081}.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
