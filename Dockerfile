# Stage 1: Build the Spring Boot app
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy pom first for caching
COPY pom.xml .

# Copy source code
COPY src ./src

# Build the JAR (skip tests for faster build)
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# Install netcat for dependency checks
RUN apk add --no-cache netcat-openbsd

# Copy the built JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Expose application port
EXPOSE 8082

# Set JVM memory safe defaults
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# Wait for all dependencies to be ready, then start the app
ENTRYPOINT ["sh", "-c", "\
until nc -z postgres_chats 5432; do echo waiting for postgres_chats; sleep 2; done; \
echo All dependencies are up! Starting app...; \
java $JAVA_OPTS -jar app.jar"]

