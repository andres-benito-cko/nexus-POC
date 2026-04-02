FROM public.ecr.aws/docker/library/gradle:8.7-jdk17 AS build
WORKDIR /app
COPY settings.gradle ./
COPY build.gradle ./
COPY src ./src
RUN gradle bootJar --no-daemon -x test

FROM public.ecr.aws/docker/library/eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
