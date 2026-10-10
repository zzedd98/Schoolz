# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-amazoncorretto-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:21.0.8_9-jre-alpine
RUN apk add --no-cache font-dejavu font-noto font-noto-arabic \
    && addgroup -S schoolz \
    && adduser -S schoolz -G schoolz
WORKDIR /app
COPY --from=build --chown=schoolz:schoolz /workspace/target/school-erp-backend-*.jar app.jar
USER schoolz
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
