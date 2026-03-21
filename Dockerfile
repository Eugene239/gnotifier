# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
ARG GIT_SHA=unknown
ARG BUILD_DATE=
ENV GIT_SHA=${GIT_SHA}
ENV BUILD_DATE=${BUILD_DATE}
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY src src
RUN chmod +x gradlew \
    && BUILD_DATE="${BUILD_DATE:-$(date -u +%Y%m%d%H)}" \
    && export BUILD_DATE \
    && ./gradlew buildFatJar --no-daemon -PbuildInfoSha="${GIT_SHA}" -PbuildInfoDate="${BUILD_DATE}"

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
RUN useradd --system --uid 10001 --shell /usr/sbin/nologin appuser
COPY --from=build /src/build/libs/gnotifier-all.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
