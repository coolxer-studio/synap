ARG BASE_IMAGE_REPOSITORY=crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/eclipse-temurin
ARG BASE_IMAGE_ARCH=amd64
FROM ${BASE_IMAGE_REPOSITORY}:17-jdk-${BASE_IMAGE_ARCH}

ARG BUILD_DATE
ARG VCS_REF
ARG VERSION=latest

LABEL org.opencontainers.image.title="synap-server" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${VCS_REF}"

WORKDIR /app

COPY target/application.jar /app/application.jar

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 11099

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
