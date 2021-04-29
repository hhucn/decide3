FROM circleci/clojure:openjdk-16-tools-deps-buster-node AS clj-build

USER root
RUN curl -o- -L https://yarnpkg.com/install.sh | bash

WORKDIR /code
ENV REPO="/code/.m2/repository"

COPY package.json deps.edn yarn.lock ./
RUN yarn install --non-interactive --frozen-lockfile
RUN clj -Sdeps "{:mvn/local-repo \"$REPO\"}" -M:dev -e ":ok"

COPY . .
RUN echo "Compile CLJS..." && \
    clj -Sdeps "{:mvn/local-repo \"$REPO\"}" -M:dev -m "shadow.cljs.devtools.cli" release :main && \
    echo "Done. " && \
    echo "Compiling CLJ..." && \
    clj -Sdeps "{:mvn/local-repo \"$REPO\"}" -X:uberjar

FROM adoptopenjdk:16-jre
COPY src/main/config/prod.edn /config/production.edn
EXPOSE 8080
ENTRYPOINT ["java", "-Dconfig=/config/production.edn", "-Dfulcro.logging=info", "-jar", "decide.jar", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=85", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC"]

COPY --from=clj-build /code/decide.jar decide.jar