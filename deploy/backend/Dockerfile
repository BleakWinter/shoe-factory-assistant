FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY backend/pom.xml ./pom.xml
RUN mvn -B -q -DskipTests dependency:go-offline

COPY backend/src ./src
RUN mvn -B -q -Pprod -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends libreoffice-calc fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/target/shoe-factory-backend-0.1.0-SNAPSHOT.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV APP_FILE_STORAGE_ROOT_PATH=/data/shoe-factory-assistant/files

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
