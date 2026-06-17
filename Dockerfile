# ============================================
# Stage 1: Build - Maven 编译 + 打包 fat jar
# ============================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

COPY src ./src
RUN mvn clean package -B -DskipTests

# ============================================
# Stage 2: Runtime - 最小 Java 17 运行环境
# ============================================
FROM eclipse-temurin:17-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends \
    # ripgrep 用于 grep_code 工具
    ripgrep \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 从 builder 复制 fat jar
COPY --from=builder /build/target/paicli-1.0-SNAPSHOT.jar app.jar

# 持久化数据目录
VOLUME /root/.paicli

# 默认启动 Runtime API 服务模式（后台守护）
# 交互模式用: docker run -it ... java -jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["serve", "--http", "--port", "8080"]

EXPOSE 8080
