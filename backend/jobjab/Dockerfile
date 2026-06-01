# NOTE: Build context is `./backend` so the build can install the sibling common-auth library.
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

COPY common-auth/pom.xml /build/common-auth/pom.xml
COPY common-auth/src     /build/common-auth/src
RUN cd /build/common-auth && mvn install -DskipTests -B

COPY jobjab/pom.xml /build/jobjab/pom.xml
WORKDIR /build/jobjab
RUN mvn dependency:go-offline -B

COPY jobjab/src /build/jobjab/src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/jobjab/target/*.jar app.jar
ARG EXPOSE_PORT=8083
EXPOSE ${EXPOSE_PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
