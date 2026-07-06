FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY backend/pom.xml ./pom.xml
COPY backend/src ./src

RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /opt/app

COPY --from=builder /workspace/target/problem-platform-1.0.0.jar ./app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]
