let toastStack;

export function showToast(message, type = "info") {
    if (!toastStack) {
        toastStack = document.createElement("div");
        toastStack.className = "toast-stack";
        toastStack.setAttribute("role", "status");
        document.body.append(toastStack);
    }
    const toast = document.createElement("div");
    toast.className = "toast";
    toast.dataset.type = type;
    toast.textContent = message;
    toastStack.append(toast);
    window.setTimeout(() => toast.remove(), 3000);
}

function button(label) {
    const element = document.createElement("button");
    element.type = "button";
    element.textContent = label;
    return element;
}

export function openModal(title, contentNode) {
    const dialog = document.createElement("dialog");
    dialog.className = "modal";
    dialog.setAttribute("aria-label", title);

    const panel = document.createElement("div");
    panel.className = "modal-panel";
    const header = document.createElement("div");
    header.className = "section-header";
    const heading = document.createElement("h2");
    heading.textContent = title;
    const close = button("关闭");
    close.addEventListener("click", () => dialog.close());
    header.append(heading, close);
    panel.append(header, contentNode);
    dialog.append(panel);
    dialog.addEventListener("click", event => {
        if (event.target === dialog) {
            dialog.close();
        }
    });
    dialog.addEventListener("close", () => dialog.remove(), { once: true });
    document.body.append(dialog);
    dialog.showModal();
    return dialog;
}

export function confirmModal(title, message) {
    const content = document.createElement("div");
    const description = document.createElement("p");
    description.textContent = message;
    const actions = document.createElement("div");
    actions.className = "modal-actions";
    const cancel = button("取消");
    cancel.autofocus = true;
    const confirm = button("确认");
    confirm.className = "danger-button";
    actions.append(cancel, confirm);
    content.append(description, actions);

    const dialog = openModal(title, content);
    cancel.addEventListener("click", () => dialog.close());
    confirm.addEventListener("click", () => dialog.close("confirm"));
    return new Promise(resolve => {
        dialog.addEventListener("close", () => resolve(dialog.returnValue === "confirm"), { once: true });
    });
}

export function skeleton(width = "100%") {
    const element = document.createElement("span");
    element.className = "skeleton";
    element.setAttribute("aria-hidden", "true");
    element.style.width = width;
    return element;
}

export function renderTableSkeleton(container, columns) {
    container.replaceChildren();
    for (let index = 0; index < 5; index++) {
        const row = container.insertRow();
        row.setAttribute("aria-hidden", "true");
        for (let column = 0; column < columns; column++) {
            row.insertCell().append(skeleton(column === 1 ? "90%" : "70%"));
        }
    }
}

export function renderPagination(container, { page, totalPages }, onPageChange) {
    const focused = document.activeElement;
    const focusWasInside = container.contains(focused);
    const previous = button("上一页");
    const next = button("下一页");
    previous.disabled = page <= 1;
    next.disabled = page >= totalPages;
    previous.dataset.step = "previous";
    next.dataset.step = "next";

    const label = document.createElement("span");
    label.tabIndex = -1;
    label.textContent = `第 ${page} / ${totalPages} 页`;
    for (const [control, target] of [[previous, page - 1], [next, page + 1]]) {
        control.addEventListener("click", () => {
            if (container.getAttribute("aria-busy") !== "true") {
                onPageChange(target);
            }
        });
    }
    container.hidden = totalPages <= 1;
    container.replaceChildren(previous, label, next);
    if (focusWasInside && !container.hidden) {
        const control = focused.dataset.step === "previous" ? previous : next;
        (control.disabled ? label : control).focus();
    }
}

export const formatRate = new Intl.NumberFormat("zh-CN", { style: "percent", maximumFractionDigits: 1 }).format;
