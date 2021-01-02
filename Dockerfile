FROM clojure:openjdk-11-tools-deps-slim-buster AS build

WORKDIR /io.hinman.home-fixtures
COPY deps.edn .
RUN clj -M:pack mach.pack.alpha.skinny --no-project
COPY src src
RUN clj -M:pack mach.pack.alpha.skinny --no-libs

FROM openjdk:11-jre-slim-buster

WORKDIR /io.hinman.home-fixtures
COPY --from=build /io.hinman.home-fixtures/target .
ENTRYPOINT ["java", "-cp", "app.jar:lib/*", "clojure.main", "-m", "io.hinman.home-fixtures.process", "80"]
CMD ["https://ics.ecal.com/ecal-sub/5fa672c994daeebe768b456f/Arsenal%20FC.ics"]
EXPOSE 80
