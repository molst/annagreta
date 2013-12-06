(ns annagreta.person "Functions working on person entities only with minimal dependencies.")

(defn make-id-person "Makes the simplest possible person map that identifies one unique person out of a string that is either a db-id, a primary email or a nickname. Leaves 'person-id' untouched if it's a map."
  [person-id]
  (let [parsed-person-id (if (string? person-id) (read-string person-id) person-id)]
    (cond
     (number? parsed-person-id)                          {:db/id parsed-person-id}
     (and (string? person-id) (.contains person-id "@")) {:person/primary-email person-id}
     (string? person-id)                                 {:person/nickname person-id}
     (map? person-id)                                    person-id)))

(defn get-id "Gets an indexed id that can be used for fast identification anywhere. Prefers descrete identifiers."
  [person]
  (if-let [entity-id (:db/id person)]
    entity-id
    (if-let [nickname (:person/nickname person)]
      nickname
      (:person/primary-email person))))

(defn primary-email [person]
  (if (map? person) (:person/primary-email person) person))

(defn print-name-descretely "Prints only first name or nickname if set, otherwise prints 'Nameless', or, if present, 'not-found-text'."
  [person & not-found-text]
  (if-let [first-name (:person/first-name person)]
    first-name
    (if-let [nickname (:person/nickname person)]
      nickname
      (if-let [not-found-text (first not-found-text)] not-found-text "Nameless"))))

(defn print-short-name-or-id
  "Prints a name or id for informative purposes. Intended to always print some short identification even though it does not have to be exact or unique."
  [person & not-found-text]
  (print-name-descretely person
                         (if-let [last-name (:person/first-name person)]
                           last-name
                           (if-let [primary-email (:person/primary-email person)]
                             (first (clojure.string/split primary-email #"@"))
                             (if-let [not-found-text (first not-found-text)] not-found-text "Nameless")))))

(defn print-all-names [person]
  (let [first-name (:person/first-name person)
        last-name  (:person/last-name  person)
        nickname   (:person/nickname   person)]
    (cond
     (and first-name last-name) (str first-name " " last-name " | " nickname)
     (or first-name last-name) (str first-name last-name " | " nickname)
     true nickname)))
