package com.mooncore.modules.audio;

/** Résultat de résolution : la piste à jouer et sa source. */
public record ResolvedAudio(String trackId, AudioSource source) {}
