# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src src
RUN chmod +x gradlew \
    && ./gradlew buildFatJar --no-daemon

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
RUN useradd --system --uid 10001 --shell /usr/sbin/nologin appuser
COPY --from=build /src/build/libs/gnotifier-all.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
