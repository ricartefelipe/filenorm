FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /app filenorm \
    && mkdir -p /data/uploads \
    && chown -R filenorm /data
WORKDIR /app
COPY --from=build /workspace/target/filenorm-api-*.jar /app/app.jar
USER filenorm
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
