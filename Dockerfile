FROM java:8
VOLUME /tmp
ADD build/libs/mpospringboot*.jar /app.jar
ADD docker_scripts/run.sh /run.sh
RUN sh -c 'touch /app.jar'
CMD ["bash","-C", "/run.sh"]