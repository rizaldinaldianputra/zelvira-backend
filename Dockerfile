# Use Maven image with OpenJDK 21 for building the application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy settings.xml for Maven mirror
COPY settings.xml /root/.m2/settings.xml

# Pre-fetch dependencies for better Docker caching
COPY pom.xml .
RUN mvn dependency:resolve -B -s /root/.m2/settings.xml \
    -Dmaven.wagon.http.retryHandler.count=3 \
    -Dmaven.wagon.http.pool=false || true

# Copy source code and package
COPY src ./src
RUN mvn clean package -B -DskipTests -s /root/.m2/settings.xml \
    -Dmaven.wagon.http.retryHandler.count=3 \
    -Dmaven.wagon.http.pool=false

# Use OpenJDK 21 for running the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /app/target/zelvira-*.jar app.jar

# Expose the application port
EXPOSE 8081

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
