FROM maven:3.9.12-eclipse-temurin-21@sha256:c3c9d3ac4ce8431a3995c0318b8d390f448e693dd4fabc16e9b68d2e1f3d7b46 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre@sha256:227118764cccd3c3294617061aaa93a08309c63a395eefa83e6b35032992ea99
# /usr/bin/pebble vem na imagem base (fora do dpkg), não é usado pela JVM e carrega CVEs da stdlib Go.
RUN rm -f /usr/bin/pebble \
    && useradd --system --uid 10001 --home-dir /app filenorm \
    && mkdir -p /data/uploads \
    && chown -R filenorm /data
WORKDIR /app
COPY --from=build /workspace/target/filenorm-api-*.jar /app/app.jar
USER filenorm
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
