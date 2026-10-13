# z-oss API 部署镜像
#
# 构建上下文必须是仓库根 z-oss/（父 pom z-boot-parent 不在磁盘上，在 repo1，
# 所以不能用 z-oss-api/ 当上下文，否则 Maven 拉不到 parent）：
#   mvn -B -DskipTests install
#   docker build -f _deploy/Dockerfile -t z-oss:1.0.4 .
#
FROM eclipse-temurin:8-jre

LABEL maintainer="zifang"

RUN apt-get update && apt-get install -y --no-install-recommends tzdata wget && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apt-get purge -y tzdata && apt-get autoremove -y && apt-get clean

WORKDIR /app

# 本地后端的对象落盘根目录。K8s 侧用 PVC 挂到这儿，所以这里只建目录不做卷。
RUN mkdir -p /data/oss

# 版本号跟着根 pom 的 <version> 走，抬版本时这里必须同步改（改错的表现是
# 镜像构建失败或 —— 更糟 —— 构建成功但跑的是旧 jar）
ARG JAR_FILE=z-oss-api/target/z-oss-api-1.0.4-exec.jar
COPY ${JAR_FILE} /app/app.jar

# 与 application.yml 的 server.port 一致
EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -q -O- http://127.0.0.1:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]