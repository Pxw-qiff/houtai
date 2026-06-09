FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src
RUN mvn -q -s /root/.m2/settings.xml -DskipTests package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=builder /build/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]