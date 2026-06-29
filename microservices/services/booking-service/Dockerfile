FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 5008

ENTRYPOINT ["java","-jar","app.jar"]
