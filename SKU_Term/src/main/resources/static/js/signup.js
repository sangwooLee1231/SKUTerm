document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("signupForm");
    const messageBox = document.getElementById("signupMessage");

    if (!form) return;

    function setMessage(type, text) {
        if (!messageBox) return;
        messageBox.textContent = text || "";
        messageBox.classList.remove("success", "error");
        if (type === "success") messageBox.classList.add("success");
        if (type === "error") messageBox.classList.add("error");
    }

    async function signup(payload) {
        try {
            const res = await fetch("/api/auth/signup", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                credentials: "include",
                body: JSON.stringify(payload)
            });

            const text = await res.text();
            let body = null;
            try {
                body = text ? JSON.parse(text) : null;
            } catch (e) {
                body = null;
            }

            // 1) HTTP 레벨 에러 (400, 409 등) 처리
            if (!res.ok) {
                // GlobalExceptionHandler의 ErrorResponse 형태 가정
                // {status, code, message, path}
                const errMsg =
                    (body && body.message) ||
                    "회원가입에 실패했습니다. 입력 정보를 다시 확인해주세요.";
                setMessage("error", errMsg);
                return;
            }

            if (body && typeof body.success === "boolean") {
                if (body.success) {
                    const successMsg =
                        (body.message) ||
                        "회원가입이 완료되었습니다. 로그인 페이지로 이동합니다.";
                    setMessage("success", successMsg);

                    setTimeout(() => {
                        window.location.href = "/member/login";
                    }, 800);
                } else {
                    const errMsg =
                        (body.error && body.error.message) ||
                        "회원가입에 실패했습니다. 입력 정보를 다시 확인해주세요.";
                    setMessage("error", errMsg);
                }
                return;
            }

            const msg =
                (body && body.message) ||
                (typeof body === "string" ? body : "회원가입이 완료되었습니다.");
            setMessage("success", msg);

            setTimeout(() => {
                window.location.href = "/member/login";
            }, 800);
        } catch (e) {
            console.error(e);
            setMessage(
                "error",
                "회원가입 중 알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
            );
        }
    }

    form.addEventListener("submit", (e) => {
        e.preventDefault();

        // 제출할 때마다 메시지 초기화
        setMessage(null, "");

        const studentNumber = form.studentNumber.value.trim();
        const name = form.studentName.value.trim();
        const department = form.studentDepartment.value.trim();
        const gradeRaw = form.studentGrade.value;
        const password = form.studentPassword.value.trim();
        const passwordConfirm =
            document.getElementById("passwordConfirm").value.trim();

        if (!studentNumber || !name || !password || !passwordConfirm) {
            setMessage("error", "필수 항목을 모두 입력해주세요.");
            return;
        }

        if (password !== passwordConfirm) {
            setMessage("error", "비밀번호가 서로 일치하지 않습니다.");
            return;
        }

        if (password.length < 8) {
            setMessage("error", "비밀번호는 최소 8자 이상이어야 합니다.");
            return;
        }

        const grade = gradeRaw ? parseInt(gradeRaw, 10) : null;

        const payload = {
            studentNumber,
            password,     // DTO 필드 이름과 정확히 일치
            name,
            department,
            grade
        };

        setMessage(null, "회원가입 중입니다...");
        signup(payload);
    });
});
