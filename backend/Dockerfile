# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fonts-noto-cjk libreoffice-calc \
    && rm -rf /var/lib/apt/lists/* \
    && soffice --version

WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    mvn -B -ntp -Pprod -Dmaven.repo.local=/root/.m2/repository package \
    && cp target/shoe-factory-backend-*.jar /build/app.jar

FROM runtime
COPY --from=build /build/app.jar /app/app.jar
