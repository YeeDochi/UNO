package org.example.uno.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.example.uno.enums.UnoColor;
import org.example.uno.enums.UnoRank;

@Getter
@AllArgsConstructor
@ToString
public class UnoCard {
    private final UnoColor color;
    private final UnoRank rank;

    // 카드를 낼 수 있는지 확인하는 메서드 (현재 탑 카드와 비교)
    public boolean canPlayOn(UnoCard topCard, UnoColor currentDesignatedColor) {
        // 1. 와일드 카드는 언제든 낼 수 있음
        if (this.color == UnoColor.BLACK) return true;

        // 2. 색깔이 같거나 (와일드로 지정된 색 포함)
        if (this.color == topCard.getColor() || this.color == currentDesignatedColor) return true;

        // 3. 숫자가/기호가 같으면 낼 수 있음
        if (this.rank == topCard.getRank()) return true;

        return false;
    }
}