# z-oss API 部署镜像 (FEATURE034 T3)
#
# Build context: z-oss/  (从仓库根传 z-oss 给 docker build)
# 触发: 先在项目根跑 `mvn -B -DskipTests=true -pl z-oss -am install`
#        产出 z-oss-api/target/z-oss-api-1.0.0-SNAPSHOT-exec.jar
#
# 基础镜像：Eclipse Temurin 8 JRE (JDK 体积多 350MB，运行时不需要)
FROM eclipse-temurin:8-jre

LABEL maintainer="zifang"

# 设置时区 + 装 wget (供 HEALTHCHECK 使用)
RUN apt-get update && apt-get install -y --no-install-recommends tzdata wget && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apt-get remove -y tzdata && apt-get autoremove -y && apt-get clean

WORKDIR /app

# 创建OSS存储目录
RUN mkdir -p /data/oss

# 复制SpringBoot 可执行 fat jar (context = z-oss/, 相对路径)
COPY z-oss-api/target/z-oss-api-1.0.0-SNAPSHOT-exec.jar app.jar

# 暴露端口：HTTP 8088
EXPOSE 8088

# 健康检查（FEATURE034 T4）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O- http://127.0.0.1:8088/doc.html || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]# 基础镜像：Eclipse Temurin 8 JDK
FROM eclipse-temurin:8-jdk

# 维护者信息
LABEL maintainer="zifang"

# 设置时区
RUN apt-get update && apt-get install -y tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apt-get remove -y tzdata && apt-get autoremove -y && apt-get clean

# 工作目录
WORKDIR /app

# 创建OSS存储目录
RUN mkdir -p /data/oss

# 复制SpringBoot jar包
COPY ../z-oss-api/target/z-oss-api-1.0.0-SNAPSHOT.jar app.jar

# 暴露端口：HTTP 8088
EXPOSE 8088

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]