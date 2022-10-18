(ns lambdaisland.xdemo.abalone
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [net.cgrand.xforms :as x]
            [kixi.stats.core :as stats]
            [com.hypirion.clj-xchart :as xchart]))

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


;; Get some stats about each parameter
(x/transjuxt (into {} (map (juxt identity #(comp (map %) (x/reduce stats/summary)))) attrs) data)

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

;; This method of linear regression uses a least squares method.
;; http://ib.berkeley.edu/courses/ib162/Regress.htm
;; https://en.wikipedia.org/wiki/Simple_linear_regression

;; Lets have a look at that data!

(comment
  (let [chart (xchart/xy-chart
               {"Abalones" (xchart/extract-series
                            {:x :diameter
                             :y :rings}
                            data)
                "Linear model" {:x [0 1]
                                :y [(first coefficients) (predict coefficients 1)]
                                :style {:render-style :line
                                        :marker-type :none}}}
               {:title "Abalone size and amount of rings..."
                :x-axis {:decimal-pattern "##.## mm"}
                :render-style :scatter})]
    (xchart/spit chart "/tmp/chart2.png")
    (java.nio.file.Files/copy (.toPath (io/file "/tmp/chart2.png"))
                              (.toPath (io/file "/tmp/chart.png"))
                              (into-array java.nio.file.CopyOption [java.nio.file.StandardCopyOption/REPLACE_EXISTING]))
    #_
    (xchart/view))

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

;; Notice that kixi.stats mostly provides reducing functions, and not
;; transducers, this makes sense, since you're interested in a final result, not
;; in a stream of data, but that does mean you end up using (transduce identity)
;; a lot, since (reduce ) doesn't call the zero/one arity version of reducing
;; functions.
;;
;; An alternative is xform's x/reduce and x/reductions, which will turn a
;; reducing function into a transducer, which you can then use in any number of
;; transducing contexts.

(x/transjuxt {:arit (x/reduce stats/arithmetic-mean)
              :geom (x/reduce stats/geometric-mean)
              :harm (x/reduce stats/harmonic-mean)}
             [1 2 3])
;; => {:arit 2.0, :geom 1.8171205928321397, :harm 18/11}

;; x/reductions is also cool because it allows you to see how some of these
;; statistical functions iteratively approach their final result. For example,
;; here's a sequence of [offset slope] coefficients that gradually approach the
;; value we calculated earlier as we feed it more data into.
;;
;; This partly duplicates code from kixi.stats.core/simple-linear-regression.
;; That function is really just sum-squares plus some completing logic.
;; x/reductions will never call the completing step, but we want to call it on
;; every step.

(sequence
 (comp (x/reductions (stats/sum-squares :shell-weight :rings))
       (map (fn [[_ x-bar y-bar ss-x _ ss-xy]] ; x-bar = arithmetic mean of x
                                        ; ss-x = sum of squared residuals (errors) for x
                                        ; ss-xy = "Corrected sum of cross products"
              (when-not (zero? ss-x)
                (let [b (/ ss-xy ss-x)]
                  [(- y-bar (* x-bar b)) b])))))
 data)
;;=>
'(nil
  nil
  [-1.7763568394002505E-15 100.00000000000001]
  [7.621621621621621 18.918918918918926]
  [7.554231974921629 18.432601880877748]
  [6.359591100420925 25.315694527961522]
  [6.055944055944053 25.874125874125884]
  [4.013567219405232 43.949568315746205]
  [3.943723382680994 44.77793551003854]
  [3.66337748941155 44.90402811570696]
  [3.465893567286967 46.50739200388574]
  [3.4942490962865573 46.730200460072304]
  [3.5302616165560305 46.622413119875056]
  [3.4462452728254984 46.497388798847474]
  [3.367872955164314 45.76690432143674]
  [3.249485896092809 45.74554766616227]
  [3.357097636465955 44.42881260551491]
  [3.0753660907466074 45.49559436831708]
  [3.22640042613841 44.96911936194412]
  [3.093919958953311 45.50538737814264]
  [3.1911986430456096 45.137580098002275]
  [4.00477154424523 41.5558126084442]
  [4.3811947645150475 39.94854010515719]
  [4.65119330763553 37.53793160009843]
  [4.638914041277673 36.837668217441546]
  [5.288535741278493 31.805826272591066]
  [5.639811251762987 29.04470872293129]
  [5.838915168716496 27.37937559129612]
  [5.926011739264754 26.629595304294096]
  [5.844888841986777 27.27240748557759]
  [5.837301199495221 27.25994161861745]
  [6.272493254466336 24.248596402883095]
  [6.646372177690841 22.049822813429046]
  [6.402265054447462 23.745504452970813]
  [6.198695715890773 24.931203491117515]
  [6.787252808008873 21.61302955608284]
  [6.674423264471025 21.93606559686668]
  [6.619002100871374 22.40781725078376]
  [6.500496806245564 22.719659332860182]
  [6.489200101490363 22.608633299870927]
  [6.529334301119866 22.480298631273328]
  [6.521363155082329 22.504106633945533]
  [6.523005366898163 22.64929500166327]
  [6.323383896757455 23.36713660395757]
  [6.171375868245412 23.918787218965154]
  [5.95362910747963 24.706097288065056]
  [5.902989991453902 24.869717459237183]
  [5.887333290564862 24.906884232396678]
  [5.791367685166282 25.113509545901675]
  [5.730707958163569 25.32620929786196]
  [5.713573811950107 25.11594107439006]
  [5.659188779529255 25.144524386623786]
  [5.603767504173028 25.312379854869057]
  [5.613829714305395 25.294285106105626]
  [5.632350979855261 25.255368273845313]
  [5.573639060020683 25.42699364903863]
  [5.541219361550605 25.301998313133584]
  [5.509057482844197 25.382005430778]
  [5.466482097723363 25.44003221831009]
  [5.339297504382814 25.924593813708974]
  [5.280912093516649 25.979974773803306]
  [5.226762276853849 26.112202244210106]
  [5.211279502704019 26.126261247623003]
  [5.205748957427247 26.11881471281976]
  [5.1747294027076896 26.216670987823317]
  [5.142838701645983 26.220639134522656]
  [5.123643250429573 26.26832725037938]
  [5.151967711201643 25.99571773381243]
  [5.404699027644314 24.31496255802915]
  [5.4137043486713 24.302895587939847]
  [5.390120329003577 24.38905793925179]
  [5.38594359656925 24.477596858609534]
  [5.391509560871014 24.46041705996095]
  [5.244542417233772 25.52220036540184]
  [5.3400348340604396 24.7604084057243]
  [5.341519329492499 24.743417775314057]
  [5.332065447045879 24.97262085628453]
  [5.375209754351952 24.483065285438077]
  [5.398139485712888 24.18747176322173]
  [5.403275044863871 24.081072684036755]
  [5.398805134964131 24.12073321487818]
  [5.4103628370414585 23.866880364228802]
  [6.036700667893908 20.33902446732918]
  [6.015754957670198 20.63448475356282]
  [5.8821601693336305 21.41865648377899]
  [5.878070895636839 21.45941426099632]
  [5.948876972985198 20.963052187755085]
  [5.96865443750676 20.805241718081934]
  [5.960578697407513 20.77122264505984]
  [5.9529414432511505 20.785977734842163]
  [5.956665402001267 20.728923718172535]
  [5.980676064565406 20.806131395126826]
  [5.984082387161655 20.8204983943922]
  [5.99867601545931 20.70338918790904]
  [6.051038713120027 20.283195261676912]
  [6.073495754401115 20.145162768677793]
  [6.11435136959475 19.891930471969612]
  [6.097637711172138 19.871326231823122]
  [6.076021111381374 19.921374405263116]
  [6.0308523925829585 20.013326928932806]
  ...)
