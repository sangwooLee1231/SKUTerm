(function () {
    const enrollmentList = document.getElementById("enrollmentList");
    const totalCountElem = document.getElementById("totalCount");
    const totalCreditElem = document.getElementById("totalCredit");

    // 초기 로드
    fetchEnrollments();

    // 수강신청 목록 조회
    async function fetchEnrollments() {
        try {
            const response = await fetch("/api/enrollments/my");
            if (!response.ok) throw new Error("Fetch failed");

            const json = await response.json();
            const list = json.data.enrollments; // List<EnrollmentListResponseDto>

            renderEnrollments(list);
            updateSummary(list);

        } catch (error) {
            console.error(error);
            enrollmentList.innerHTML = `<div class="empty-state"><p>내역을 불러오지 못했습니다.</p></div>`;
        }
    }

    // 렌더링
    function renderEnrollments(items) {
        if (!items || items.length === 0) {
            enrollmentList.innerHTML = `<div class="empty-state"><p>신청된 강의가 없습니다.</p></div>`;
            return;
        }

        enrollmentList.innerHTML = "";

        items.forEach(item => {
            const isMajor = item.division === "MAJOR";
            const badgeClass = isMajor ? "major" : "general";
            const badgeText = isMajor ? "전공" : "교양";

            const div = document.createElement("div");
            div.className = "enrollment-card";
            div.innerHTML = `
                <div class="ec-info">
                    <span class="ec-badge ${badgeClass}">${badgeText}</span>
                    <h3 class="ec-title">${escapeHtml(item.courseName)}</h3>
                </div>

                <div class="ec-meta">
                    <div class="meta-group">
                        <span class="meta-label">교수님</span>
                        <span class="meta-value">${escapeHtml(item.professor)}</span>
                    </div>
                    <div class="meta-group">
                        <span class="meta-label">학점</span>
                        <span class="meta-value">${item.credit}학점</span>
                    </div>
                    <div class="meta-group">
                        <span class="meta-label">강의실</span>
                        <span class="meta-value">${escapeHtml(item.room || "미정")}</span>
                    </div>
                    <div class="meta-group">
                        <span class="meta-label">신청일시</span>
                        <span class="meta-value">${formatDate(item.createdAt)}</span>
                    </div>
                </div>

                <div class="ec-action">
                    <button type="button" class="btn-cancel" data-id="${item.lectureId}">
                        수강취소
                    </button>
                </div>
            `;
            enrollmentList.appendChild(div);
        });

        // 취소 버튼 이벤트 바인딩
        document.querySelectorAll(".btn-cancel").forEach(btn => {
            btn.addEventListener("click", (e) => {
                cancelEnrollment(e.target.dataset.id);
            });
        });
    }

    // 학점/건수 요약 업데이트
    function updateSummary(items) {
        if (!items) return;

        let count = items.length;
        let credits = items.reduce((sum, item) => sum + (item.credit || 0), 0);

        totalCountElem.textContent = count;
        totalCreditElem.textContent = credits;
    }

    // 수강 취소 요청
    async function cancelEnrollment(lectureId) {
        if (!confirm("정말로 수강신청을 취소하시겠습니까?")) return;

        try {
            const response = await fetch(`/api/enrollments/${lectureId}`, {
                method: "DELETE"
            });
            const json = await response.json();

            if (response.ok) {
                alert("수강 취소되었습니다.");
                fetchEnrollments();
            } else {
                alert(json.message || "취소에 실패했습니다.");
            }
        } catch (error) {
            console.error(error);
            alert("오류가 발생했습니다.");
        }
    }

    function escapeHtml(text) {
        if (!text) return "";
        return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
    }

    function formatDate(dateString) {
        if (!dateString) return "-";
        const date = new Date(dateString);
        return date.toLocaleDateString() + " " + date.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
    }
})();