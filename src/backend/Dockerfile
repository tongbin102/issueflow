# 后端镜像：基于 Eclipse Temurin 17 JDK
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && \
    mvn -B clean package -DskipTests && \
    cp target/issueflow-backend.jar /app/app.jar

FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /app/app.jar /app/app.jar
# 附件存储卷
VOLUME ["/data/attachments"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
