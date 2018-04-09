(ns dl4clj.datasets.iterators
  (:import [org.deeplearning4j.datasets.datavec RecordReaderDataSetIterator
            RecordReaderMultiDataSetIterator$Builder RecordReaderMultiDataSetIterator
            SequenceRecordReaderDataSetIterator]
           [org.deeplearning4j.datasets.iterator
            DoublesDataSetIterator FloatsDataSetIterator INDArrayDataSetIterator
            AsyncDataSetIterator AsyncMultiDataSetIterator CombinedPreProcessor
            ;; CombinedPreProcessor$Builder CurvesDataSetIterator IteratorDataSetIterator
            CombinedPreProcessor$Builder IteratorDataSetIterator
            IteratorMultiDataSetIterator MovingWindowBaseDataSetIterator
            MultipleEpochsIterator ReconstructionDataSetIterator SamplingDataSetIterator
            ExistingDataSetIterator]
           [org.deeplearning4j.datasets.iterator.impl MultiDataSetIteratorAdapter
            ListDataSetIterator SingletonMultiDataSetIterator]
           [org.deeplearning4j.datasets.iterator.impl
            CifarDataSetIterator IrisDataSetIterator LFWDataSetIterator
            MnistDataSetIterator RawMnistDataSetIterator]
           [org.deeplearning4j.spark.iterator
            PathSparkDataSetIterator
            PathSparkMultiDataSetIterator
            PortableDataStreamDataSetIterator
            PortableDataStreamMultiDataSetIterator]
           [java.util Random])
  (:require [dl4clj.constants :refer [value-of]]
            [dl4clj.berkeley :refer [new-pair]]
            [dl4clj.helpers :refer :all]
            [dl4clj.utils :refer [contains-many? generic-dispatching-fn builder-fn
                                  obj-or-code?]]
            [clojure.core.match :refer [match]]
            [nd4clj.linalg.factory.nd4j :refer [vec-or-matrix->indarray]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; multimethod
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti iterator
  "Multimethod that builds a dataset iterator based on the supplied type and opts"
  generic-dispatching-fn)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; record reader dataset iterator mulimethods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod iterator :rr-dataset-iter [opts]
  (let [config (:rr-dataset-iter opts)
        {rr :record-reader
         batch-size :batch-size
         label-idx :label-idx
         n-labels :n-possible-labels
         l-idx-from :label-idx-from
         l-idx-to :label-idx-to
         regression? :regression?
         max-n-batches :max-num-batches
         converter :writeable-converter} config]
    (match [config]
           [{:writeable-converter _ :batch-size _ :label-idx-from _
             :label-idx-to _ :n-possible-labels _ :max-num-batches _
             :regression? _ :record-reader _}]
           `(RecordReaderDataSetIterator. ~rr ~converter ~batch-size ~l-idx-from
                                          ~l-idx-to ~n-labels ~max-n-batches
                                          ~regression?)
           [{:writeable-converter _ :batch-size _ :label-idx _
             :n-possible-labels _ :max-num-batches _ :regression? _
             :record-reader _}]
           `(RecordReaderDataSetIterator. ~rr ~converter ~batch-size ~label-idx
                                          ~n-labels ~max-n-batches ~regression?)
           [{:writeable-converter _ :batch-size _ :label-idx _
             :n-possible-labels _  :regression? _ :record-reader _}]
           `(RecordReaderDataSetIterator. ~rr ~converter ~batch-size ~label-idx
                                          ~n-labels ~regression?)
           [{:record-reader _ :batch-size _ :label-idx-from _
             :label-idx-to _ :regression? _}]
           `(RecordReaderDataSetIterator.
             ~rr ~batch-size ~l-idx-from ~l-idx-to ~regression?)
           [{:record-reader _ :batch-size _ :label-idx _
             :n-possible-labels _ :max-num-batches _}]
           `(RecordReaderDataSetIterator.
             ~rr ~batch-size ~label-idx ~n-labels ~max-n-batches)
           [{:writeable-converter _ :batch-size _ :label-idx _
             :n-possible-labels _  :record-reader _}]
           `(RecordReaderDataSetIterator. ~rr ~converter ~batch-size ~label-idx ~n-labels)
           [{:record-reader _ :batch-size _ :label-idx _ :n-possible-labels _}]
           `(RecordReaderDataSetIterator.
             ~rr ~batch-size ~label-idx ~n-labels)
           [{:writeable-converter _ :batch-size _ :record-reader _}]
           `(RecordReaderDataSetIterator. ~rr ~converter ~batch-size)
           [{:batch-size _ :record-reader _}]
           `(RecordReaderDataSetIterator. ~rr ~batch-size))))

(defmethod iterator :seq-rr-dataset-iter [opts]
  (let [config (:seq-rr-dataset-iter opts)
        {rr :record-reader
         m-batch-size :mini-batch-size
         n-labels :n-possible-labels
         label-idx :label-idx
         regression? :regression?
         labels-reader :labels-reader
         features-reader :features-reader
         alignment :alignment-mode} config]
    (match [config]
           [{:labels-reader _ :features-reader _ :mini-batch-size _
             :n-possible-labels _ :regression? _ :alignment-mode _}]
           `(SequenceRecordReaderDataSetIterator.
             ~features-reader ~labels-reader ~m-batch-size ~n-labels ~regression?
             ~(value-of-helper :seq-alignment-mode alignment))
           [{:labels-reader _ :features-reader _ :mini-batch-size _
             :n-possible-labels _ :regression? _}]
           `(SequenceRecordReaderDataSetIterator.
             ~features-reader ~labels-reader ~m-batch-size ~n-labels ~regression?)
           [{:record-reader _ :mini-batch-size _ :n-possible-labels _
             :label-idx _ :regression? _}]
           `(SequenceRecordReaderDataSetIterator.
             ~rr ~m-batch-size ~n-labels ~label-idx ~regression?)
           [{:labels-reader _ :features-reader _ :mini-batch-size _ :n-possible-labels _}]
           `(SequenceRecordReaderDataSetIterator.
             ~features-reader ~labels-reader ~m-batch-size ~n-labels)
           [{:record-reader _ :mini-batch-size _ :n-possible-labels _ :label-idx _}]
           `(SequenceRecordReaderDataSetIterator.
             ~rr ~m-batch-size ~n-labels ~label-idx))))

(defmethod iterator :multi-dataset-iter [opts]
  (assert (integer? (:batch-size (:multi-dataset-iter opts)))
          "you must supply batch-size and it must be an integer")
  (let [config (:multi-dataset-iter opts)
        {add-input :add-input
         add-input-hot :add-input-one-hot
         add-output :add-output
         add-output-hot :add-output-one-hot
         add-reader :add-reader
         add-seq-reader :add-seq-reader
         alignment :alignment-mode
         batch-size :batch-size} config
        {reader-name :reader-name
         first-column :first-column
         last-column :last-column} add-input
        {hot-reader-name :reader-name
         hot-column :column
         hot-num-classes :n-classes} add-input-hot
        {output-reader-name :reader-name
         output-first-column :first-column
         output-last-column :last-column} add-output
        {hot-output-reader-name :reader-name
         hot-output-column :column
         hot-output-n-classes :n-classes} add-output-hot
        {record-reader-name :reader-name
         rr :record-reader} add-reader
        {seq-reader-name :reader-name
         seq-rr :record-reader} add-seq-reader
        method-map {:input '.addInput
                    :input-one-hot '.addInputOneHot
                    :output '.addOutput
                    :output-one-hot '.addOutputOneHot
                    :reader '.addReader
                    :seq-reader '.addSequenceReader
                    :alignment '.sequenceAlignmentMode}
        updated-opts {:alignment (if alignment (value-of-helper :multi-alignment-mode
                                                                alignment))
                      :reader (if add-reader [record-reader-name rr])
                      :seq-reader (if add-seq-reader [seq-reader-name seq-rr])
                      :output-one-hot (if add-output-hot [hot-output-reader-name
                                                          hot-output-column
                                                          hot-output-n-classes])
                      :output (if add-output [output-reader-name
                                              output-first-column
                                              output-last-column])
                      :input-one-hot (if add-input-hot [hot-reader-name
                                                        hot-column
                                                        hot-num-classes])
                      :input (if add-input [reader-name first-column last-column])}
        opts* (into {} (filter val updated-opts))
        b `(RecordReaderMultiDataSetIterator$Builder. ~batch-size)]
    `(.build
      ~(builder-fn b method-map opts*))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; dataset iterator mulimethods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod iterator :doubles-dataset-iter [opts]
  (let [config (:doubles-dataset-iter opts)
        {features :features
         labels :labels
         batch-size :batch-size} config]
    `(DoublesDataSetIterator. [(new-pair :p1 (double-array ~features)
                                         :p2 (double-array ~labels))]
                              ~batch-size)))

(defmethod iterator :floats-dataset-iter [opts]
  (let [config (:floats-dataset-iter opts)
        {features :features
         labels :labels
         batch-size :batch-size} config]
    `(FloatsDataSetIterator. [(new-pair :p1 (float-array ~features)
                                        :p2 (float-array ~labels))]
                             ~batch-size)))

(defmethod iterator :INDArray-dataset-iter [opts]
  (let [config (:INDArray-dataset-iter opts)
        {features :features
         labels :labels
         batch-size :batch-size} config]
    `(INDArrayDataSetIterator. [(new-pair :p1 (vec-or-matrix->indarray ~features)
                                          :p2 (vec-or-matrix->indarray ~labels))]
                               ~batch-size)))

(defmethod iterator :iterator-multi-dataset-iter [opts]
  (let [config (:iterator-multi-dataset-iter opts)
        {iter :multi-dataset-iter
         batch-size :batch-size} config]
    `(IteratorMultiDataSetIterator. ~iter ~batch-size)))

(defmethod iterator :iterator-dataset-iter [opts]
  (let [config (:iterator-dataset-iter opts)
        {iter :iter
         batch-size :batch-size} config]
    `(IteratorDataSetIterator. ~iter ~batch-size)))

(defmethod iterator :async-multi-dataset-iter [opts]
  (let [config (:async-multi-dataset-iter opts)
        {iter :multi-dataset-iter
         que-l :que-length} config]
    `(AsyncMultiDataSetIterator. ~iter ~que-l)))

(defmethod iterator :moving-window-base-dataset-iter [opts]
  (let [config (:moving-window-base-dataset-iter opts)
        {batch :batch-size
         n-examples :n-examples
         data :dataset
         window-rows :window-rows
         window-columns :window-columns} config]
    `(MovingWindowBaseDataSetIterator. ~batch ~n-examples ~data ~window-rows ~window-columns)))

(defmethod iterator :multiple-epochs-iter [opts]
  (let [config (:multiple-epochs-iter opts)
        {iter :iter
         q-size :que-size
         t-iterations :total-iterations
         n-epochs :n-epochs
         ds :dataset} config]
    (match [config]
           [{:n-epochs _ :iter _ :que-size _}]
           `(MultipleEpochsIterator. ~n-epochs ~iter ~q-size)
           [{:iter _ :que-size _ :total-iterations _}]
           `(MultipleEpochsIterator. ~iter ~q-size ~t-iterations)
           [{:n-epochs _ :iter _}]
           `(MultipleEpochsIterator. ~n-epochs ~iter)
           [{:n-epochs _ :dataset _}]
           `(MultipleEpochsIterator. ~n-epochs ~ds)
           [{:iter _ :total-iterations _}]
           `(MultipleEpochsIterator. ~iter ~t-iterations))))

(defmethod iterator :reconstruction-dataset-iter [opts]
  (let [config (:reconstruction-dataset-iter opts)
        iter (:iter config)]
    `(ReconstructionDataSetIterator. ~iter)))

(defmethod iterator :sampling-dataset-iter [opts]
  (let [config (:sampling-dataset-iter opts)
        {ds :sampling-source
         batch-size :batch-size
         n-samples :total-n-samples} config]
    `(SamplingDataSetIterator. ~ds ~batch-size ~n-samples)))

(defmethod iterator :existing-dataset-iter [opts]
  (let [config (:existing-dataset-iter opts)
        {iterable :dataset
         n-examples :total-examples
         n-features :n-features
         n-labels :n-labels
         labels :labels
         ds-iter :iter} config]
    (match [config]
           [{:dataset _ :total-examples _ :n-features _ :n-labels _}]
           `(ExistingDataSetIterator. ~iterable ~n-examples ~n-features ~n-labels)
           [{:dataset _ :labels _}]
           `(ExistingDataSetIterator. ~iterable ~labels)
           [{:iter _ :labels _}]
           `(ExistingDataSetIterator. ~ds-iter ~labels)
           [{:iter _}]
           `(ExistingDataSetIterator. ~ds-iter)
           [{:dataset _}]
           `(ExistingDataSetIterator. ~iterable))))

(defmethod iterator :async-dataset-iter [opts]
  (let [config (:async-dataset-iter opts)
        {ds-iter :iter
         que-size :que-size
         que :que} config]
    (match [config]
           [{:iter _ :que _ :que-size _}]
           `(AsyncDataSetIterator. ~ds-iter ~que-size ~que)
           [{:iter _ :que-size _}]
           `(AsyncDataSetIterator. ~ds-iter ~que-size)
           [{:iter _}]
           `(AsyncDataSetIterator. ~ds-iter))))

(defmethod iterator :ds-iter-to-multi-ds-iter [opts]
  (let [conf (:ds-iter-to-multi-ds-iter opts)
        iter (:iter conf)]
    `(MultiDataSetIteratorAdapter. ~iter)))

(defmethod iterator :list-ds-iter [opts]
  (let [conf (:list-ds-iter opts)
        {ds :dataset
         batch-size :batch-size} conf]
    (if (contains? conf :batch-size)
      `(ListDataSetIterator. ~ds ~batch-size)
      `(ListDataSetIterator. ~ds))))

(defmethod iterator :multi-ds-to-multi-ds-iter [opts]
  (let [conf (:multi-ds-to-multi-ds-iter opts)
        mds (:multi-dataset conf)]
    `(SingletonMultiDataSetIterator. ~mds)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; default dataset iterator mulimethods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_(defmethod iterator :curves-dataset-iter [opts]
  (let [config (:curves-dataset-iter opts)
        {batch :batch-size
         n-examples :n-examples} config]
    `(CurvesDataSetIterator. ~batch ~n-examples)))

(defmethod iterator :cifar-dataset-iter [opts]
  (let [config (:cifar-dataset-iter opts)
        {batch-size :batch-size
         n-examples :n-examples
         img-dims :img-dims
         train? :train?
         use-special-pre-process-cifar? :use-special-pre-process-cifar?
         n-possible-labels :n-possible-labels
         img-transform :img-transform} config
        img `(int-array ~img-dims)]
    (match [config]
           [{:batch-size _ :n-examples _ :img-dims _ :n-possible-labels _
             :img-transform _ :use-special-pre-process-cifar? _ :train? _}]
           `(CifarDataSetIterator. ~batch-size ~n-examples ~img ~n-possible-labels
                                   ~img-transform ~use-special-pre-process-cifar? ~train?)
           [{:batch-size _ :n-examples _ :img-dims _
             :use-special-pre-process-cifar? _ :train? _}]
           `(CifarDataSetIterator. ~batch-size ~n-examples ~img
                                   ~use-special-pre-process-cifar? ~train?)
           [{:batch-size _ :n-examples _ :img-dims _ :train? _}]
           `(CifarDataSetIterator. ~batch-size ~n-examples ~img ~train?)
           [{:batch-size _ :n-examples _ :img-dims _}]
           `(CifarDataSetIterator. ~batch-size ~n-examples ~img)
           [{:batch-size _ :n-examples _ :train? _}]
           `(CifarDataSetIterator. ~batch-size ~n-examples ~train?)
           [{:batch-size _ :img-dims _}]
           `(CifarDataSetIterator. ~batch-size ~img)
           [{:batch-size _ :n-examples _}]
           `(CifarDataSetIterator. ~batch-size ~n-examples))))

(defmethod iterator :iris-dataset-iter [opts]
  (let [config (:iris-dataset-iter opts)
        {batch-size :batch-size
         n-examples :n-examples} config]
    `(IrisDataSetIterator. ~batch-size ~n-examples)))

(defmethod iterator :lfw-dataset-iter [opts]
  (let [config (:lfw-dataset-iter opts)
        {img-dims :img-dims
         batch-size :batch-size
         n-examples :n-examples
         use-subset? :use-subset?
         train? :train?
         split-train-test :split-train-test
         n-labels :n-labels
         seed :seed
         label-generator :label-generator
         image-transform :image-transform} config
        img `(int-array ~img-dims)
        rng (if (contains? config :seed)
              `(new Random ~seed)
              `(new Random 123))]
    (match [config]
           [{:batch-size _ :n-examples _ :img-dims _ :n-labels _
             :use-subset? _ :label-generator _ :train? _ :split-train-test _
             :seed _ :image-transform _}]
           `(LFWDataSetIterator. ~batch-size ~n-examples ~img ~n-labels ~use-subset?
                                 ~label-generator ~train? ~split-train-test ~image-transform
                                 ~rng)
           [{:batch-size _ :n-examples _ :img-dims _ :n-labels _
             :use-subset? _ :label-generator _ :train? _ :split-train-test _
             :seed _}]
           `(LFWDataSetIterator. ~batch-size ~n-examples ~img ~n-labels ~use-subset?
                                 ~label-generator ~train? ~split-train-test ~rng)
           [{:batch-size _ :n-examples _ :img-dims _ :n-labels _
             :use-subset? _ :train? _ :split-train-test _ :seed _}]
           `(LFWDataSetIterator. ~batch-size ~n-examples ~img ~n-labels ~use-subset?
                                 ~train? ~split-train-test ~rng)
           [{:batch-size _ :n-examples _  :n-labels _ :train? _ :split-train-test _}]
           `(LFWDataSetIterator. ~batch-size ~n-examples ~n-labels ~train? ~split-train-test)
           [{:batch-size _ :n-examples _ :img-dims _  :train? _ :split-train-test _}]
           `(LFWDataSetIterator. ~batch-size ~n-examples ~img ~train? ~split-train-test)
           [{:batch-size _ :n-examples _ :img-dims _ }]
           `(LFWDataSetIterator. ~batch-size ~n-examples ~img)
           [{:batch-size _ :use-subset? _ :img-dims _ }]
           `(LFWDataSetIterator. ~batch-size ~img ~use-subset?)
           [{:batch-size _ :n-examples _}]
           `(LFWDataSetIterator. ~batch-size ~n-examples)
           [{:img-dims _ }]
           `(LFWDataSetIterator. ~img))))

(defmethod iterator :mnist-dataset-iter [opts]
  (let [config (:mnist-dataset-iter opts)
        {batch-size :batch-size
         train? :train?
         seed :seed
         n-examples :n-examples
         binarize? :binarize?
         shuffle? :shuffle?
         batch :batch} config]
    (match [config]
           [{:batch _ :n-examples _ :binarize? _
             :train? _ :shuffle? _ :seed _}]
           `(MnistDataSetIterator. ~batch ~n-examples ~binarize? ~train?
                                   ~shuffle? ~(long seed))
           [{:batch-size _ :train? _ :seed _}]
           `(MnistDataSetIterator. ~batch-size ~train? ~(int seed))
           [{:batch _ :n-examples _ :binarize? _}]
           `(MnistDataSetIterator. ~batch ~n-examples ~binarize?)
           [{:batch _ :n-examples _}]
           `(MnistDataSetIterator. ~batch ~n-examples))))

(defmethod iterator :raw-mnist-dataset-iter [opts]
  (let [config (:raw-mnist-dataset-iter opts)
        {batch :batch
         n-examples :n-examples} config]
    `(RawMnistDataSetIterator. ~batch ~n-examples)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; spark dataset iterator mulimethods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; going to update once I get to updating spark stuff

(defmethod iterator :path-to-ds [opts]
  (let [config (:path-to-ds opts)
        {str-paths :string-paths
         iter :iter} config]
    (if str-paths
      `(PathSparkDataSetIterator. ~str-paths)
      `(PathSparkDataSetIterator. ~iter))))

(defmethod iterator :path-to-multi-ds [opts]
  (let [config (:path-to-multi-ds opts)
        {str-paths :string-paths
         iter :iter} config]
    (if str-paths
      `(PathSparkMultiDataSetIterator. ~str-paths)
      `(PathSparkMultiDataSetIterator. ~iter))))

(defmethod iterator :portable-ds-stream [opts]
  (let [config (:portable-ds-stream opts)
        {streams :streams
         iter :iter} config]
    (if streams
      `(PortableDataStreamDataSetIterator. ~streams)
      `(PortableDataStreamDataSetIterator. ~iter))))

(defmethod iterator :portable-multi-ds-stream [opts]
  (let [config (:portable-multi-ds-stream opts)
        {streams :streams
         iter :iter} config]
    (if streams
      `(PortableDataStreamMultiDataSetIterator. ~streams)
      `(PortableDataStreamMultiDataSetIterator. ~iter))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; record reader dataset iterators user facing fns
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn new-record-reader-dataset-iterator
  "creates a new record reader dataset iterator by calling its constructor
  with the supplied args.  args are:

  :record-reader (record-reader) a record reader, see datavec.api.records.readers

  :as-code? (boolean), return java object or code for creating it

  :batch-size (int) the batch size

  :label-idx (int) the index of the labels in a dataset

  :n-possible-labels (int) the number of possible labels

  :label-idx-from (int) starting column for range of columns containing labels in the dataset

  :label-idx-to (int) ending column for range of columns containing labels in the dataset

  :regression? (boolean) are we dealing with a regression or classification problem

  :max-num-batches (int) the maximum number of batches the iterator should go through

  :writeable-converter (writable), converts a writable to another data type
   - need to have a central source of their creation
     - https://deeplearning4j.org/datavecdoc/org/datavec/api/writable/Writable.html
     - the classes which implement this interface
   - opts are new-double-writable-converter, new-float-writable-converter
     new-label-writer-converter, new-self-writable-converter
   - see: dl4clj.datavec.api.io

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/datavec/RecordReaderDataSetIterator.html"
  [& {:keys [record-reader batch-size label-idx n-possible-labels
             label-idx-from label-idx-to regression? max-num-batches
             writeable-converter as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:rr-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-seq-record-reader-dataset-iterator
  "creates a new sequence record reader dataset iterator by calling its constructor
  with the supplied args.  args are:

  :record-reader (sequence-record-reader) a record reader, see datavec.api.records.readers

  :as-code? (boolean), return java object or code for creating it

  :mini-batch-size (int) the mini batch size

  :n-possible-labels (int) the number of possible labels

  :label-idx (int) the index of the labels in a dataset

  :regression? (boolean) are we dealing with a regression or classification problem

  :labels-reader (record-reader) a record reader specificaly for labels, see datavec.api.records.readers

  :features-reader (record-reader) a record reader specificaly for features, see datavec.api.records.readers

  :alignment-mode (keyword), one of :equal-length, :align-start, :align-end
   -see https://deeplearning4j.org/doc/org/deeplearning4j/datasets/datavec/SequenceRecordReaderDataSetIterator.AlignmentMode.html

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/datavec/SequenceRecordReaderDataSetIterator.html"
  [& {:keys [record-reader mini-batch-size n-possible-labels label-idx regression?
             labels-reader features-reader alignment-mode as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:seq-rr-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-record-reader-multi-dataset-iterator
  ;; update after refactor of multi method
  "creates a new record reader multi dataset iterator by calling its builder with
  the supplied args.  args are:

  :alignment-mode (keyword),  one of :equal-length, :align-start, :align-end
   -see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/datavec/RecordReaderMultiDataSetIterator.AlignmentMode.html

  :batch-size (int), size of the batchs the iterator uses for a single run

  :add-seq-reader (map) {:reader-name (str) :record-reader (record-reader)}, a sequence record reader
   -see: datavec.api.records.readers

  :add-reader (map) {:reader-name (str) :record-reader (record-reader)}, a record reader
   -see: datavec.api.records.readers

  :add-output-one-hot (map) {:reader-name (str) :column (int) :n-classes (int)}

  :add-input-one-hot (map) {:reader-name (str) :column (int) :n-classes (int)}

  :add-input (map) {:reader-name (str) :first-column (int) :last-column (int)}

  :add-output (map) {:reader-name (str) :first-column (int) :last-column (int)}

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/datavec/RecordReaderMultiDataSetIterator.Builder.html"
  [& {:keys [alignment-mode batch-size add-seq-reader
             add-reader add-output-one-hot add-output
             add-input-one-hot add-input as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:multi-dataset-iter opts})]
    (obj-or-code? as-code? code)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; dataset iterators user facing fns
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn new-async-dataset-iterator
  "AsyncDataSetIterator takes an existing DataFmulSetIterator and loads one or more
  DataSet objects from it using a separate thread. For data sets where
  (next! some-iterator) is long running (limited by disk read or processing time for example)
  this may improve performance by loading the next DataSet asynchronously
  (i.e., while training is continuing on the previous DataSet).

  Obviously this may use additional memory.
  Note however that due to asynchronous loading of data, (next! iter n) is not supported.

  :as-code? (boolean), return java object or code for creating it

  :iter (ds-iterator), a dataset iterator
   - see: dl4clj.datasets.iterators (this ns)

  :que-size (int), the size of the que

  :que (blocking-que), the que containing the dataset

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/AsyncDataSetIterator.html"
  [& {:keys [iter que-size que as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:async-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-existing-dataset-iterator
  "This wrapper provides DataSetIterator interface to existing datasets or dataset iterators

  :dataset (iterable), an iterable object, some dataset

  :as-code? (boolean), return java object or code for creating it

  :total-examples (int), the total number of examples

  :n-features (int), the total number of features in the dataset

  :n-labels (int), the number of labels in a dataset

  :labels (list), a list of labels as strings

  :iter (iterator), a dataset iterator
   - see: dl4clj.datasets.iterators (this ns)

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/ExistingDataSetIterator.html"
  [& {:keys [dataset total-examples n-features n-labels labels iter as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:existing-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-sampling-dataset-iterator
  "A wrapper for a dataset to sample from.
  This will randomly sample from the given dataset.

  :sampling-source (dataset), the dataset to sample from

  :as-code? (boolean), return java object or code for creating it

  :batch-size (int), the batch size

  :total-n-samples (int), the total number of desired samples from the dataset

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/SamplingDataSetIterator.html"
  [& {:keys [sampling-source batch-size total-n-samples as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:sampling-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-reconstruction-dataset-iterator
  "Wraps a dataset iterator, setting the first (feature matrix) as the labels.

  ds-iter (iterator), the iterator to wrap
   - see: dl4clj.datasets.iterators (this ns)

  :as-code? (boolean), return java object or code for creating it

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/ReconstructionDataSetIterator.html"
  [& {:keys [iter as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:reconstruction-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-multiple-epochs-iterator
  "A dataset iterator for doing multiple passes over a dataset

  :iter (dataset iterator), an iterator for a dataset
   - see: dl4clj.datasets.iterators (this ns)

  :as-code? (boolean), return java object or code for creating it

  :que-size (int), the size for the multiple iterations (improve this desc)

  :total-iterations (long), the total number of times to run through the dataset

  :n-epochs (int), the number of epochs to run

  :dataset (dataset), a dataset

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/MultipleEpochsIterator.html"
  [& {:keys [iter que-size total-iterations n-epochs dataset as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:multiple-epochs-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-moving-window-base-dataset-iterator
  ;; currently can't test this until I figure out the issue im running into with
  ;; moving window dataset fetcher
  ;; this calls the fetcher behind the scene
  "DataSetIterator for moving window (rotating matrices)

  :batch-size (int), the batch size

  :as-code? (boolean), return java object or code for creating it

  :n-examples (int), the total number of examples

  :dataset (dataset), a dataset to make new examples from

  :window-rows (int), the number of rows to rotate

  :window-columns (int), the number of columns to rotate

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/MovingWindowBaseDataSetIterator.html"
  [& {:keys [batch-size n-examples dataset window-rows window-columns as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:moving-window-base-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-async-multi-dataset-iterator
  "Async prefetching iterator wrapper for MultiDataSetIterator implementations

  use caution when using this with a CUDA backend

  :multi-dataset-iter (multidataset iterator), iterator to wrap

  :as-code? (boolean), return java object or code for creating it

  :que-length (int), length of the que for async processing

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/AsyncMultiDataSetIterator.html"
  [& {:keys [multi-dataset-iter que-length as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:async-multi-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-iterator-dataset-iterator
  "A DataSetIterator that works on an Iterator, combining and splitting the input
  DataSet objects as required to get a consistent batch size.

  Typically used in Spark training, but may be used elsewhere.
  NOTE: reset method is not supported here.

  :iter (iter), an iterator containing datasets
   - see: dl4clj.datasets.iterators (this ns)

  :as-code? (boolean), return java object or code for creating it

  :batch-size (int), the batch size

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/IteratorDataSetIterator.html"
  [& {:keys [iter batch-size as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:iterator-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-iterator-multi-dataset-iterator
  "A DataSetIterator that works on an Iterator, combining and splitting the input
  DataSet objects as required to get a consistent batch size.

  Typically used in Spark training, but may be used elsewhere.
  NOTE: reset method is not supported here.

  :multi-dataset-iter (iter) an iterator containing multiple datasets

  :as-code? (boolean), return java object or code for creating it

  :batch-size (int), the batch size

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/IteratorMultiDataSetIterator.html"
  [& {:keys [multi-dataset-iter batch-size as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:iterator-multi-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-doubles-dataset-iterator
  "creates a dataset iterator which iterates over the supplied features and labels

  :features (coll of doubles), a collection of doubles which acts as inputs
   - [0.2 0.4 ...]

  :labels (coll of doubles), a collection of doubles which acts as targets
   - [0.4 0.8 ...]

  :as-code? (boolean), return java object or code for creating it

  :batch-size (int), the batch size

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/DoublesDataSetIterator.html"
  [& {:keys [features labels batch-size as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:doubles-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-floats-dataset-iterator
  "creates a dataset iterator which iterates over the supplied iterable

  :features (coll of floats), a collection of floats which acts as inputs

  :labels (coll of floats), a collection of floats which acts as the targets

  :as-code? (boolean), return java object or code for creating it

  :batch-size (int), the batch size

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/FloatsDataSetIterator.html"
  [& {:keys [features labels batch-size as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:floats-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-INDArray-dataset-iterator
  "creates a dataset iterator given a pair of INDArrays and a batch-size

  :features (vec or INDArray), an INDArray which acts as inputs
   - see: nd4clj.linalg.factory.nd4j

  :labels (vec or INDArray), an INDArray which as the targets
   - see: nd4clj.linalg.factory.nd4j

  :as-code? (boolean), return java object or code for creating it

  :batch-size (int), the batch size

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/INDArrayDataSetIterator.html"
  [& {:keys [features labels batch-size as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:INDArray-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-multi-data-set-iterator-adapter
  "Iterator that adapts a DataSetIterator to a MultiDataSetIterator

  :iter (datset-iterator), an iterator for a dataset
   - see: dl4clj.datasets.iterators (this ns)

  :as-code? (boolean), return java object or code for creating it

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/impl/MultiDataSetIteratorAdapter.html"
  [& {:keys [iter as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:ds-iter-to-multi-ds-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-list-dataset-iterator
  "creates a new list data set iterator given a collection of datasets.

  :dataset (collection), a collection of dataset examples
   - from (as-list dataset), from nd4clj.linalg.dataset.api.data-set

  :batch-size (int), the batch size, if not supplied, defaults to 5

  :as-code? (boolean), return java object or code for creating it

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/impl/ListDataSetIterator.html"
  [& {:keys [dataset batch-size as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:list-ds-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-singleton-multi-dataset-iterator
  "A very simple adapter class for converting a single MultiDataSet to a MultiDataSetIterator.

  :as-code? (boolean), return java object or code for creating it

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/impl/SingletonMultiDataSetIterator.html"
  [& {:keys [multi-dataset as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:multi-ds-to-multi-ds-iter opts})]
    (obj-or-code? as-code? code)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; default dataset iterators user facing fns
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_(defn new-curves-dataset-iterator
  "creates a dataset iterator for curbes data

  :batch-size (int), the size of the batch

  :as-code? (boolean), return java object or code for creating it

  :n-examples (int), the total number of examples

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/CurvesDataSetIterator.html"
  [& {:keys [batch-size n-examples as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:curves-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-cifar-data-set-iterator
  "Load the images from the cifar dataset,

  :batch-size (int), the batch size

  :as-code? (boolean), return java object or code for creating it

  :n-examples (int), the number of examples from the ds to include in the iterator

  :img-dim (vector), desired dimensions of the images
   - should contain 3 ints

  :train? (boolean), are we training or testing?

  :use-special-pre-process-cifar? (boolean), are we going to use the predefined preprocessor built for this dataset
   - There is a special preProcessor used to normalize the dataset based on Sergey Zagoruyko example https://github.com/szagoruyko/cifar.torch

  :img-transform (map) config map for an image-transformation (as of writing this doc string, not implemented)

  :n-possible-labels (int), specify the number of possible outputs/tags/classes for a given image

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/impl/CifarDataSetIterator.html
  and: https://github.com/szagoruyko/cifar.torch"
  [& {:keys [batch-size n-examples img-dims train?
             use-special-pre-process-cifar?
             n-possible-labels img-transform as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:cifar-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-iris-data-set-iterator
  "IrisDataSetIterator handles traversing through the Iris Data Set.

  :batch-size (int), size of the batch

  :as-code? (boolean), return java object or code for creating it

  :n-examples (int), number of examples to iterator over

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/impl/IrisDataSetIterator.html"
  [& {:keys [batch-size n-examples as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:iris-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-lfw-data-set-iterator
  "Creates a dataset iterator for the LFW image dataset.

  :img-dims (vec), desired dimensions of the images

  :as-code? (boolean), return java object or code for creating it

  :batch-size (int), the batch size

  :n-examples (int), number of examples to take from the dataset

  :use-subset? (boolean), use a subset of the dataset or the whole thing

  :train? (boolean, are we training a net or testing it

  :split-train-test (double), the division between training and testing datasets

  :n-labels (int), the number of possible classifications for a single image

  :seed (int), number used to keep randomization consistent

  :label-generator (label generator), call (new-parent-path-label-generator) or
   (new-pattern-path-label-generator opts) to create a label generator to use

  :image-transform (map), a transform to apply to the images,
   - as of writing this doc string, this functionality not implemented

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/impl/LFWDataSetIterator.html"
  [& {:keys [img-dims batch-size n-examples use-subset? train? split-train-test
             n-labels seed label-generator image-transform as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:lfw-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-mnist-data-set-iterator
  "creates a dataset iterator for the Mnist dataset

  :batch-size (int), the batch size

  :as-code? (boolean), return java object or code for creating it

  :train? (boolean), training or testing

  :seed (int), used to consistently randomize the dataset

  :n-examples (int), the overall number of examples

  :binarize? (boolean), whether to binarize mnist or not

  :shuffle? (boolean), whether to shuffle the dataset or not

  :batch (int), size of each patch
  - supplying batch-size will retrieve the entire dataset where as batch will get a subset

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/impl/MnistDataSetIterator.html"
  [& {:keys [batch-size train? seed n-examples binarize? shuffle? rng-seed batch
             as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:mnist-dataset-iter opts})]
    (obj-or-code? as-code? code)))

(defn new-raw-mnist-data-set-iterator
  "Mnist data with scaled pixels

  :batch (int) size of each patch

  :as-code? (boolean), return java object or code for creating it

  :n-examples (int), the overall number of examples

  see: https://deeplearning4j.org/doc/org/deeplearning4j/datasets/iterator/impl/RawMnistDataSetIterator.html"
  [& {:keys [batch n-examples as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:raw-mnist-dataset-iter opts})]
    (obj-or-code? as-code? code)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; spark dataset iterator user facing fns
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn new-path-spark-ds-iterator
  "A DataSetIterator that loads serialized DataSet objects
  from a String that represents the path
   -note: DataSet objects saved from a DataSet to an output stream

  :string-paths (coll), a collection of string paths representing the location
   of the data-set streams

  :iter (java.util.Iterator), an iterator for a collection of string paths
   representing the location of the data-set streams

  you should supply either :string-paths or :iter, if you supply both, will default
   to using the collection of string paths

  see: https://deeplearning4j.org/doc/org/deeplearning4j/spark/iterator/PathSparkDataSetIterator.html"
  [& {:keys [string-paths iter as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:path-to-ds opts})]
    (obj-or-code? as-code? code)))

(defn new-path-spark-multi-ds-iterator
  "A DataSetIterator that loads serialized MultiDataSet objects
  from a String that represents the path
   -note: DataSet objects saved from a MultiDataSet to an output stream

  :string-paths (coll), a collection of string paths representing the location
   of the data-set streams

  :iter (java.util.Iterator), an iterator for a collection of string paths
   representing the location of the data-set streams

  you should supply either :string-paths or :iter, if you supply both, will default
   to using the collection of string paths

  see: https://deeplearning4j.org/doc/org/deeplearning4j/spark/iterator/PathSparkMultiDataSetIterator.html"
  [& {:keys [string-paths iter as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:path-to-multi-ds opts})]
    (obj-or-code? as-code? code)))

(defn new-spark-portable-datastream-ds-iterator
  "A DataSetIterator that loads serialized DataSet objects
  from a PortableDataStream, usually obtained from SparkContext.binaryFiles()
   -note: DataSet objects saved from a DataSet to an output stream

  :streams (coll), a collection of portable datastreams

  :iter (java.util.Iterator), an iterator for a collection of portable datastreams

  you should only supply :streams or :iter, if you supply both, will default to
   using the collection of portable datastreams

  see: https://deeplearning4j.org/doc/org/deeplearning4j/spark/iterator/PortableDataStreamDataSetIterator.html"
  [& {:keys [streams iter as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:portable-ds-stream opts})]
    (obj-or-code? as-code? code)))

(defn new-spark-portable-datastream-multi-ds-iterator
  "A DataSetIterator that loads serialized MultiDataSet objects
  from a PortableDataStream, usually obtained from SparkContext.binaryFiles()
   -note: DataSet objects saved from a MultiDataSet to an output stream

  :streams (coll), a collection of portable datastreams

  :iter (java.util.Iterator), an iterator for a collection of portable datastreams

  you should only supply :streams or :iter, if you supply both, will default to
   using the collection of portable datastreams

  see: https://deeplearning4j.org/doc/org/deeplearning4j/spark/iterator/PortableDataStreamMultiDataSetIterator.html"
  [& {:keys [streams iter as-code?]
      :or {as-code? true}
      :as opts}]
  (let [code (iterator {:portable-multi-ds-stream opts})]
    (obj-or-code? as-code? code)))
