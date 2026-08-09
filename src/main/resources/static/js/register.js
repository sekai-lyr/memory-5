const registerForm = document.querySelector("#registerForm");

if (registerForm) {
    registerForm.addEventListener("submit", (event) => {
        const password = document.querySelector("#password").value;
        const confirmPassword = document.querySelector("#confirmPassword").value;
        const error = document.querySelector("#registerError");
        if (password.length < 6) {
            error.textContent = "密码至少 6 位";
            event.preventDefault();
            return;
        }
        if (password !== confirmPassword) {
            error.textContent = "两次输入的密码不一致";
            event.preventDefault();
        }
    });
}
