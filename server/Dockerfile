# Build-Stufe: baut das Fat-Jar aus :server inklusive :shared.
FROM gradle:8.14-jdk17 AS build
WORKDIR /src

COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared ./shared
COPY server ./server

# Das :app-Modul wird hier bewusst nicht kopiert, sonst braeuchte das Image
# das Android SDK. settings.gradle.kts referenziert es aber, daher wird der
# Include fuer diesen Build entfernt.
RUN sed -i '/include(":app")/d' settings.gradle.kts \
    && gradle --no-daemon :server:buildFatJar

# Laufzeit-Stufe: schlankes JRE ohne Build-Werkzeuge.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S rotaskat && adduser -S rotaskat -G rotaskat
COPY --from=build /src/server/build/libs/*-all.jar app.jar
USER rotaskat

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
