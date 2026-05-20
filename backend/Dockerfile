# =============================================================================
# Halo backend — Dockerfile dev/staging (multi-stage)
#
# Versão produção otimizada (camadas separadas, slim JRE, hardening) entra em
# T-045. Esta versão é simples e funcional: roda no profile `app` do
# infra/docker-compose.yml.
# =============================================================================

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cacheia deps separadamente do código-fonte
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuário não-root
RUN useradd --create-home --shell /bin/bash halo
USER halo

COPY --from=build /workspace/target/halo-backend-*.jar /app/halo-backend.jar

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD wget --quiet --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/halo-backend.jar"]
