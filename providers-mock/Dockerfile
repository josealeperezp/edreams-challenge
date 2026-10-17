# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -ntp -DskipTests -Dspring-boot.repackage.skip=true package
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -ntp -DskipTests package

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /workspace/target/providers-mock.jar app.jar
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
