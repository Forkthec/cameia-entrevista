# syntax=docker/dockerfile:1

# ---------- Etapa de compilacion ----------
# Imagen base con JDK 21 y Maven
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /build

# Descarga de dependencias en una capa propia: solo se repite si cambia el pom
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

# Compilacion del codigo fuente; las pruebas se ejecutan en CI, no en la imagen
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests \
    && mv target/*.jar /build/app.jar

# ---------- Etapa de ejecucion ----------
# Imagen base con JRE 21, sin herramientas de compilacion
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Usuario sin privilegios: el proceso Java nunca corre como root
RUN addgroup -S cameia && adduser -S cameia -G cameia

# El JAR pertenece al usuario de ejecucion y no necesita permisos de escritura
COPY --from=build --chown=cameia:cameia /build/app.jar /app/app.jar
USER cameia

# Puerto interno del contenedor; la publicacion al host la decide docker-compose
ENV SERVER_PORT=8080
EXPOSE 8080

# Opciones de JVM ajustables sin reconstruir la imagen
ENV JAVA_OPTS=""

# Sonda de disponibilidad contra el endpoint de salud del microservicio
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --quiet --spider "http://127.0.0.1:${SERVER_PORT}/health" || exit 1

# exec para que la JVM sea PID 1 y reciba las senales de parada de Docker
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
