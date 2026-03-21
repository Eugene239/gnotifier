# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build
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

FROM bellsoft/liberica-openjre-alpine-musl:21
WORKDIR /app
RUN addgroup -S app && adduser -S -u 10001 -G app appuser
COPY --from=build /src/build/libs/gnotifier-all.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xms32m", "-Xmx128m", "-jar", "/app/app.jar"]
