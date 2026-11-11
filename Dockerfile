# Image de la mini-banque : UN conteneur qui sert l'API Spring Boot ET l'interface Angular.
# Construction en 3 étapes : seuls le JRE et l'application arrivent dans l'image finale
# (pas de Node, pas de Maven, pas de JDK, pas de code source).
#
#   docker build -t mini-banque .
#
# Les tests ne tournent pas ici : la CI (GitHub Actions) les lance avant de construire l'image.

# ---------- 1. Interface Angular ----------
FROM node:24-alpine AS interface
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
# Certificat racine facultatif (antivirus ou proxy qui inspecte le HTTPS), utilisé seulement pendant npm ci
RUN --mount=type=secret,id=ca_supplementaire,required=false \
    if [ -s /run/secrets/ca_supplementaire ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/ca_supplementaire; fi && \
    npm ci
COPY frontend/ ./
RUN npx ng build --configuration production

# ---------- 2. API Spring Boot (avec l'interface intégrée) ----------
FROM maven:3.9-eclipse-temurin-26-alpine AS api
WORKDIR /app
# Même certificat facultatif, ajouté au magasin de certificats de Java de CETTE étape seulement
RUN --mount=type=secret,id=ca_supplementaire,required=false \
    if [ -s /run/secrets/ca_supplementaire ]; then \
      keytool -importcert -noprompt -cacerts -storepass changeit -alias ca-supplementaire -file /run/secrets/ca_supplementaire; \
    fi
COPY pom.xml ./
# Dépendances Maven téléchargées dans une couche à part (et un cache) : réutilisées tant que pom.xml ne change pas
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Les fichiers compilés d'Angular deviennent des ressources statiques de Spring Boot
COPY --from=interface /frontend/dist/mini-banque-web/browser/ ./src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests
# Découpe le JAR en couches (dépendances / chargeur / application) : à chaque nouvelle version,
# seule la petite couche « application » change, les autres restent en cache.
RUN java -Djarmode=tools -jar target/mini-banque-*.jar extract --layers --launcher --destination extrait

# ---------- 3. Image finale ----------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S banque && adduser -S banque -G banque
COPY --from=api /app/extrait/dependencies/ ./
COPY --from=api /app/extrait/spring-boot-loader/ ./
COPY --from=api /app/extrait/snapshot-dependencies/ ./
COPY --from=api /app/extrait/application/ ./
# Jamais en root
USER banque
ENV SPRING_DOCKER_COMPOSE_ENABLED=false \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- "http://localhost:${PORT:-8080}/actuator/health" | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
