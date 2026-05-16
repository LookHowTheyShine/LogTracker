# Build stage — run from project root: docker build -f warehouse-alert-processor/Dockerfile .
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Copy parent POM first (cached layer unless parent pom.xml changes)
COPY pom.xml ./pom.xml

# Copy module POM (cached layer unless module pom.xml changes)
COPY warehouse-alert-processor/pom.xml ./warehouse-alert-processor/pom.xml

# Resolve dependencies before copying source (maximises Docker layer cache)
RUN mvn -B -f pom.xml dependency:go-offline -pl warehouse-alert-processor -am -q

# Copy source and build
COPY warehouse-alert-processor/src ./warehouse-alert-processor/src
RUN mvn -B -f pom.xml package -pl warehouse-alert-processor -am -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S warehouse && adduser -S warehouse -G warehouse
USER warehouse

COPY --from=build /workspace/warehouse-alert-processor/target/warehouse-alert-processor-*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
