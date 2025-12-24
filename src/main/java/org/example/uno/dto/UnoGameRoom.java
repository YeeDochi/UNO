package org.example.uno.dto;

import org.example.uno.enums.UnoColor;
import org.example.uno.enums.UnoRank;
import java.util.*;

public class UnoGameRoom extends BaseGameRoom {
    private UnoDack deck;
    private UnoCard topCard;
    private UnoColor currentColor;

    // [기능 추가] 버린 카드 더미 (덱이 비면 이걸 섞어서 다시 씀)
    private List<UnoCard> discardPile = new ArrayList<>();

    private List<String> playerOrder = new ArrayList<>();
    private int currentPlayerIdx = 0;
    private boolean isClockwise = true;

    public UnoGameRoom(String name) {
        super(name);
    }

    // [버그 수정] 유저가 나가면 게임 순서에서도 제거해야 턴이 안 멈춤
    @Override
    public synchronized void exitUser(String playerId) {
        super.exitUser(playerId);
        playerOrder.remove(playerId);

        // 인덱스 보정 (나보다 앞 순서 사람이 나가면 인덱스 -1)
        if (currentPlayerIdx >= playerOrder.size()) {
            currentPlayerIdx = 0;
        }

        // 남은 사람이 1명이면 게임 종료 처리 (선택)
        if (playing && playerOrder.size() < 2) {
            playing = false;
        }
    }

    @Override
    public synchronized GameMessage handleAction(GameMessage message) {
        Player player = users.get(message.getSenderId());
        if (player == null) return createErrorMessage("플레이어 정보를 찾을 수 없습니다.", message.getSenderId());

        try {
            Map<String, Object> data = message.getData();
            String type = (String) data.getOrDefault("type", "");
            String actionType = (String) data.getOrDefault("actionType", "");

            if ("START".equals(actionType)) {
                if (playing) return createErrorMessage("이미 게임이 진행 중입니다.", player.getId());
                if (users.size() < 2) return createErrorMessage("최소 2명이 필요합니다.", player.getId());
                startGame();
                return createSystemMessage("GAME_STARTED", "게임이 시작되었습니다!");
            }

            if (!playing && ("PLAY_CARD".equals(type) || "DRAW_CARD".equals(type))) {
                return createErrorMessage("게임이 아직 시작되지 않았습니다.", player.getId());
            }

            if ("PLAY_CARD".equals(type)) {
                String colorStr = (String) data.get("color");
                String rankStr = (String) data.get("rank");
                String newColor = (String) data.get("newColor");

                if (colorStr == null || rankStr == null) return createErrorMessage("카드 데이터 오류", player.getId());

                return playCard(player, new UnoCard(UnoColor.valueOf(colorStr), UnoRank.valueOf(rankStr)), newColor);
            }

            if ("DRAW_CARD".equals(type)) {
                return drawCard(player);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return createErrorMessage("서버 오류: " + e.getMessage(), player.getId());
        }
        return null;
    }

    private void startGame() {
        this.playing = true;
        this.deck = new UnoDack();
        this.discardPile.clear();
        this.playerOrder = new ArrayList<>(users.keySet());
        Collections.shuffle(playerOrder);
        this.currentPlayerIdx = 0;
        this.isClockwise = true;

        for (Player p : users.values()) {
            List<UnoCard> hand = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                UnoCard c = drawOne();
                if (c != null) hand.add(c);
            }
            p.setAttribute("hand", hand);
        }

        this.topCard = drawOne();
        this.currentColor = topCard.getColor();
        if (this.currentColor == UnoColor.BLACK) this.currentColor = UnoColor.RED;
    }

    // [기능 추가] 덱 안전하게 뽑기 (비면 리필)
    private UnoCard drawOne() {
        UnoCard c = deck.draw();
        if (c == null) {
            if (discardPile.isEmpty()) return null; // 카드 전멸
            deck.remake(discardPile); // 버린 카드 섞어서 덱으로
            c = deck.draw();
        }
        return c;
    }

    private GameMessage playCard(Player player, UnoCard card, String newColorStr) {
        if (playerOrder.isEmpty()) return createErrorMessage("참가자가 없습니다.", player.getId());

        String currentPlayerId = playerOrder.get(currentPlayerIdx);
        if (!player.getId().equals(currentPlayerId)) {
            // 현재 누구 턴인지 알려줌
            Player currentP = users.get(currentPlayerId);
            String name = (currentP != null) ? currentP.getNickname() : "알 수 없음";
            return createErrorMessage("당신의 턴이 아닙니다! (현재: " + name + ")", player.getId());
        }

        if (!card.canPlayOn(topCard, currentColor)) {
            return createErrorMessage("낼 수 없는 카드입니다.", player.getId());
        }

        List<UnoCard> hand = (List<UnoCard>) player.getAttribute("hand");
        if (!removeCardFromHand(hand, card)) {
            return createErrorMessage("손에 없는 카드입니다.", player.getId());
        }

        // 낸 카드를 버린 카드 더미로 이동
        if (this.topCard != null) this.discardPile.add(this.topCard);
        this.topCard = card;

        // 색상 변경 처리
        if (card.getColor() == UnoColor.BLACK) {
            if (newColorStr != null && !newColorStr.isEmpty()) {
                try { this.currentColor = UnoColor.valueOf(newColorStr); }
                catch (IllegalArgumentException e) { this.currentColor = UnoColor.RED; }
            } else { this.currentColor = UnoColor.RED; }
        } else {
            this.currentColor = card.getColor();
        }

        // 특수 효과
        handleSpecialEffect(card);

        if (hand.isEmpty()) {
            this.playing = false;
            GameMessage msg = createSystemMessage("GAME_OVER", player.getNickname() + " 승리!");
            msg.getData().put("winnerName", player.getNickname());
            return msg;
        }

        nextTurn();
        return createSystemMessage("UPDATE", "턴이 넘어갔습니다.");
    }

    private GameMessage drawCard(Player player) {
        if (playerOrder.isEmpty()) return null;
        String currentPlayerId = playerOrder.get(currentPlayerIdx);
        if (!player.getId().equals(currentPlayerId)) {
            Player currentP = users.get(currentPlayerId);
            String name = (currentP != null) ? currentP.getNickname() : "알 수 없음";
            return createErrorMessage("당신의 턴이 아닙니다! (현재: " + name + ")", player.getId());
        }

        List<UnoCard> hand = (List<UnoCard>) player.getAttribute("hand");
        UnoCard newCard = drawOne();
        if (newCard != null) hand.add(newCard);

        nextTurn();
        return createSystemMessage("UPDATE", player.getNickname() + "님이 카드를 뽑았습니다.");
    }

    private void handleSpecialEffect(UnoCard card) {
        if (card.getRank() == UnoRank.REVERSE) {
            isClockwise = !isClockwise;
            // 2인플일 때 리버스는 스킵과 같음 -> 내 턴 한 번 더
            if (playerOrder.size() == 2) nextTurn();
        }
        else if (card.getRank() == UnoRank.SKIP) {
            nextTurn();
        }
        // [기능 추가] 공격 카드 구현
        else if (card.getRank() == UnoRank.DRAW_TWO) {
            nextTurn();
            attackNextPlayer(2);
        }
        else if (card.getRank() == UnoRank.WILD_DRAW_FOUR) {
            nextTurn();
            attackNextPlayer(4);
        }
    }

    private void attackNextPlayer(int count) {
        if (playerOrder.isEmpty()) return;
        String victimId = playerOrder.get(currentPlayerIdx);
        Player victim = users.get(victimId);
        if (victim != null) {
            List<UnoCard> hand = (List<UnoCard>) victim.getAttribute("hand");
            if (hand != null) {
                for(int i=0; i<count; i++) {
                    UnoCard c = drawOne();
                    if(c != null) hand.add(c);
                }
            }
        }
    }

    private GameMessage createErrorMessage(String content, String targetId) {
        GameMessage msg = createSystemMessage("ERROR", content);
        if (msg.getData() == null) msg.setData(new HashMap<>());
        msg.getData().put("targetId", targetId);
        return msg;
    }

    private void nextTurn() {
        if (playerOrder.isEmpty()) return;
        int direction = isClockwise ? 1 : -1;
        currentPlayerIdx = (currentPlayerIdx + direction + playerOrder.size()) % playerOrder.size();
    }

    private boolean removeCardFromHand(List<UnoCard> hand, UnoCard target) {
        if (hand == null) return false;
        for (int i = 0; i < hand.size(); i++) {
            UnoCard c = hand.get(i);
            if (c.getColor() == target.getColor() && c.getRank() == target.getRank()) {
                hand.remove(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized Map<String, Object> getGameSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("topCard", topCard);
        snapshot.put("currentColor", currentColor);

        String currentPid = (!playerOrder.isEmpty() && currentPlayerIdx < playerOrder.size())
                ? playerOrder.get(currentPlayerIdx) : "";
        snapshot.put("currentPlayer", currentPid);

        if (!currentPid.isEmpty() && users.containsKey(currentPid)) {
            snapshot.put("currentPlayerName", users.get(currentPid).getNickname());
        } else {
            snapshot.put("currentPlayerName", "");
        }

        snapshot.put("isClockwise", isClockwise);
        snapshot.put("playing", playing);

        Map<String, Integer> handSizes = new HashMap<>();
        Map<String, List<UnoCard>> allHands = new HashMap<>();

        for (String pid : users.keySet()) {
            Player p = users.get(pid);
            if (p != null) {
                List<UnoCard> hand = (List<UnoCard>) p.getAttribute("hand");
                if (hand == null) hand = new ArrayList<>();
                handSizes.put(p.getNickname(), hand.size());
                allHands.put(pid, hand);
            }
        }
        snapshot.put("handSizes", handSizes);
        snapshot.put("allHands", allHands);

        return snapshot;
    }

    private GameMessage createSystemMessage(String type, String content) {
        return GameMessage.builder()
                .type(type)
                .roomId(this.roomId)
                .sender("SYSTEM")
                .content(content)
                .data(getGameSnapshot())
                .build();
    }
}