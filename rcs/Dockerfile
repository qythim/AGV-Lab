# ===== 第一階段：使用 JDK 21 編譯打包 Spring Boot JAR =====
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

# 先複製 Maven Wrapper 與 pom.xml 以利用 Docker 快取層
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# 修正 Windows CRLF 換行符並賦予執行權限，預先下載相依套件
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw dependency:go-offline -B

# 複製原始碼並打包（跳過測試加速建置）
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ===== 第二階段：使用輕量 JRE 21 執行階段 =====
FROM eclipse-temurin:21-jre
WORKDIR /app

# 建立地圖儲存與日誌目錄
RUN mkdir -p /app/map_storage /app/logs

# 從建置階段複製編譯完成的 JAR 檔
COPY --from=builder /build/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]