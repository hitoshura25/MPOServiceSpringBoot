# Build stage
FROM gradle:6.3-jdk8 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle clean build

# Package stage
FROM openjdk:8-jdk-alpine
RUN apk update && apk add bash
COPY --from=build /home/gradle/src/build/libs/mpospringboot*.jar app.jar
COPY --from=build /home/gradle/src/docker_scripts/run.sh run.sh
CMD ["bash","-C", "/run.sh"]

