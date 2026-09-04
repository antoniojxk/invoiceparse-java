FROM node:26-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
COPY samples samples
COPY --from=frontend-build /frontend/dist src/main/resources/static
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN groupadd --system invoiceparse && useradd --system --gid invoiceparse --home-dir /app invoiceparse
COPY --from=build --chown=invoiceparse:invoiceparse /workspace/target/invoiceparse-java-*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55.0 -XX:+ExitOnOutOfMemoryError"
USER invoiceparse
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
