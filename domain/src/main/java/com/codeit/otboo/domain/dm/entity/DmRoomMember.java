package com.codeit.otboo.domain.dm.entity;

import java.time.OffsetDateTime;

import com.codeit.otboo.domain.common.BaseEntity;
import com.codeit.otboo.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "dm_room_member",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_dm_room_member_room_user",
        columnNames = {"dm_room_id", "user_id"}
    )
)
@Getter
@SuperBuilder
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmRoomMember extends BaseEntity {

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dm_room_id", nullable = false)
    private DmRoom dmRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    private DirectMessage lastReadMessage;


    public DmRoomMember(DmRoom dmRoom, User user) {
        this.dmRoom = dmRoom;
        this.user = user;
        this.joinedAt = OffsetDateTime.now();
    }

    public void updateLastReadMessage(DirectMessage message) {
        this.lastReadMessage = message;
    }

    public void leave() {
        this.leftAt = OffsetDateTime.now();
    }

    public void rejoin() {
        this.joinedAt = OffsetDateTime.now();
        this.leftAt = null;
        this.lastReadMessage = null;
    }

}
