# z-oss API 部署镜像
#
# 构建上下文必须是仓库根 z-oss/（父 pom z-boot-parent 不在磁盘上，在 repo1，
# 所以不能用 z-oss-api/ 当上下文，否则 Maven 拉不到 parent）：
#   mvn -B -DskipTests install
#   docker build -f _deploy/Dockerfile -t z-oss:1.0.4 .
#
# 底座说明：为什么是 zops-ubuntu:local 而不是 eclipse-temurin:8-jre ——
# 离线集群这台构建机的镜像站全部 TLS handshake 超时，Docker Hub 拉不到
# 任何底座；而 zops-jre8:local 那类手搓 JRE 的 cacerts 是指向树外的悬空符号链接
# （/etc/ssl/certs/java/cacerts），拷进镜像后 truststore 为空，所有 HTTPS 静默失败。
# 所以走"宿主底座 + 包管理器装 JRE"，cacerts 由 apt 自己生成，不会悬空。
FROM zops-ubuntu:local

LABEL maintainer="zifang"

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-8-jre-headless ca-certificates tzdata wget && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apt-get purge -y tzdata && apt-get autoremove -y && apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 装完再验一次 truststore 非空：java -version 测不出这个问题，
# 只有 keytool -list 能（cacerts 悬空时 JVM 起得来，HTTPS 才挂）
RUN keytool -list -keystore /etc/ssl/certs/java/cacerts -storepass changeit > /dev/null

WORKDIR /app

# 本地后端的对象落盘根目录。K8s 侧用 PVC 挂到这儿，所以这里只建目录不做卷。
RUN mkdir -p /data/oss

# 版本号跟着根 pom 的 <version> 走，抬版本时这里必须同步改（改错的表现是
# 镜像构建失败或 —— 更糟 —— 构建成功但跑的是旧 jar）
ARG JAR_FILE=z-oss-api/target/z-oss-api-1.0.4-exec.jar
COPY ${JAR_FILE} /app/app.jar

# 与 z-oss-standalone.yml 的 server.port 一致
EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -q -O- http://127.0.0.1:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]