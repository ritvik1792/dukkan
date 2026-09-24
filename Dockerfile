FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 dukkan \
    && mkdir -p /app/data/uploads \
    && chown -R dukkan:dukkan /app
COPY --from=build --chown=dukkan:dukkan /src/target/dukkan-0.1.0.jar app.jar
USER dukkan
ENV PORT=8080
ENV DUKKAN_UPLOAD_DIR=/tmp/dukkan-uploads
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
