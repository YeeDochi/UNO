package org.example.uno.service;

import org.example.uno.dto.BaseGameRoom;
import org.example.uno.dto.GameMessage;
import org.example.uno.dto.Player;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameService {
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    // 입장 처리

// [GameService.java] handleGameAction 등 다른 메서드는 기존 유지
// join 메서드만 아래와 같이 수정해주세요.

    public void join(String roomId, GameMessage message) {
        BaseGameRoom room = roomService.findRoom(roomId);
        if (room == null) return;

        // [수정] 이미 존재하는 유저라면 덮어쓰지 않고 기존 정보 유지 (패 보존)
        Player existingPlayer = room.getUsers().get(message.getSenderId());
        if (existingPlayer == null) {
            room.enterUser(new Player(message.getSender(), message.getSenderId()));
        } else {
            // 닉네임이 바뀌었을 수도 있으니 업데이트
            existingPlayer.setNickname(message.getSender());
        }

        message.setType("JOIN");
        message.setContent(message.getSender() + "님이 입장하셨습니다.");
        broadcast(roomId, message);

        // [동기화] 현재 게임 상태를 입장한 유저에게만이라도 확실히 보내야 함
        // 여기서는 전체에게 뿌려서 모두의 화면을 최신으로 맞춤
        GameMessage syncMsg = new GameMessage();
        syncMsg.setType("SYNC");
        syncMsg.setRoomId(roomId);
        syncMsg.setSender("SYSTEM");
        syncMsg.setData(room.getGameSnapshot());

        broadcast(roomId, syncMsg);
    }

    // 게임 행동 처리 (핵심)
    public void handleGameAction(String roomId, GameMessage message) {
        BaseGameRoom room = roomService.findRoom(roomId);
        if (room == null) return;

        GameMessage result = room.handleAction(message);

        if (result != null) {
            broadcast(roomId, result);
        }
    }

    public void chat(String roomId, GameMessage message) {
        // 정답 체크 로직이 필요하면 여기서 room.checkAnswer() 등을 호출 가능
        broadcast(roomId, message);
    }

    public void exit(String roomId, GameMessage message) {
        BaseGameRoom room = roomService.findRoom(roomId);
        if (room != null) {
            room.exitUser(message.getSenderId());
            if (room.getUsers().isEmpty()) {
                roomService.deleteRoom(roomId);
            } else {
                broadcast(roomId, message);
            }
        }
    }

    private void broadcast(String roomId, GameMessage message) {
        messagingTemplate.convertAndSend("/topic/" + roomId, message);
    }
}