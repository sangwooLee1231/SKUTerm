document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("loginForm");
    const messageBox = document.getElementById("loginMessage");

    if (!form) return;

    function setMessage(type, text) {
        if (!messageBox) return;
        messageBox.textContent = text || "";
        messageBox.classList.remove("success", "error");
        if (type) messageBox.classList.add(type);
    }

    async function login(studentNumber, password) {
        try {
            const res = await fetch("/api/auth/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                credentials: "include", // refresh 토큰 쿠키 사용
                body: JSON.stringify({
                    studentNumber: studentNumber,
                    password: password
                })
            });

            const text = await res.text();
            let payload = null;
            try {
                payload = text ? JSON.parse(text) : null;
            } catch (_) {
                payload = null;
            }

            if (!res.ok) {
                const msg =
                    (payload && payload.message) ||
                    "로그인에 실패했습니다. 학번 또는 비밀번호를 다시 확인해주세요.";
                setMessage("error", msg);
                return;
            }

            const msg =
                (payload && payload.message) || "로그인에 성공했습니다.";

            setMessage("success", msg);

            // 로그인 성공 시 이동할 페이지 (대기열 또는 강의목록)
            setTimeout(() => {
                window.location.href = "/queue"; // 또는 "/lectures"
            }, 500);
        } catch (e) {
            console.error(e);
            setMessage("error", "로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    form.addEventListener("submit", (e) => {
        e.preventDefault();
        const studentNumber = form.studentNumber.value.trim();
        const password = form.password.value.trim();

        if (!studentNumber || !password) {
            setMessage("error", "학번과 비밀번호를 모두 입력해주세요.");
            return;
        }

        setMessage(null, "로그인 중...");
        login(studentNumber, password);
    });
});
