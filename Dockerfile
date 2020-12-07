FROM clojure:tools-deps AS clj-build

RUN apt-get update &&\
    apt-get install -y curl apt-transport-https ca-certificates
RUN curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add - &&\
    curl -sL https://deb.nodesource.com/setup_10.x | bash - &&\
    echo "deb https://dl.yarnpkg.com/debian/ stable main" | tee /etc/apt/sources.list.d/yarn.list &&\
    apt update &&\
    apt install -y yarn

COPY package.json deps.edn yarn.lock ./
RUN yarn install --non-interactive --frozen-lockfile

COPY . .
RUN npx shadow-cljs release :main && \
    clojure -Spom && clojure -X:depstar uberjar :aot true :jar decide.jar :main-class decide.server-main

FROM openjdk:14-jdk-slim
COPY src/main/config/prod.edn /config/production.edn
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "decide.jar", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=85", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC"]
CMD ["-Dconfig=/config/production.edn", "-Dfulcro.logging=info"]

COPY --from=clj-build /tmp/decide.jar decide.jar