# ---------- Stage 1: Build the JAR with Maven ----------
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Copy Maven config and source code
COPY pom.xml .
COPY src ./src

# Build jar (skip tests for faster build)
RUN mvn clean package -DskipTests


# ---------- Stage 2: Run the JAR ----------
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Copy jar from the build stage
COPY --from=build /app/target/LiveAlerts-1.0-SNAPSHOT.jar app.jar

# Render gives us the PORT environment variable – default to 10000
ENV PORT=10000

EXPOSE 10000

# Start the application
CMD ["java", "-jar", "app.jar"]
