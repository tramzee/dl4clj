(ns dl4clj.constants
  (:require [clojure.string :as s]
            [dl4clj.utils :as u])
  (:import [org.deeplearning4j.nn.conf GradientNormalization LearningRatePolicy
            Updater BackpropType ConvolutionMode]
           [org.deeplearning4j.nn.conf.layers ConvolutionLayer$AlgoMode
            RBM$VisibleUnit RBM$HiddenUnit
            PoolingType]
           [org.deeplearning4j.nn.conf.inputs InputType]
           [org.deeplearning4j.nn.api OptimizationAlgorithm MaskState
            Layer$Type Layer$TrainingMode]
           [org.deeplearning4j.nn.weights WeightInit]
           [org.nd4j.linalg.activations Activation]
           [org.nd4j.linalg.lossfunctions LossFunctions LossFunctions$LossFunction]
           [org.nd4j.linalg.convolution Convolution$Type]
           [org.deeplearning4j.datasets.datavec RecordReaderMultiDataSetIterator$AlignmentMode
            SequenceRecordReaderDataSetIterator$AlignmentMode]))

;; not used anywhere yet
;; https://deeplearning4j.org/doc/org/deeplearning4j/nn/api/MaskState.html
;; https://deeplearning4j.org/doc/org/deeplearning4j/nn/api/Layer.Type.html
;; https://deeplearning4j.org/doc/org/deeplearning4j/nn/api/Layer.TrainingMode.html

;; datasets constants
;;https://deeplearning4j.org/doc/org/deeplearning4j/datasets/datavec/RecordReaderMultiDataSetIterator.AlignmentMode.html
;;https://deeplearning4j.org/doc/org/deeplearning4j/datasets/datavec/SequenceRecordReaderDataSetIterator.AlignmentMode.html
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; multi fn
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn constant-type
  [opts]
  (first (keys opts)))

(defmulti value-of constant-type)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; multi fn heavy lifting
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn constants
  [l-type k & {:keys [activation?
                      camel?]
               :or {activation? false
                    camel? false}}]
  (let [val (name k)]
    (if camel?
      (l-type (u/camelize val true))
      (cond activation?
            (cond (s/includes? val "-")
                  (l-type (s/upper-case (s/join (s/split val #"-"))))
                  :else
                  (l-type (s/upper-case val)))
            :else
            (l-type (s/replace (s/upper-case val) "-" "_"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; multi fn methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod value-of :activation-fn [opts]
  (constants #(Activation/valueOf %) (:activation-fn opts) :activation? true))

(defmethod value-of :gradient-normalization [opts]
  (constants #(GradientNormalization/valueOf %) (:gradient-normalization opts) :camel? true))

(defmethod value-of :learning-rate-policy [opts]
  (constants #(LearningRatePolicy/valueOf %) (:learning-rate-policy opts) :camel? true))

(defmethod value-of :updater [opts]
  (constants #(Updater/valueOf %) (:updater opts)))

(defmethod value-of :weight-init [opts]
  (constants #(WeightInit/valueOf %) (:weight-init opts)))

(defmethod value-of :loss-fn [opts]
  (constants #(LossFunctions$LossFunction/valueOf %) (:loss-fn opts)))

(defmethod value-of :hidden-unit [opts]
  (constants #(RBM$HiddenUnit/valueOf %) (:hidden-unit opts)))

(defmethod value-of :visible-unit [opts]
  (constants #(RBM$VisibleUnit/valueOf %) (:visible-unit opts)))

(defmethod value-of :convolution-mode [opts]
  (constants #(ConvolutionMode/valueOf %) (:convolution-mode opts) :camel? true))

(defmethod value-of :cudnn-algo-mode [opts]
  (constants #(ConvolutionLayer$AlgoMode/valueOf %) (:cudnn-algo-mode opts)))

(defmethod value-of :pool-type [opts]
  (constants #(PoolingType/valueOf %) (:pool-type opts)))

(defmethod value-of :backprop-type [opts]
  (if (= (:backprop-type opts) :truncated-bptt)
    (BackpropType/valueOf "TruncatedBPTT")
    (BackpropType/valueOf "Standard")))

(defmethod value-of :optimization-algorithm [opts]
  (constants #(OptimizationAlgorithm/valueOf %) (:optimization-algorithm opts)))

(defmethod value-of :mask-state [opts]
  (constants #(MaskState/valueOf %) (:mask-state opts) :camel? true))

(defmethod value-of :layer-type [opts]
  (constants #(Layer$Type/valueOf %) (:layer-type opts)))

(defmethod value-of :layer-training-mode [opts]
  (constants #(Layer$TrainingMode/valueOf %) (:layer-training-mode opts)))

(defmethod value-of :seq-alignment-mode [opts]
  (constants #(SequenceRecordReaderDataSetIterator$AlignmentMode/valueOf %)
             (:seq-alignment-mode opts)
             :activation true))

(defmethod value-of :multi-alignment-mode [opts]
  (constants #(RecordReaderMultiDataSetIterator$AlignmentMode/valueOf %)
             (:multi-alignment-mode opts)
             :activation true))

(comment
  " for seq alignment mode
EQUAL_LENGTH: Default. Assume that label and input time series are of equal length, and all examples are of the same length
ALIGN_START: Align the label/input time series at the first time step, and zero pad either the labels or the input at the end
ALIGN_END: Align the label/input at the last time step, zero padding either the input or the labels as required
Note 1: When the time series for each example are of different lengths, the shorter time series will be padded to the length of the longest time series.
Note 2: When ALIGN_START or ALIGN_END are used, the DataSet masking functionality is used. Thus, the returned DataSets will have the input and mask arrays set. These mask arrays identify whether an input/label is actually present, or whether the value is merely masked."

  " for multi alignment mode
When dealing with time series data of different lengths, how should we align the input/labels time series? For equal length: use EQUAL_LENGTH For sequence classification: use ALIGN_END"
  )

(defn input-types
  [opts]
  (let [{typez :type
         height :height
         width :width
         depth :depth
         size :size} (:input-type opts)]
    (cond
      (= typez :convolutional)
      (InputType/convolutional height width depth)
      (= typez :convolutional-flat)
      (InputType/convolutionalFlat height width depth)
      (= typez :feed-forward)
      (InputType/feedForward size)
      (= typez :recurrent)
      (InputType/recurrent size))))
