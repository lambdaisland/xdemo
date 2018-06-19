(ns lambdaisland.xdemo.abalone
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [net.cgrand.xforms :as x]
            [kixi.stats.core :as stats]))

;; Demo of some basic linear regression using kixi.stats. Our dataset is the
;; Abalone dataset from the UCI Machine Learning Repository, found at
;; http://www.dcc.fc.up.pt/~ltorgo/Regression/DataSets.html

;; The number of rings on the abalone shows its age, but determining the amount of
;; rings is tedious, so we try to predict the amount of rings based on something
;; that's easier to measure, in this case, the diameter.

(def attrs [:sex
            :length
            :diameter
            :height
            :whole-weight
            :shucked-weight
            :viscera-weight
            :shell-weight
            :rings])

(def data
  (into []
        (map (fn [as]
               (into {}
                     (x/by-key (map #(Double/parseDouble %)))
                     (-> (zipmap attrs as)
                         (dissoc :sex)))))
        (csv/read-csv (slurp (io/resource "abalone.data")))))

;; With a little bit of massaging our data is a nice vector of maps.

data
;;=>
[{:length 0.455,
  :diameter 0.365,
  :height 0.095,
  :whole-weight 0.514,
  :shucked-weight 0.2245,
  :viscera-weight 0.101,
  :shell-weight 0.15,
  :rings 15.0}
 ,,,]

;; Let's have a look at what we have
(into []
      (comp (x/by-key #(int (* 10 (:diameter %)))        ; key = rounded diameter size
                      :rings                             ; value = amount of rings
                      (comp (x/by-key identity x/count)  ; group by amount of rings and count the number of occurences
                            (x/sort-by first)            ; and sort by the amount of rings
                            (x/into [])))
            (x/sort-by first))                           ; sort by rounded diameter size
      data)

;; You can look at this as a bunch of histograms. This helps to get a feel for
;; the data, and allows us to see afterwards if our results are roughly within
;; range of what we would expect intuitively.

[[0 [[1.0 1] [3.0 1] [4.0 1]]]
 ;; for abalones of about 0.1 diameter, 46 have 4 rings
 [1 [[2.0 1] [3.0 14] [4.0 46] [5.0 43] [6.0 20] [7.0 4] [8.0 2]]]
 ;; for abaolones of about 0.2 diameter, 145 have 6 rings
 [2 [[4.0 10] [5.0 66] [6.0 145] [7.0 138] [8.0 51] [9.0 36] [10.0 26] [11.0 9] [12.0 5] [13.0 2]]]
 [3 [[5.0 6] [6.0 85] [7.0 206] [8.0 270] [9.0 173] [10.0 101] [11.0 65] [12.0 51] [13.0 36] [14.0 26] [15.0 17] [16.0 9] [17.0 3] [18.0 5] [19.0 4] [21.0 2] [22.0 1] [23.0 2]]]
 [4 [[6.0 9] [7.0 41] [8.0 227] [9.0 392] [10.0 345] [11.0 219] [12.0 113] [13.0 101] [14.0 66] [15.0 60] [16.0 31] [17.0 34] [18.0 21] [19.0 16] [20.0 15] [21.0 5] [22.0 2] [23.0 5] [25.0 1] [26.0 1] [27.0 1]]]
 ;; for abalones of about 0.5 diameter, 188 have 11 rings
 [5 [[7.0 2] [8.0 18] [9.0 88] [10.0 160] [11.0 188] [12.0 93] [13.0 62] [14.0 32] [15.0 26] [16.0 26] [17.0 21] [18.0 16] [19.0 12] [20.0 11] [21.0 7] [22.0 3] [23.0 1] [24.0 2] [27.0 1] [29.0 1]]]
 [6 [[10.0 2] [11.0 6] [12.0 5] [13.0 2] [14.0 2] [16.0 1] [23.0 1]]]]

;; Now it gets ridiculously easy, kixi.stats.core/simple-linear-regression
;; returns a reducing function that calculates the offset and slope of the line
;; that is the best describes the correlation between two parameters.

(def coefficients
  (transduce identity (stats/simple-linear-regression :diameter :rings) data))
;;=> [2.3185735167565795 18.669921360615433]

;; Y=a+bX

;; Given these coefficients we can now predict the number of rings given a certain diameter.

(defn predict [[offset slope] diameter]
  (+ offset (* slope diameter)))


;; For the 0.2 diameter class, the most common occurence was 6 rings

(predict coefficients 0.2)
;;=> 6.052557788879666

;; For the 0.5 diameter class, the most common occurence was 11 rings

(predict coefficients 0.5)
;; => 11.653534197064296

;; checks out!


;; In this case I picked diameter since it seemed intuitively to be a good
;; predictor of age, but lets have a look which other attributes could serve.

(->> data
     (transduce identity (stats/correlation-matrix (into {} (map (fn [x] [x x])) attrs)))
     (filter #(= :rings (second (first %))))
     (sort-by last))
;; => ([[:sex :rings] nil]
;;     [[:shucked-weight :rings] 0.4208836579452148]
;;     [[:viscera-weight :rings] 0.5038192487597698]
;;     [[:whole-weight :rings] 0.5403896769238992]
;;     [[:length :rings] 0.5567195769296182]
;;     [[:height :rings] 0.5574673244580344]
;;     [[:diameter :rings] 0.5746598513059198]
;;     [[:shell-weight :rings] 0.6275740445103175])

;; Seems shell-weight would be an even better predictor
