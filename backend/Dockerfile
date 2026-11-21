# ---------- Build stage: compile and package the Spring Boot jar ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy only the build descriptors first so the dependency layer is cached
# and only re-downloaded when pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests need Docker (Testcontainers) and run in CI, not inside the image build.
RUN ./mvnw -B -q package -DskipTests

# ---------- Runtime stage: JRE only, non-root user ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
