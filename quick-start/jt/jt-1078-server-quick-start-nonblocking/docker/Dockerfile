FROM m.daocloud.io/docker.io/library/eclipse-temurin:21-jre
LABEL authors="hylexus"

WORKDIR /app/

ARG JAR_FILE=build/libs/jt-1078-server-quick-start-nonblocking.jar
COPY ${JAR_FILE} app.jar

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom"
ENV APP_ARGS="--spring.profiles.active=docker_compose"

CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar ${APP_ARGS}"]

EXPOSE 8888
