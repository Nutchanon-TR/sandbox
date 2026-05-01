# NOTE: Build context is `./backend` (set in docker-compose.yml and github actions) so we can also
# COPY the sibling `common-auth/` library into the build stage.
#
# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

# ── 1) Install the shared common-auth library into Maven local cache ──
#    Must happen BEFORE building user so its <dependency> on common-auth resolves.
COPY common-auth/pom.xml /build/common-auth/pom.xml
COPY common-auth/src     /build/common-auth/src
RUN cd /build/common-auth && mvn install -DskipTests -B

# ── 2) Cache user dependencies (changes less often than source) ──
COPY user/pom.xml /build/user/pom.xml
WORKDIR /build/user
RUN mvn dependency:go-offline -B

# ── 3) Build user — common-auth resolves from local cache populated in step 1 ──
COPY user/src /build/user/src
RUN mvn clean package -DskipTests -B

# Stage 2: Create the minimal runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
# common-auth is bundled INSIDE this jar at BOOT-INF/lib/common-auth-0.1.0.jar
COPY --from=builder /build/user/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
