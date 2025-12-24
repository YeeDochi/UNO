// [uno.js] GitHub 테마 스타일 적용 및 애니메이션 개선

const UnoGame = {
    myId: null,
    isMyTurn: false,
    myHand: [],
    currentColor: null,
    // [추가] 이전 패의 장수를 기억하여 새 카드 식별에 사용
    prevHandSize: 0,

    onEnterRoom: () => {
        UnoGame.myId = null;
        UnoGame.isMyTurn = false;
        UnoGame.myHand = [];
        UnoGame.currentColor = null;
        UnoGame.prevHandSize = 0; // 초기화
        console.log("[Uno] Entered Room.");

        // UI 초기화
        document.getElementById('my-hand-area').innerHTML = '';
        document.getElementById('top-card-area').innerHTML = '';
        document.getElementById('opponents-area').innerHTML = '<span style="color:var(--text-secondary); font-size:13px;">게임 대기 중...</span>';
        document.getElementById('info-color').innerText = '-';
        document.getElementById('info-turn').innerText = '-';
    },

    handleMessage: (msg, myId) => {
        if (msg.type === 'GAME_OVER') return;

        if (msg.type === 'ERROR') {
            if (msg.data && msg.data.targetId && msg.data.targetId !== myId) return;
            Core.showAlert(msg.content);
            if (msg.data) updateGameState(msg.data, myId);
            return;
        }

        UnoGame.myId = myId;

        if (msg.type === 'SYNC' || msg.type === 'GAME_STARTED') {
            updateGameState(msg.data, myId);
        }
        else if (msg.type === 'MY_HAND') {
            // [변경] 내 패가 업데이트될 때 기존 장수를 기억했다가 비교
            UnoGame.prevHandSize = UnoGame.myHand.length;
            UnoGame.myHand = msg.data;
            renderMyHand();
        }
        else if (msg.type === 'UPDATE') {
            updateGameState(msg.data, myId);
        }
    }
};

// --- [1] 렌더링 헬퍼 ---
const UnoRenderer = {
    getDisplayRank: (rank) => {
        const map = {
            'ZERO': '0', 'ONE': '1', 'TWO': '2', 'THREE': '3', 'FOUR': '4',
            'FIVE': '5', 'SIX': '6', 'SEVEN': '7', 'EIGHT': '8', 'NINE': '9',
            'SKIP': '<i class="fas fa-ban"></i>', // FontAwesome 아이콘 활용 (선택사항)
            'REVERSE': '<i class="fas fa-sync-alt"></i>',
            'DRAW_TWO': '+2',
            'WILD': '<i class="fas fa-star"></i>',
            'WILD_DRAW_FOUR': '+4'
        };
        // 아이콘이 없으면 텍스트 그대로 출력
        return map[rank] || rank;
    },

    createCard: (color, rank, onClick) => {
        const area = document.createElement('div');
        area.className = 'card-area';

        const colorClass = color ? color : 'back';
        const displayRank = UnoRenderer.getDisplayRank(rank);

        area.innerHTML = `
            <div class="card ${colorClass}">
                <div class="card-face">
                    <div class="card-corner-text" style="text-align:left;">${displayRank}</div>
                    <div class="card-center-text">${displayRank}</div>
                    <div class="card-corner-text" style="text-align:right; transform:rotate(180deg);">${displayRank}</div>
                </div>
                <div class="card-back"></div>
            </div>
        `;

        if (onClick) {
            area.onclick = () => {
                document.querySelectorAll('.card-area').forEach(el => el.classList.remove('selected'));
                area.classList.add('selected');
                onClick();
            };
        }
        return area;
    }
};

// --- [2] 상태 업데이트 함수 ---

function updateGameState(snapshot, myId) {
    if (!snapshot) return;

    UnoGame.isMyTurn = (snapshot.currentPlayer === myId);
    UnoGame.currentColor = snapshot.currentColor;

    // 1. 탑 카드 그리기
    const topArea = document.getElementById('top-card-area');
    if (topArea && snapshot.topCard) {
        topArea.innerHTML = '';
        const cardEl = UnoRenderer.createCard(snapshot.topCard.color, snapshot.topCard.rank, null);
        // 탑 카드는 호버 효과 제거
        cardEl.style.pointerEvents = 'none';
        cardEl.querySelector('.card').style.boxShadow = 'none';
        topArea.appendChild(cardEl);
    }

    // 2. 텍스트 정보 갱신
    updateInfoText(snapshot, myId);

    // 3. 상대방 목록 갱신
    updateOpponents(snapshot, myId);

    // 4. 내 패 갱신
    if (snapshot.allHands && snapshot.allHands[myId]) {
        UnoGame.prevHandSize = UnoGame.myHand.length;
        UnoGame.myHand = snapshot.allHands[myId];
        renderMyHand();
    }
}

function updateInfoText(snapshot, myId) {
    const colorSpan = document.getElementById('info-color');
    const turnSpan = document.getElementById('info-turn');
    const startBtn = document.getElementById('startBtn');

    if (colorSpan) {
        const colorMap = {
            // card-style.css와 일치하는 톤 다운된 색상
            'RED': '#e55039',
            'BLUE': '#4a69bd',
            'GREEN': '#20bf6b',
            'YELLOW': '#f39c12',
            'BLACK': 'var(--text-primary)'
        };

        const cText = snapshot.currentColor || '-';

        if (cText !== '-') {
            const colorCode = colorMap[cText] || 'var(--text-primary)';
            // 네온 효과(shadow)도 조금 줄여서 차분하게
            colorSpan.innerHTML = `<i class="fas fa-circle" style="color:${colorCode};"></i> ${cText}`;
            colorSpan.style.color = colorCode;
            colorSpan.style.textShadow = 'none'; // 눈부심 방지 위해 제거
            colorSpan.style.fontWeight = 'bold';
        } else {
            // ... (기존 유지)
            colorSpan.innerText = '-';
            colorSpan.style.color = 'var(--text-primary)';
            colorSpan.style.textShadow = 'none';
        }
    }

    if (turnSpan) {
        if (!snapshot.playing) {
            turnSpan.innerText = "대기 중";
        } else {
            const isMe = (snapshot.currentPlayer === myId);
            const displayName = snapshot.currentPlayerName || "Unknown";
            turnSpan.innerHTML = isMe ?
                "<span class='badge' style='background:var(--color-success-emphasis); color:white;'>MY TURN</span>" :
                `<span style='color:var(--text-secondary);'>${displayName}의 턴</span>`;
        }
    }

    // 버튼 상태 (기존 유지)
    if (startBtn) {
        startBtn.disabled = snapshot.playing;
        startBtn.innerText = snapshot.playing ? "진행 중" : "게임 시작";
    }
}

function updateOpponents(snapshot, myId) {
    const oppArea = document.getElementById('opponents-area');
    if (!oppArea || !snapshot.handSizes) return;

    oppArea.innerHTML = '';
    const opponents = Object.keys(snapshot.handSizes).filter(name => name !== Core.myNickname);

    if (opponents.length === 0) {
        oppArea.innerHTML = '<span style="color:var(--text-secondary); font-size:13px;">접속 대기 중...</span>';
        return;
    }

    opponents.forEach(name => {
        const count = snapshot.handSizes[name];
        const div = document.createElement('div');
        // GitHub 스타일 배지 적용
        div.className = 'badge';
        div.style.display = 'flex';
        div.style.alignItems = 'center';
        div.style.gap = '8px';
        div.style.padding = '6px 12px';
        div.style.fontWeight = 'normal';

        div.innerHTML = `
            <i class="fas fa-user" style="color:var(--text-secondary);"></i>
            <span>${name}</span>
            <span style="font-weight:bold; color:var(--text-primary);">${count}</span>
        `;
        oppArea.appendChild(div);
    });
}

// [핵심 수정] 내 패 그리기 함수 개선
function renderMyHand() {
    const container = document.getElementById('my-hand-area');
    if (!container) return;

    container.innerHTML = '';
    UnoGame.myHand.forEach((card, idx) => {
        const cardEl = UnoRenderer.createCard(card.color, card.rank, () => {
            reqPlayCard(card);
        });

        // [수정 포인트] 새로 추가된 카드 애니메이션 처리
        if (idx >= UnoGame.prevHandSize) {
            const delay = (idx - UnoGame.prevHandSize) * 0.1;
            cardEl.style.animationDelay = `${delay}s`;
            cardEl.classList.add('card-anim-deal');

            // [추가] 애니메이션이 끝나면 클래스를 제거하여 CSS 충돌 방지
            // (애니메이션의 opacity:1이 남아서 흐려지지 않는 현상 해결)
            setTimeout(() => {
                cardEl.classList.remove('card-anim-deal');
            }, (delay + 0.4) * 1000 + 100); // 애니메이션 시간(0.3s~0.4s)보다 조금 넉넉하게
        }

        // 내 턴 아니면 흐리게 및 클릭 불가 처리
        if (!UnoGame.isMyTurn) {
            // 이제 card-anim-deal 클래스가 사라지면 이 스타일이 정상 적용됩니다.
            cardEl.style.opacity = 0.7;
            cardEl.style.cursor = "not-allowed";
            cardEl.style.filter = "grayscale(50%)";
        }

        container.appendChild(cardEl);
    });

    // 렌더링 후 현재 패 크기를 저장
    UnoGame.prevHandSize = UnoGame.myHand.length;
}

// --- [3] 액션 요청 ---
function reqDrawCard() {
    if (!UnoGame.isMyTurn) return Core.showAlert("아직 내 턴이 아닙니다.");
    Core.sendAction({ type: 'DRAW_CARD' });
}

let pendingWildCard = null;

// [수정] 카드 내기 요청
function reqPlayCard(card) {
    if (!UnoGame.isMyTurn) return Core.showAlert("아직 내 턴이 아닙니다.");

    if (card.color === 'BLACK') {
        // [변경] prompt 대신 커스텀 모달 띄우기
        pendingWildCard = card; // 현재 낼 카드를 임시 저장
        document.getElementById('color-modal').classList.remove('hidden');
        // 드롭다운 초기화 (기본값 RED)
        document.getElementById('color-select').value = 'RED';
    } else {
        // 일반 카드는 바로 전송
        Core.sendAction({
            type: 'PLAY_CARD',
            color: card.color,
            rank: card.rank
        });
    }
}

// [추가] 색상 선택 모달 확인 버튼 클릭 시
function submitColorSelection() {
    const selectEl = document.getElementById('color-select');
    const selectedColor = selectEl.value; // RED, BLUE, GREEN, YELLOW

    if (!pendingWildCard) return; // 에러 방지

    // 서버로 전송
    Core.sendAction({
        type: 'PLAY_CARD',
        color: 'BLACK',
        rank: pendingWildCard.rank,
        newColor: selectedColor
    });

    // 모달 닫기 및 초기화
    closeColorModal();
}

// [추가] 색상 선택 모달 닫기 (취소 시)
function closeColorModal() {
    document.getElementById('color-modal').classList.add('hidden');
    pendingWildCard = null;
}

// --- [4] 초기화 ---
Core.init(UnoGame, {
    apiPath: '/UNO',
    wsPath: '/UNO/ws',
    gameName: 'UNO Online'
});