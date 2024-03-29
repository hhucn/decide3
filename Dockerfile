FROM clojure:openjdk-17-tools-deps-buster AS clj-build

USER root
RUN curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - && \
    apt-get -y update && \
    apt-get -y dist-upgrade && \
    apt-get install -y nodejs && \
    npm install --global yarn

WORKDIR /code
ENV REPO="/code/.m2/repository"

COPY package.json deps.edn yarn.lock ./
RUN yarn install --non-interactive --frozen-lockfile

COPY . .
RUN echo "Compile CLJS..." && \
    clj -Sdeps "{:mvn/local-repo \"$REPO\"}" -M:dev -m "shadow.cljs.devtools.cli" release :main && \
    echo "Done. " && \
    echo "Compiling CLJ..." && \
    clj -Sdeps "{:mvn/local-repo \"$REPO\"}" -T:build uber

FROM openjdk:17-slim
COPY src/main/config/prod.edn /config/production.edn
EXPOSE 8080
ENTRYPOINT ["java",  "-Dlog4j2.formatMsgNoLookups=true", "-Dconfig=/config/production.edn", "-Dfulcro.logging=info", "-jar", "decide.jar", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=85", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC"]

COPY --from=clj-build /code/target/decide.jar decide.jar
