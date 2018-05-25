(ns engine.sound.core
  (:import
   (org.lwjgl.openal AL10 ALC10 ALC AL)
   (org.lwjgl.system MemoryStack)
   (org.lwjgl.stb STBVorbis)
   (org.lwjgl.system.libc LibCStdlib))
  (:require [nio.core :as nio]))
(defonce audio-template
  {:device nil
   :context nil
   :listener nil})

(defonce sound-template
  {:name nil
   :buffer nil
   :source nil})

(defn init
  [global]
  ; create audio instance in global object
  (let [device-default-name
        (ALC10/alcGetString 0 ALC10/ALC_DEVICE_SPECIFIER)
        device
        (ALC10/alcOpenDevice device-default-name)
        _ (prn device)
        listener
        (do nil)
        context
        (let [attributes (java.nio.IntBuffer/allocate 0)]
          (ALC10/alcCreateContext
           device
           attributes))
        _ (ALC10/alcMakeContextCurrent context)]
    (as-> audio-template $
      (assoc $ :device device)
      (assoc $ :context context)
      (swap! global assoc :audio $))))

; :deprecated    
; (defn check
;   [global]
;   (let [device
;         (-> @global (:audio) (:device))
;         alCapabilities
;         (AL/createCapabilities
;          (ALC/createCapabilities device))]
;     (println "OpenAL 1.1 support: " (alCapabilities/OpenAL11))
;     (println "OpenAL 1.0 support: " (alCapabilities/OpenAL11))))

(defn close
  [global]
  (ALC10/alcDestroyContext
   (-> @global
       (:audio)
       (:context)))
  (ALC10/alcCloseDevice
   (-> @global
       (:audio)
       (:device))))

(defn setup-audio
  [global audio]
  (swap! global assoc-in [:level :audio]
         (conj (-> @global (:level) (:audio)) audio)))

(defn play-audio
  [audio]
  (AL10/alSourcePlay (:source audio)))
;   try {
;      //Wait for a second
;      Thread.sleep(1000)};
;  catch (InterruptedException ignored) {}


(defn delete-audio
  [global name]
  (AL10/alDeleteSources (-> @global (:audio) (:source)))
  (AL10/alDeleteBuffers (-> @global (:audio) (:buffer)))
  (swap! global update-in [:audio] dissoc name))

(defn load-audio
  [global name path-to-audio-file]
  (let [; Allocate space to store return information from the function
        stack
        (MemoryStack/stackPush)
        channels-buffer
        (.mallocInt stack 1)
        sample-rate-buffer
        (.mallocInt stack 1)
        raw-buffer
        (STBVorbis/stb_vorbis_decode_filename
         path-to-audio-file
         channels-buffer
         ALC10)
        channels
        (.get channels-buffer 0)
        ; find the correct OpenAL format
        format
        (cond
          (= channels 1)
          AL10/AL_FORMAT_MONO16
          (= channels 1)
          AL10/AL_FORMAT_STEREO16
          :else -1)
        sample-rate
        (.get sample-rate-buffer 0)
        ; Request space for the buffer
        *buffer
        (AL10/alGenBuffers)
        ; Send the data to OpenAL
        _ (AL10/alBufferData *buffer format raw-buffer sample-rate)
        ; Free the memory allocated by STB
        _ (.free raw-buffer)
        *source
        (AL10/alGenSources)
        _ (AL10/alSourcei *source AL10/AL_BUFFER *buffer)]
    (setup-audio
     global
     (-> audio-template
         (assoc :name name)
         (assoc :source *source)
         (assoc :buffer *buffer)))))
