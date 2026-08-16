FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/PrivateChatServer-0.0.1-SNAPSHOT.jar app.jar
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
