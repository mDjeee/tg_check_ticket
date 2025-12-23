# Use an official OpenJDK runtime as a parent image
FROM eclipse-temurin:17-jdk-jammy

# Set working directory inside the container
WORKDIR /app

# Copy Maven build files
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Copy source code
COPY src src

# Package the app
RUN ./mvnw clean package -DskipTests

# Expose the port your Spring Boot app runs on
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "target/demo-0.0.1-SNAPSHOT.jar", "-Dh2.console.settings.web-allow-others=true", "--spring.profiles.active=prod"]
