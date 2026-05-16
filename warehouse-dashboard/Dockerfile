# Build from project root: docker build -f warehouse-dashboard/Dockerfile .
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY warehouse-dashboard/pom.xml ./warehouse-dashboard/pom.xml

RUN mvn -B -f pom.xml dependency:go-offline -pl warehouse-dashboard -am -q

COPY warehouse-dashboard/src ./warehouse-dashboard/src
RUN mvn -B -f pom.xml package -pl warehouse-dashboard -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S warehouse && adduser -S warehouse -G warehouse
USER warehouse

COPY --from=build /workspace/warehouse-dashboard/target/warehouse-dashboard-*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
