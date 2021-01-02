(ns io.hinman.home-fixtures.process
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.logging :as logging]
            [org.httpkit.server :as server]
            [tick.ical :as ical])
  (:import [java.util.concurrent TimeUnit])
  (:gen-class))

(def ^:private unavailable-response {:status 503})

(def ^:private response (atom unavailable-response))

(defn- start-server [port]
  (server/run-server (fn [_]
                       @response)
                     {:port port}))

(defn- home-fixture-event? [event]
  (-> event
      (ical/property-value "SUMMARY")
      (string/ends-with? "(H)")))

(defn- calendar [url]
  (-> url
      io/reader
      ical/parse-ical
      first
      (ical/remove-events (complement home-fixture-event?))))

(defn- start-refresher [calendar-url]
  (let [stop-chan (async/chan)
        refresher-chan (async/thread
                         (loop []
                           (reset! response
                                   (try
                                     {:status 200
                                      :headers {"Content-Type" "text/calendar"}
                                      :body (-> calendar-url
                                                calendar
                                                ical/print-object
                                                with-out-str)}
                                     (catch Throwable t
                                       (logging/info t "Failed to refresh calendar")
                                       unavailable-response)))
                           (async/alt!!
                             stop-chan nil
                             (async/timeout (.toMillis TimeUnit/HOURS 1)) (recur)
                             :priority true)))]
    (fn []
      (async/close! stop-chan)
      (async/<!! refresher-chan))))

(defn- start [port calendar-url]
  (logging/info "Starting server...")
  (let [stop-server (start-server port)]
    (logging/info "Server started")
    (logging/info "Starting refresher...")
    (let [stop-refresher (start-refresher calendar-url)]
      (logging/info "Refresher started")
      (fn []
        (logging/info "Stopping refresher...")
        (stop-refresher)
        (logging/info "Refresher stopped")
        (logging/info "Stopping server...")
        (stop-server)
        (logging/info "Server stopped")))))

(defn -main [port calendar-url]
  (let [stop (start (Integer/parseInt port) calendar-url)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. stop))))

(comment
  (def stop (start 8080 "https://ics.ecal.com/ecal-sub/5fa672c994daeebe768b456f/Arsenal%20FC.ics"))
  (stop))
