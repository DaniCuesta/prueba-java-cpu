FROM docker.io/library/maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM docker.io/library/eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/prueba-java-cpu-1.0.0-jar-with-dependencies.jar app.jar

ENV MAX_CORES=3
ENV STEP_SECONDS=10

ENTRYPOINT ["java", "-jar", "app.jar"]

