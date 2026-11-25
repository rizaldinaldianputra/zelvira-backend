package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByRoomId(String roomId);

    List<ChatRoom> findAllByOrderByUpdatedAtDesc();
}
