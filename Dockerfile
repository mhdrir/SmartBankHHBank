# Multi-stage build:
#  stage 1 (build) has Maven + JDK and compiles the jar
#  stage 2 (runtime) has only a JRE + the jar -> smaller image, smaller attack surface

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy the pom first and download dependencies: this layer is cached as long as pom.xml is unchanged,
# so code-only changes rebuild in seconds.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
# Never run as root inside the container
RUN useradd --system --uid 1001 spring
USER spring
COPY --from=build /app/target/smartbank-payments-*.jar app.jar
EXPOSE 8080
# MaxRAMPercentage: let the JVM size its heap from the container memory limit (ECS/Kubernetes)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
