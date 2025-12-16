(function () {
    const timetableBody = document.getElementById("timetableBody");
    const btnRefresh = document.getElementById("btnRefresh");
    const btnDownload = document.getElementById("btnDownload");

    const HOURS = [
        { p: 1, t: "09:00" },
        { p: 2, t: "10:00" },
        { p: 3, t: "11:00" },
        { p: 4, t: "12:00" },
        { p: 5, t: "13:00" },
        { p: 6, t: "14:00" },
        { p: 7, t: "15:00" },
        { p: 8, t: "16:00" },
        { p: 9, t: "17:00" }
    ];

    const DAYS = ["MON", "TUE", "WED", "THU", "FRI"];

    initGrid();
    fetchTimetable();

    function initGrid() {
        timetableBody.innerHTML = "";

        HOURS.forEach(h => {
            const tr = document.createElement("tr");

            const th = document.createElement("td");
            th.className = "td-period";
            th.innerHTML = `
                <span class="period-num">${h.p}</span>
                <span class="period-time">${h.t}</span>
            `;
            tr.appendChild(th);

            // 요일 컬럼
            DAYS.forEach(day => {
                const td = document.createElement("td");
                td.id = `cell-${day}-${h.p}`;
                td.dataset.day = day;
                td.dataset.period = h.p;
                tr.appendChild(td);
            });

            timetableBody.appendChild(tr);
        });
    }

    async function fetchTimetable() {
        try {
            const response = await fetch("/api/timetable/my");
            if (!response.ok) throw new Error("Load failed");

            const json = await response.json();
            const slots = json.data.timetable;

            renderTimetable(slots);
        } catch (error) {
            console.error(error);
        }
    }

    function renderTimetable(slots) {
        const colorMap = new Map();
        let colorIndex = 1;

        slots.forEach(slot => {
            let startPeriod = slot.period;
            if (!startPeriod && slot.startTime) {
                const hour = parseInt(slot.startTime.split(":")[0]);
                startPeriod = hour - 9 + 1;
            }


            const duration = slot.credit || 1;

            if (!startPeriod || startPeriod < 1) return;

            const cellId = `cell-${slot.dayOfWeek}-${startPeriod}`;
            const targetCell = document.getElementById(cellId);

            if (targetCell) {
                if (targetCell.style.display === "none") return;

                if (!colorMap.has(slot.courseName)) {
                    colorMap.set(slot.courseName, (colorIndex++ % 6) + 1);
                }
                const colorClass = `color-${colorMap.get(slot.courseName)}`;

                targetCell.innerHTML = `
                    <div class="course-cell ${colorClass}" style="height:100%">
                        <div class="cc-name">${slot.courseName}</div>
                        <div class="cc-room">${slot.room || ""}</div>
                        <div class="cc-prof">${slot.startTime}~${slot.endTime}</div>
                    </div>
                `;

                if (duration > 1) {
                    targetCell.rowSpan = duration;

                    for (let i = 1; i < duration; i++) {
                        const nextPeriod = startPeriod + i;
                        const nextCellId = `cell-${slot.dayOfWeek}-${nextPeriod}`;
                        const nextCell = document.getElementById(nextCellId);
                        if (nextCell) {
                            nextCell.style.display = "none";
                        }
                    }
                }
            }
        });
    }

    btnRefresh.addEventListener("click", () => {
        initGrid();
        fetchTimetable();
    });

    btnDownload.addEventListener("click", () => {
        alert("이미지 저장 기능은 준비중입니다.");
    });
})();