(ns dl4clj.nn.conf.builders.multi-layer-builders
  (:require [dl4clj.nn.conf.builders.builders :as bb]
            [dl4clj.nn.conf.backprop-type :as backprop-t]
            [dl4clj.nn.conf.input-pre-processor :as pre-process]
            [dl4clj.nn.conf.constants :as constants])
  (:import [org.deeplearning4j.nn.conf NeuralNetConfiguration$Builder
            NeuralNetConfiguration$ListBuilder MultiLayerConfiguration$Builder]
           [org.deeplearning4j.nn.multilayer MultiLayerNetwork]))

(defn multi-layer-config-builder
  "creates a multi layer neural network configuration to be used within a multilayernetwork.

  params are:

  :backprop (boolean) whether to do backprop or not

  :backprop-type (keyword) the type of backprop, one of :standard or :truncated-bptt

  :damping-factor (double) damping factor used in backprop (not 100% sure on this)

  :input-pre-processors {int keyword} ie {0 {:zero-mean-pre-pre-processor opts}
                                          1 {:unit-variance-processor opts}}
  specifies the processors, these are used at each layer for doing things like
  ormalization and shaping of input. see input-pre-processor ns for details

  :input-type {:input-type opts}, where :input-type is one of:
   :convolutional, :convolutional-flat, :feed-forward, :recurrent

  :pretrain (boolean) Whether to do pre train or not

  :tbptt-back-length (int) When doing truncated BPTT: how many steps of backward should we do?
  Only applicable when doing backpropType(BackpropType.TruncatedBPTT)

  :tbptt-fwd-length (int) When doing truncated BPTT: how many steps of forward pass
  should we do before doing (truncated) backprop? Only applicable when doing TruncatedBPTT
  Typically tBPTTForwardLength parameter is same as the tBPTTBackwardLength parameter,
  but may be larger than it in some circumstances (but never smaller)
  Ideally your training data time series length should be divisible by this"

  ([]
   (multi-layer-config-builder (MultiLayerConfiguration$Builder.) {}))
  ([opts]
   (multi-layer-config-builder (MultiLayerConfiguration$Builder.) opts))
  ([^MultiLayerConfiguration$Builder multi-layer-config-b
    {:keys [backprop backprop-type damping-factor
            input-pre-processors input-type pretrain
            tbptt-back-length tbptt-fwd-length]
     ;; confs java.util.List<NeuralNetConfiguration>
     ;; confs is automaticaly set when you .build
     :or {}
     :as opts}]
   (if (contains? opts :backprop)
     (.backprop multi-layer-config-b backprop) multi-layer-config-b)
   (if (contains? opts :backprop-type)
     (.backpropType multi-layer-config-b
                    (constants/value-of {:backprop-type backprop-type}))
     multi-layer-config-b)
   ;; .build automaticaly takes care of this I believe
   #_(if (contains? opts :confs)
     (.confs multi-layer-config-b confs) multi-layer-config-b)
   (if (contains? opts :damping-factor)
     (.dampingFactor multi-layer-config-b damping-factor) multi-layer-config-b)
   (if (contains? opts :input-pre-processors)
     (.inputPreProcessors multi-layer-config-b
                          (pre-process/pre-processors input-pre-processors))
     multi-layer-config-b)
   (if (contains? opts :input-type)
     (.setInputType multi-layer-config-b (constants/input-types input-type))
     multi-layer-config-b)
   (if (contains? opts :pretrain)
     (.pretrain multi-layer-config-b pretrain) multi-layer-config-b)
   (if (contains? opts :tbptt-back-length)
     (.tBPTTBackwardLength multi-layer-config-b tbptt-back-length)
     multi-layer-config-b)
   (if (contains? opts :tbptt-fwd-length)
     (.tBPTTForwardLength multi-layer-config-b tbptt-fwd-length)
     multi-layer-config-b)
   multi-layer-config-b))

(defn list-builder
  "builds a list of layers to be used in a multi-layer configuration

  layers should take the form of {:layers {idx {:layer-type layer-opts}}}
  or {:layers {idx (layer-type-builder opts)}}
  (layer-type-builders are found at the bottom of the builders ns)

  :layers can also be a mix of param maps and function calls.  ie,
  {:layers {0 {:activation-layer {opts-keys opts-values}}
            1 (activation-layer-builder {opts-keys opts-values})}}

  see the builders ns for layer opts"
  [nn-conf-builder layers]
  (let [b (.list nn-conf-builder)
        max-idx (+ 1 (last (map first layers)))]
    (loop [idx 0
           result b]
      (cond (not= idx max-idx)
            (let [current-layer (get layers idx)]
              (if (seq? current-layer)
                ;; we are dealing with a config map that needs to go through builder multimethod
                (recur
                 (inc idx)
                 (.layer result idx (bb/builder current-layer)))
                ;; we have already been through the builder multimethod and just need to set the layer
                (recur
                 (inc idx)
                 (.layer result idx current-layer))))
            (= idx max-idx)
            result))))

(defn multi-layer-network
  [conf]
  (MultiLayerNetwork. conf))

(comment
;; this is working
  (-> (dl4clj.nn.conf.builders.nn-conf-builder/nn-conf-builder
       {:drop-out 2
        :backprop true
        :seed 123
        :global-activation-fn "CUBE" ;; wont overwrite the activation fns set at the layer level
        :layers {0 {:graves-lstm {:layer-name "first layer"
                                  :n-in 10
                                  :n-out 10
                                  :activation-fn "RELU"
                                  :epsilon 2.0}}
                 1 {:graves-lstm {:layer-name "genisys"
                                  :activation-fn "SOFTMAX"
                                  :n-in 10
                                  :n-out 20}}
                 2 (dl4clj.nn.conf.builders.builders/garves-lstm-layer-builder
                    {:n-in 100
                     :n-out 1000
                     ;; here activation-fn is being set by the :global-activation-fn
                     :layer-name "third layer"
                     :gradient-normalization :none })}})
      (multi-layer-config-builder {:backprop true
                                   :tbptt-back-length 10
                                   :tbptt-fwd-length 30
                                   :pretrain false})
      (.build)
      )
  ;; will need to add checks to make sure the ovlapping config maps for nn-conf and multi layer are the same
  ;; need error handling which determines if there are :layer and :layers keys and warns that :layer will be lost
  ;; that detection will also determine if the output of nn-conf-builder will be set to multi-layer-conf-builder or not
  )
