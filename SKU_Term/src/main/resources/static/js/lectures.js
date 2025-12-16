(function () {
    const searchForm = document.getElementById("searchForm");
    const lectureList = document.getElementById("lectureList");
    const resultCount = document.getElementById("resultCount");
    const btnSearch = document.getElementById("btnSearch");
    const btnReset = document.getElementById("btnReset");

    // 초기화: 검색어 입력 시 엔터 처리
    searchForm.querySelectorAll("input").forEach(input => {
        input.addEventListener("keyup", (e) => {
            if (e.key === "Enter") fetchLectures();
        });
    });

    btnSearch.addEventListener("click", fetchLectures);

    btnReset.addEventListener("click", () => {
        searchForm.reset();
        lectureList.innerHTML = `
            <div class="empty-state">
                <p>초기화되었습니다. 다시 검색해주세요.</p>
            </div>
        `;
        resultCount.textContent = "0";
    });

    // API 호출 함수
    async function fetchLectures() {
        // 로딩 상태
        lectureList.innerHTML = `<div class="empty-state"><p>데이터를 불러오는 중입니다...</p></div>`;

        const formData = new FormData(searchForm);
        const params = new URLSearchParams();
        for (const [key, value] of formData.entries()) {
            if (value && value.trim() !== "") params.append(key, value.trim());
        }

        try {
            const response = await fetch(`/api/lectures?${params.toString()}`);
            if (!response.ok) throw new Error("서버 응답 오류");

            const json = await response.json();
            const lectures = json.data.lectures;

            resultCount.textContent = lectures.length;
            renderLectures(lectures);

        } catch (error) {
            console.error(error);
            lectureList.innerHTML = `
                <div class="empty-state">
                    <p>목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</p>
                </div>
            `;
        }
    }

    // 렌더링 함수 (와이드 카드 형태)
    function renderLectures(lectures) {
        if (!lectures || lectures.length === 0) {
            lectureList.innerHTML = `
                <div class="empty-state">
                    <p>검색 결과가 없습니다.</p>
                </div>
            `;
            return;
        }

        lectureList.innerHTML = "";

        lectures.forEach(lecture => {
            const card = document.createElement("div");
            card.className = "lecture-row-card";

            // 데이터 처리
            const isMajor = lecture.division === "MAJOR";
            const badgeClass = isMajor ? "major" : "general";
            const badgeText = isMajor ? "전공" : "교양";

            const current = lecture.currentCount || 0;
            const max = lecture.maxCapacity || 0;
            const ratio = max > 0 ? (current / max) * 100 : 0;
            const isFull = current >= max;
            const statusText = isFull ? "마감" : "여석 있음";
            const statusClass = isFull ? "full" : "available";

            // 시간/장소 정보
            const timeInfo = lecture.timeDescription || "-"; // 백엔드 DTO 확인 필요
            const roomInfo = lecture.room || "미정";

            // 카드 왼쪽 테두리 색상 (전공/교양 구분용)
            card.style.borderLeftColor = isMajor ? "#0369a1" : "#9ca3af";

            card.innerHTML = `
                <div class="card-section-main">
                    <div class="lc-badge-row">
                        <span class="lc-badge ${badgeClass}">${badgeText}</span>
                    </div>
                    <h3 class="lc-title">${escapeHtml(lecture.courseName)}</h3>
                    <span class="lc-code">Code: ${lecture.id}</span>
                </div>

                <div class="card-section-info">
                    <div class="info-item">
                        <span class="info-label">교수님</span>
                        <span class="info-value">${escapeHtml(lecture.professor || "미정")}</span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">학점</span>
                        <span class="info-value">${lecture.credit}학점</span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">강의시간</span>
                        <span class="info-value" title="${escapeHtml(timeInfo)}">
                            ${truncateText(timeInfo, 18)}
                        </span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">강의실</span>
                        <span class="info-value">${escapeHtml(roomInfo)}</span>
                    </div>
                </div>

                <div class="card-section-action">
                    <div class="status-box">
                        <div class="status-text ${statusClass}">
                            ${current} / ${max} (${statusText})
                        </div>
                        <div class="progress-track">
                            <div class="progress-bar ${statusClass}" style="width: ${Math.min(ratio, 100)}%"></div>
                        </div>
                    </div>
                    <button type="button" class="btn-cart-add" data-id="${lecture.id}">
                        장바구니 담기
                    </button>
                </div>
            `;

            lectureList.appendChild(card);
        });

        // 장바구니 버튼 바인딩
        document.querySelectorAll(".btn-cart-add").forEach(btn => {
            btn.addEventListener("click", (e) => addToCart(e.target.dataset.id));
        });
    }

    // 장바구니 추가 로직
    async function addToCart(lectureId) {
        if(!confirm("장바구니에 담으시겠습니까?")) return;

        try {
            const response = await fetch("/api/cart", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({ lectureId: parseInt(lectureId) })
            });
            const json = await response.json();
            if (response.ok) alert("장바구니에 담았습니다.");
            else alert(json.message || "실패했습니다.");
        } catch(err) {
            console.error(err);
            alert("오류가 발생했습니다.");
        }
    }

    function escapeHtml(text) {
        if (!text) return "";
        return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
    }

    function truncateText(text, maxLength) {
        if (!text) return "";
        if (text.length <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

})();