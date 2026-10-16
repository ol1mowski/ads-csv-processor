FROM node:24-alpine AS client
WORKDIR /client
COPY client/package.json client/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY client/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk AS server
WORKDIR /server
COPY server/ ./
COPY --from=client /client/dist ./src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -Dmaven.test.skip=true package \
    && cp target/ads-csv-processor-*.jar /server/app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin app \
    && mkdir -p /app /data \
    && chown app:app /data
WORKDIR /app
COPY --from=server /server/app.jar /app/app.jar
USER app
ENV APP_STORAGE_ROOT=/data
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
