package com.codeit.otboo.api.dm.dto;

import java.util.UUID;

public record DmRoomResponse(
    UUID roomId,
    String dmKey,
    UUID opponentId,
    boolean created
) { }
