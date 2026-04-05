FROM public.ecr.aws/docker/library/eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/ai-generator-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
