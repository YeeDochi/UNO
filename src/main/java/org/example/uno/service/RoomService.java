package org.example.uno.service;

import org.example.uno.dto.BaseGameRoom;
import org.example.uno.dto.UnoGameRoom;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {
    private final Map<String, BaseGameRoom> rooms = new ConcurrentHashMap<>();

    public BaseGameRoom createRoom(String name) {
        // 템플릿에서 이 부분만 수정
        UnoGameRoom room = new UnoGameRoom(name); // ★ 여기 수정
        rooms.put(room.getRoomId(), room);
        return room;
    }

    public BaseGameRoom findRoom(String roomId) {
        return rooms.get(roomId);
    }

    public List<BaseGameRoom> findAll() {
        return new ArrayList<>(rooms.values());
    }

    public void deleteRoom(String roomId) {
        rooms.remove(roomId);
    }
}