(function () {
    const myRankElem = document.getElementById("myRank");
    const expectedTimeElem = document.getElementById("expectedTime");
    const progressBar = document.getElementById("progressBar");
    const queueTokenInfo = document.getElementById("queueTokenInfo");

    // 폴링 간격 (1초)
    const POLLING_INTERVAL = 1000;
    let initialRank = 0;

    // 세션에 토큰이 있는지 확인
    const storedToken = sessionStorage.getItem("queueToken");

    if (storedToken) {
        pollStatus(storedToken);
    } else {
        joinQueue();
    }

    async function joinQueue() {
        try {
            const response = await fetch("/api/queue/join", {
                method: "POST"
            });
            const json = await response.json();

            if (response.ok) {
                const data = json.data;
                const token = data.queueToken;

                document.cookie = `queueToken=${token}; path=/; max-age=3600`;
                sessionStorage.setItem("queueToken", token);
                if (data.active) {
                    window.location.href = "/lectures";
                    return;
                }
                initialRank = data.position;
                pollStatus(token);
            } else {
                alert("대기열 진입 실패: " + json.message);
                window.location.href = "/";
            }
        } catch (e) {
            console.error(e);
            alert("서버 연결 오류");
        }
    }

    function pollStatus(token) {
        queueTokenInfo.textContent = `Token: ${token.substring(0, 8)}...`;

        // 1초마다 상태 확인
        const intervalId = setInterval(async () => {
            try {
                const response = await fetch(`/api/queue/status?token=${token}`);
                const json = await response.json();

                if (response.ok) {
                    const status = json.data.queueStatus;

                    if (status.active) {
                        clearInterval(intervalId);
                        progressBar.style.width = "100%";
                        myRankElem.textContent = "0";
                        expectedTimeElem.textContent = "입장 중...";

                        setTimeout(() => {
                            window.location.href = "/lectures";
                        }, 500);
                        return;
                    }

                    // 2기 중이면 UI 업데이트
                    updateUI(status);

                } else {
                    clearInterval(intervalId);
                    sessionStorage.removeItem("queueToken");
                    alert("대기 시간이 만료되었습니다. 다시 진입합니다.");
                    window.location.reload();
                }

            } catch (e) {
                console.error("Polling error", e);
            }
        }, POLLING_INTERVAL);
    }

    function updateUI(status) {
        const currentPos = status.position;
        const waitTime = status.estimatedWaitSeconds;

        myRankElem.textContent = currentPos;

        const min = Math.floor(waitTime / 60);
        const sec = waitTime % 60;
        expectedTimeElem.textContent = `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;


        if (initialRank === 0 || currentPos > initialRank) {
            initialRank = currentPos + 10;
        }

        const progress = Math.max(0, Math.min(100, ((initialRank - currentPos) / initialRank) * 100));
        progressBar.style.width = `${progress}%`;
    }

})();