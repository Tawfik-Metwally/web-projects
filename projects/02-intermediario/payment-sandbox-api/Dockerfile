FROM eclipse-temurin:25.0.4_7-jdk-noble@sha256:2feab631bffce6236d8bb5261a4abe19a8d6f85bad1c01166f74686c983d011f AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:25.0.4_7-jre-noble@sha256:b573af9e331196fbc42e246da4df24df9b6c556c73e7efddfde0511f1c9508c5 AS runtime

LABEL org.opencontainers.image.title="Payment Sandbox API" \
    org.opencontainers.image.description="Containerized API for simulated payment flows" \
    org.opencontainers.image.source="https://github.com/Tawfik-Metwally/web-projects"

RUN groupadd --system --gid 10001 spring \
    && useradd --system --uid 10001 --gid spring spring

WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/target/*.jar app.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
