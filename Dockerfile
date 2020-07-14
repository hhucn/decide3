FROM clojure:openjdk-14-lein AS clj-build

RUN apt-get update &&\
    apt-get install -y curl apt-transport-https ca-certificates
RUN curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add - &&\
    curl -sL https://deb.nodesource.com/setup_10.x | bash - &&\
    echo "deb https://dl.yarnpkg.com/debian/ stable main" | tee /etc/apt/sources.list.d/yarn.list &&\
    apt-get update &&\
    apt-get install -y yarn 
RUN curl -O https://download.clojure.org/install/linux-install-1.10.1.536.sh &&\
    chmod +x linux-install-1.10.1.536.sh &&\
    ./linux-install-1.10.1.536.sh

COPY package.json project.clj deps.edn yarn.lock ./
RUN yarn install && lein deps

COPY . .
RUN lein uberjar

FROM openjdk:14-jdk-slim
COPY src/main/config/prod.edn /config/production.edn
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "decide.jar", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=85", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC"]
CMD ["-Dconfig=/config/production.edn", "-Dfulcro.logging=info"]

COPY --from=clj-build /tmp/target/decide.jar decide.jar