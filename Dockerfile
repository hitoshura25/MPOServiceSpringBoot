FROM gradle:6.3-jdk8 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon 

FROM openjdk:8-jdk-alpine
#VOLUME /tmp
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/mpospringboot*.jar /app/app.jar
COPY --from=build /home/gradle/src/docker_scripts/run.sh /app/run.sh
#ADD build/libs/mpospringboot*.jar /app.jar
#ADD docker_scripts/run.sh /run.sh
RUN sh -c 'touch /app/app.jar'
CMD ["bash","-C", "/app/run.sh"]
