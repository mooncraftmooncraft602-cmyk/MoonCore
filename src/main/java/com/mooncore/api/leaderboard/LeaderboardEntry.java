package com.mooncore.api.leaderboard;

import java.util.UUID;

/** Une ligne de classement. */
public record LeaderboardEntry(int rank, UUID uuid, String name, long value) {}
