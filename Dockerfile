FROM eclipse-temurin:17-jre
ARG JAR_FILE=app.jar
WORKDIR /app
COPY build/${JAR_FILE} app.jar
EXPOSE 8080

#Comprobación de vida simple a /health
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD curl -fsS http://localhost:8080/health || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]