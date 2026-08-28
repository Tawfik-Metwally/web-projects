FROM eclipse-temurin:25.0.4_7-jdk-noble AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:25.0.4_7-jre-noble AS runtime

RUN groupadd --system --gid 10001 spring \
    && useradd --system --uid 10001 --gid spring spring

WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/target/*.jar app.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
