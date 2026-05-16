# Build from project root: docker build -f warehouse-producer/Dockerfile .
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY warehouse-producer/pom.xml ./warehouse-producer/pom.xml

RUN mvn -B -f pom.xml dependency:go-offline -pl warehouse-producer -am -q

COPY warehouse-producer/src ./warehouse-producer/src
RUN mvn -B -f pom.xml package -pl warehouse-producer -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S warehouse && adduser -S warehouse -G warehouse
USER warehouse

COPY --from=build /workspace/warehouse-producer/target/warehouse-producer-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
