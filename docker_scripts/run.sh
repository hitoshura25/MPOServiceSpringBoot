#!/bin/bash
java -Djava.security.egd=file:/dev/./urandom -Dserver.port=$PORT -jar /app.jar