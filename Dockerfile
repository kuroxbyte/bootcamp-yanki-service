# syntax=docker/dockerfile:1

# ---- Compilación ----
# Checkstyle 14 necesita Java 21 para ejecutarse; el código se compila para Java 17.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY checkstyle checkstyle
COPY src src
# Checkstyle, pruebas y cobertura se ejecutan en el pipeline de Maven, no al construir la imagen.
RUN ./mvnw -q -B package -DskipTests -Dcheckstyle.skip -Djacoco.skip

# ---- Ejecución ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/target/yanki-service-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
