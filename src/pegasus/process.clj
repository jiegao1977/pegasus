(ns pegasus.process
  "Two major crawler bits:
   - the pipeline - a crawl task from URL to saving a payload is a pipeline
   - the pipeline components - each operation in the pipeline - downloading
     the URL, extracting links, updating crawler state and so on.

   This namespace contains defintions and examples."
  (:require [clojure.core.async :as async]
            [clojure.repl :refer [pst]]
            [schema.core :as s]
            [taoensso.timbre :as timbre
             :refer (log  trace  debug  info  warn  error  fatal  report
                          logf tracef debugf infof warnf errorf fatalf reportf
                          spy get-env log-env)]))

(defprotocol PipelineComponentProtocol
  "A pipeline component protocol.
   A pipeline component is responsible for setting up state (creating
   directories and that sort of thing), being a member of the pipeline,
   and then cleaning up when the crawl is supposed to end.

   initialize - called with an existing config."

  ;; initialize is called when the crawl
  ;; starts.
  (initialize
    [this config])

  ;; run is called during the crawl when a new URL is visited
  (run
    [this obj config])

  ;; clean is called when the crawler is shutting down.
  (clean
    [this config]))

(defn add-transducer
  [in xf parallelism]
  (let [out (async/chan (async/buffer 2048)
                        identity
                        (fn [x]
                          (info x)
                          nil))]
    (async/pipeline-blocking parallelism out xf in)
    out))

(defn run-process
  [component process-schema in-chan parallelism crawl-config]
  ;; (add-transducer in-chan
  ;;                 (comp
  ;;                  (map
  ;;                   #(try
  ;;                      (merge %
  ;;                             {:input
  ;;                              (do (println component)
  ;;                                  (as-> % $
  ;;                                    (get $ :input)
  ;;                                    (s/validate $ process-schema)))})
  ;;                      (catch Exception e
  ;;                        (do (info component)
  ;;                            (error e)
  ;;                            (merge % {:input nil})))))
  ;;                  (filter :input))
  ;;                 parallelism)
  (println component))

(defn initialize-component-configs
  [pipeline orig-config]
  (reduce
   (fn [config [component _ _]]
     (let [cls   (get config component)]
       (initialize cls
                   config)))
   orig-config
   pipeline))

(defn initialize-pipeline
  "A pipeline contains kws - fn-map
  contains a map from the kws to implementations.
  The components (typically) read from a
  channel and write to a channel.
  The first component is fixed as the component
  that speaks to a queue.
  The last component is the writer"
  [config]
  (info (:pipeline config))
  (let [pipeline  (:pipeline config)

        init-chan (async/chan (async/buffer 1024))

        initialized-config (initialize-component-configs pipeline
                                                         config)
        
        _ (println initialized-config)

        final-out-chan (reduce
                        (fn [last-out-channel [component component-schema parallelism]]
                          (info :current-component component)

                          (let [component-cls (get config component)

                                component-obj (component-cls)]
                            ;; (run-process component-obj
                            ;;              component-schema
                            ;;              last-out-channel
                            ;;              parallelism
                            ;;              config)
                            component-obj))
                        init-chan
                        pipeline)]

    init-chan))
