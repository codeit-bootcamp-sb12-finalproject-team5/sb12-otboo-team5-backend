package com.codeit.otboo.domain.dm.repository;

import java.util.UUID;

public interface DmUnreadCountProjection {

    UUID getRoomId();

    long getUnreadCount();
}
