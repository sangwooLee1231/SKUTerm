(function () {
    const cartList = document.getElementById("cartList");
    const checkAll = document.getElementById("checkAll");
    const selectedCountElem = document.getElementById("selectedCount");
    const selectedCreditElem = document.getElementById("selectedCredit");

    const btnEnrollSelected = document.getElementById("btnEnrollSelected");
    const btnDeleteSelected = document.getElementById("btnDeleteSelected");

    let currentCartData = [];

    // 초기 로드
    fetchCart();

    // 장바구니 목록 조회
    async function fetchCart() {
        try {
            const response = await fetch("/api/cart/my");
            if (!response.ok) throw new Error("Load failed");

            const json = await response.json();
            currentCartData = json.data.cartItems; // 데이터 저장

            renderCart(currentCartData);
            updateSummary();
        } catch (error) {
            console.error(error);
            cartList.innerHTML = `<div class="empty-state"><p>장바구니를 불러오지 못했습니다.</p></div>`;
        }
    }

    // 렌더링
    function renderCart(items) {
        if (!items || items.length === 0) {
            cartList.innerHTML = `<div class="empty-state"><p>장바구니가 비어있습니다.</p></div>`;
            return;
        }

        cartList.innerHTML = "";

        items.forEach(item => {
            const isMajor = item.division === "MAJOR";
            const badgeClass = isMajor ? "major" : "";
            const badgeText = isMajor ? "전공" : "교양";

            const isFull = (item.currentCount || 0) >= (item.maxCapacity || 0);

            const div = document.createElement("div");
            div.className = "cart-item-card";
            div.innerHTML = `
                <div class="col-check">
                    <input type="checkbox" class="scp-checkbox item-check" 
                           data-id="${item.lectureId}" 
                           data-credit="${item.credit}">
                </div>
                
                <div class="col-info">
                    <span class="ci-badge ${badgeClass}">${badgeText}</span>
                    <h3 class="ci-title">${escapeHtml(item.courseName)}</h3>
                </div>
                
                <div class="col-meta">
                    <div class="meta-item"><span class="meta-label">교수</span>${escapeHtml(item.professor)}</div>
                    <div class="meta-item"><span class="meta-label">학점</span>${item.credit}학점</div>
                    <div class="meta-item"><span class="meta-label">강의실</span>${escapeHtml(item.room || "미정")}</div>
                    <div class="meta-item"><span class="meta-label">분반</span>A반</div>
                </div>
                
                <div class="col-stat">
                    <span class="${isFull ? 'stat-full' : 'stat-avail'}">
                        ${item.currentCount}/${item.maxCapacity} (${isFull ? '마감' : '신청가능'})
                    </span>
                </div>
                
                <div class="col-action">
                    <button type="button" class="btn-delete-icon" data-id="${item.lectureId}" title="삭제">
                        &times;
                    </button>
                </div>
            `;
            cartList.appendChild(div);
        });

        // 개별 삭제 버튼 이벤트
        document.querySelectorAll(".btn-delete-icon").forEach(btn => {
            btn.addEventListener("click", (e) => {
                deleteCartItem(e.target.dataset.id);
            });
        });

        // 체크박스 이벤트 바인딩 (요약 업데이트용)
        document.querySelectorAll(".item-check").forEach(chk => {
            chk.addEventListener("change", updateSummary);
        });
    }

    // 요약 정보 업데이트 (선택 건수, 학점)
    function updateSummary() {
        const checkboxes = document.querySelectorAll(".item-check:checked");
        let count = 0;
        let credits = 0;

        checkboxes.forEach(chk => {
            count++;
            credits += parseInt(chk.dataset.credit || 0);
        });

        selectedCountElem.textContent = count;
        selectedCreditElem.textContent = credits;

        // 전체 선택 체크박스 상태 동기화
        const total = document.querySelectorAll(".item-check").length;
        checkAll.checked = (total > 0 && count === total);
    }

    // 전체 선택/해제
    checkAll.addEventListener("change", (e) => {
        const isChecked = e.target.checked;
        document.querySelectorAll(".item-check").forEach(chk => {
            chk.checked = isChecked;
        });
        updateSummary();
    });

    btnDeleteSelected.addEventListener("click", () => {
        const checked = document.querySelectorAll(".item-check:checked");
        if (checked.length === 0) {
            alert("삭제할 강의를 선택해주세요.");
            return;
        }
        if (!confirm(`${checked.length}건의 강의를 삭제하시겠습니까?`)) return;

        // 병렬로 삭제 요청 보내기 (혹은 백엔드에 일괄삭제 API가 있다면 그걸 사용하는게 좋음. 현재는 없으므로 반복 호출)
        const deletePromises = Array.from(checked).map(chk => {
            const id = chk.dataset.id;
            return fetch(`/api/cart/${id}`, { method: "DELETE" });
        });

        Promise.all(deletePromises)
            .then(() => {
                alert("삭제되었습니다.");
                fetchCart(); // 새로고침
            })
            .catch(err => console.error(err));
    });

    async function deleteCartItem(id) {
        if (!confirm("장바구니에서 삭제하시겠습니까?")) return;
        try {
            const res = await fetch(`/api/cart/${id}`, { method: "DELETE" });
            if (res.ok) fetchCart();
            else alert("삭제 실패");
        } catch(e) { console.error(e); }
    }

    btnEnrollSelected.addEventListener("click", async () => {
        const checked = document.querySelectorAll(".item-check:checked");
        if (checked.length === 0) {
            alert("수강신청할 강의를 선택해주세요.");
            return;
        }

        const ids = Array.from(checked).map(c => parseInt(c.dataset.id));

        if (!confirm(`선택한 ${ids.length}건에 대해 수강신청을 진행하시겠습니까?`)) return;

        try {
            const response = await fetch("/api/cart/enroll", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ lectureIds: ids })
            });

            const json = await response.json();

            if (response.ok) {
                const results = json.data.results;
                let successCnt = 0;
                let failMsg = "";

                results.forEach(r => {
                    if (r.success) successCnt++;
                    else failMsg += `- 강의ID ${r.lectureId}: ${r.message}\n`;
                });

                let msg = `${successCnt}건 신청 완료되었습니다.`;
                if (failMsg) msg += `\n\n[실패 목록]\n${failMsg}`;

                alert(msg);
                fetchCart();
            } else {
                alert(json.message || "수강신청 요청 실패");
            }
        } catch (e) {
            console.error(e);
            alert("시스템 오류가 발생했습니다.");
        }
    });

    function escapeHtml(text) {
        if (!text) return "";
        return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
    }
})();