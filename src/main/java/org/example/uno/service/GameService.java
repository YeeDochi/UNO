package org.example.uno.service;

import org.example.uno.dto.BaseGameRoom;
import org.example.uno.dto.GameMessage;
import org.example.uno.dto.Player;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.example.common.service.ScoreSender;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GameService {
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScoreSender scoreSender;

    // 입장 처리
    public void join(String roomId, GameMessage message) {
        BaseGameRoom room = roomService.findRoom(roomId);
        if (room == null) return;

        Player newPlayer = new Player(message.getSender(), message.getSenderId());

        // [추가] 로그인 유저 체크 및 ID 저장 로직
        if (message.getData() != null && message.getData().containsKey("dbUsername")) {
            String realId = (String) message.getData().get("dbUsername");
            if (realId != null && !realId.equals("null") && !realId.isEmpty()) {
                newPlayer.setDbUsername(realId);
                System.out.println("✅ 로그인 유저 입장: " + newPlayer.getSender() + " (" + realId + ")");
            }
        }

        room.enterUser(newPlayer);

        message.setType("JOIN");
        message.setContent(message.getSender() + "님이 입장하셨습니다.");
        broadcast(roomId, message);

        GameMessage syncMsg = new GameMessage();
        syncMsg.setType("SYNC");
        syncMsg.setRoomId(roomId);
        syncMsg.setSender("SYSTEM");
        syncMsg.setData(room.getGameSnapshot()); // BaseGameRoom에 추가한 메서드 호출

        broadcast(roomId, syncMsg);
    }

    // 게임 행동 처리 (핵심)
    public void handleGameAction(String roomId, GameMessage message) {
        BaseGameRoom room = roomService.findRoom(roomId);
        if (room == null) return;

        GameMessage result = room.handleAction(message);

        if (result != null) {
            // [추가] 게임 종료 신호가 오면 점수 저장 로직 실행
            if ("GAME_OVER".equals(result.getType())) {
                // 방에 있는 모든 유저 정보를 넘겨줌
                endGame(roomId, new ArrayList<>(room.getUsers().values()));
            }

            broadcast(roomId, result);
        }
    }

    public void chat(String roomId, GameMessage message) {
        broadcast(roomId, message);
    }
    public void endGame(String roomId, List<Player> players) {
        BaseGameRoom room = roomService.findRoom(roomId);
        if (room == null) return;

        for (Player player : players) {
            if (player.getDbUsername() == null) {
                continue;
            }

            int totalScore = 0;
            if (room instanceof org.example.uno.dto.UnoGameRoom) {
                org.example.uno.dto.UnoGameRoom unoRoom = (org.example.uno.dto.UnoGameRoom) room;
                totalScore = unoRoom.getTotalScore(player.getSenderId());
            }

            scoreSender.sendScore(
                    player.getDbUsername(),
                    "UNO",
                    totalScore,
                    true
            );
        }
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