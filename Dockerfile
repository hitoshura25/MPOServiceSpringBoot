FROM openjdk:8-jdk-alpine
VOLUME /tmp
CMD ["./gradlew", "clean", "build"]
ADD build/libs/mpospringboot*.jar /app.jar
ADD docker_scripts/run.sh /run.sh
RUN sh -c 'touch /app.jar'
CMD ["bash","-C", "/run.sh"]
