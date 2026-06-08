package com.mooncore.api.season;

/** Informations sur une saison. {@code endsAtMs} = 0 si la saison n'a pas de fin définie. */
public record SeasonInfo(String seasonId, long startedAtMs, long endsAtMs, boolean active) {}
