# 后端镜像：基于 Eclipse Temurin 17 JDK
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && \
    mvn -B clean package -DskipTests && \
    cp target/issueflow-backend.jar /app/app.jar

# ---------------------------------------------------------------------------
# MySQL 8 官方客户端（Phase10 数据管理：备份 mysqldump / 恢复 mysql）
#
# ⚠ 为什么不用 `apt-get install mariadb-client`：
#   Debian 源里的 default-mysql-client 实际是 MariaDB 分支，
#   其 mysqldump 生成的 DDL 方言（如 utf8mb4_0900_ai_ci 排序规则、
#   /*!80016 ... */ 版本注释）与 MySQL 8.0 不兼容，
#   备份能跑但恢复时会在 CREATE TABLE 处报 Unknown collation。
#   因此这里从 mysql:8.0 官方镜像直接复制二进制，保证版本严格一致。
#
# ⚠ 只复制 mysqldump / mysql 两个可执行文件 + 其依赖的私有库目录，
#   不整包拷贝，避免镜像膨胀。
# ---------------------------------------------------------------------------
FROM mysql:8.0 AS mysqlcli

FROM eclipse-temurin:17-jdk
WORKDIR /app

# MySQL 8 客户端二进制及其运行时依赖
COPY --from=mysqlcli /usr/bin/mysqldump /usr/bin/mysqldump
COPY --from=mysqlcli /usr/bin/mysql     /usr/bin/mysql
COPY --from=mysqlcli /usr/lib64/mysql/  /usr/lib64/mysql/
# 补齐 mysql:8.0（Oracle Linux 基底）二进制链接的系统库
RUN apt-get update && \
    apt-get install -y --no-install-recommends libncurses6 libtinfo6 zlib1g libssl3 && \
    rm -rf /var/lib/apt/lists/* && \
    echo "/usr/lib64/mysql" > /etc/ld.so.conf.d/mysql-client.conf && \
    ldconfig && \
    chmod 0755 /usr/bin/mysqldump /usr/bin/mysql && \
    # 构建期自检：工具不可用则直接让镜像构建失败，避免上线后才发现备份跑不动
    mysqldump --version && mysql --version

COPY --from=build /app/app.jar /app/app.jar

# 附件存储卷
VOLUME ["/data/attachments"]
# 备份存储卷（Phase10）。目录权限 0700 —— 备份包内含整库数据，
# 绝不允许同宿主机上的其他容器/用户读取。
RUN mkdir -p /data/issueflow/backups && chmod 0700 /data/issueflow/backups
VOLUME ["/data/issueflow/backups"]

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
