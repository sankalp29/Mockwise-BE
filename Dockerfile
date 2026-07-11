FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre

# Toolchains for multi-language syntax checks.
# Java uses the JDK/JRE tools API from the base image when available.
# Heavier SDKs (full .NET, Scala) may still report toolStatus=MISSING unless added.
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      python3 \
      g++ \
      build-essential \
      nodejs \
      npm \
      golang-go \
      ruby \
      curl \
      ca-certificates \
    && npm install -g typescript \
    && curl --proto "=https" --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y \
    && rm -rf /var/lib/apt/lists/*

ENV PATH="/root/.cargo/bin:${PATH}"

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
