# syntax=docker/dockerfile:1

# ---- build stage: resolve dependencies first so source changes reuse the cache
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY infrastructure/pom.xml infrastructure/
COPY bootstrap/pom.xml bootstrap/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -ntp -DskipTests -Dspring-boot.repackage.skip=true package

COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src
COPY bootstrap/src bootstrap/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -ntp -DskipTests package

# ---- runtime stage
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /workspace/bootstrap/target/flights-backend.jar app.jar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
