# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH
WORKDIR /app
# 使用 Maven Central 的规范地址，避免第三方镜像 TLS 不稳定导致生产构建失败
RUN mkdir -p /root/.m2 && printf '%s\n' '<?xml version="1.0" encoding="UTF-8"?>' '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' '  <mirrors>' '    <mirror>' '      <id>maven-central</id>' '      <mirrorOf>central</mirrorOf>' '      <name>Maven Central</name>' '      <url>https://repo.maven.apache.org/maven2</url>' '    </mirror>' '  </mirrors>' '</settings>' > /root/.m2/settings.xml
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Runtime stage - 使用Debian镜像避免Alpine musl libc的DNS解析问题
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN mkdir -p /data && chmod 777 /data
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx256m", "-Xms128m", "-jar", "app.jar", "--spring.profiles.active=docker", "--spring.datasource.url=jdbc:h2:file:/data/xiqin;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL", "--spring.datasource.username=sa", "--spring.datasource.password=", "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.jpa.hibernate.ddl-auto=update", "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "--spring.flyway.enabled=false"]
