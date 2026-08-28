# =============================================================================
# 線上聖誕老公公 API
#
# Cloud Run 是 scale-to-zero 的：全年多數時間沒有實例，每個進來的請求都可能觸發
# 冷啟動。上架首日更是尖峰——民眾同時湧入搶領，那時候多一秒都是體感。
#
# 因此這個映像檔用 AppCDS（Class Data Sharing）：建置時先跑一次應用程式到
# context refresh，把啟動路徑上載入的類別寫成共用封存檔，正式啟動時直接記憶體映射
# 進來。實測啟動時間從 8.4-9.0 秒降到 5.2-5.3 秒。
# =============================================================================

# --------------------------------------------------------------------- 建置
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只複製 pom 並抓依賴：原始碼改動時這一層仍能命中快取
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ------------------------------------------------------- 執行環境 + CDS 訓練
#
# 訓練與執行必須用「同一個」JVM：CDS 封存檔綁定 JVM 的建置版本，換一個基底映像檔
# 就會失效並靜靜地退回一般啟動。所以這裡不分兩個 stage。
FROM eclipse-temurin:21-jre-alpine

# 使用者要先建好，而且 /app 的所有權要先設定——後面產生的檔案才會直接屬於它。
# 若改用 `chown -R` 收尾，那一行會把整個 /app（200MB 以上）複寫成新的一層，
# 映像檔白白多出兩百多 MB。
RUN addgroup -S santa && adduser -S santa -G santa \
    && mkdir -p /app && chown santa:santa /app
USER santa
WORKDIR /app

COPY --from=build --chown=santa:santa /build/target/*.jar application.jar

# 解開成目錄結構再跑。Spring Boot 的 fat jar 用自訂 class loader，CDS 對它的覆蓋率
# 遠不如一般的 classpath；解開後封存檔能涵蓋的類別多得多。
RUN java -Djarmode=tools -jar application.jar extract --destination extracted \
    && rm application.jar

# 訓練跑一次。cds profile 關掉了資料庫、Flyway 與所有對外連線，
# spring.context.exit=onRefresh 讓它在 context 建好的瞬間就結束。
#
# 工作目錄與 jar 路徑必須和下面的 ENTRYPOINT 完全一致：CDS 把 classpath 記進封存檔，
# 對不上就整個作廢，而預設的 -Xshare:auto 會靜靜地退回一般啟動——看起來一切正常，
# 實際上什麼都沒省到。這個坑沒有任何錯誤訊息，只能靠 -Xshare:on 驗證。
WORKDIR /app/extracted
RUN java -XX:ArchiveClassesAtExit=/app/application.jsa \
         -Dspring.context.exit=onRefresh \
         -Dspring.profiles.active=cds \
         -jar application.jar \
    && test -f /app/application.jsa

EXPOSE 8080

# MaxRAMPercentage：容器記憶體是 512Mi，讓 JVM 依實際配額決定堆積大小，
#   而不是沿用它對主機記憶體的猜測。
# UseSerialGC：單核心的小容器上，平行 GC 的執行緒協調成本高於收益，
#   而且序列式 GC 的啟動成本最低——對 scale-to-zero 特別重要。
# SharedArchiveFile：載入上面訓練出來的封存檔。檔案損毀或 classpath 不符時，
#   -Xshare:auto（預設）會退回一般啟動，不會讓服務起不來。
ENTRYPOINT ["java", \
    "-XX:SharedArchiveFile=/app/application.jsa", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseSerialGC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "application.jar"]
