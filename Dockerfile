# 1. 빌드 단계
FROM gradle:7.2-jdk17 AS build

# 2. 빌드 환경 설정
WORKDIR /app
COPY . .

# 3. 프로젝트 빌드
RUN ./gradlew bootJar

# 4. 실행 단계
FROM openjdk:17-jdk-slim

# 5. 빌드된 JAR 파일을 복사
COPY --from=build /app/build/libs/*.jar /baroka.jar

# 6. 실행 명령 설정
ENTRYPOINT ["java", "-jar", "/baroka.jar"]

## docker build --platform linux/amd64 -t zxz4641/baroka:1.2 .