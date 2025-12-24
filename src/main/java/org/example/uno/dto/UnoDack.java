package org.example.uno.dto;

import org.example.uno.enums.UnoColor;
import org.example.uno.enums.UnoRank;

import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class UnoDack {
    private Stack<UnoCard> cards = new Stack<>();

    public UnoDack() {
        initialize();
        shuffle();
    }

    private void initialize() {
        for (UnoColor color : UnoColor.values()) {
            if (color == UnoColor.BLACK) continue;
            cards.push(new UnoCard(color, UnoRank.ZERO));
            for (int i = 0; i < 2; i++) {
                for (UnoRank r : new UnoRank[]{UnoRank.ONE, UnoRank.TWO, UnoRank.THREE, UnoRank.FOUR, UnoRank.FIVE, UnoRank.SIX, UnoRank.SEVEN, UnoRank.EIGHT, UnoRank.NINE, UnoRank.SKIP, UnoRank.REVERSE, UnoRank.DRAW_TWO}) {
                    cards.push(new UnoCard(color, r));
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            cards.push(new UnoCard(UnoColor.BLACK, UnoRank.WILD));
            cards.push(new UnoCard(UnoColor.BLACK, UnoRank.WILD_DRAW_FOUR));
        }
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public UnoCard draw() {
        if (cards.isEmpty()) {
            return null; // 에러 대신 null 반환 (Room에서 처리)
        }
        return cards.pop();
    }

    // [추가] 버린 카드들을 다시 덱으로 채우기
    public void remake(List<UnoCard> discardPile) {
        cards.addAll(discardPile);
        shuffle();
        discardPile.clear(); // 가져왔으니 버린 카드 더미는 비움
    }

    public int size() {
        return cards.size();
    }
}