import { request } from "./api.js";
import "./nav.js";
import { confirmModal, renderPagination, showToast, skeleton } from "./ui.js";

const endpoint = "/api/admin/problems";
const controls = document.getElementById("admin-controls");
const form = document.getElementById("problem-form");
const heading = document.getElementById("form-heading");
const caseList = document.getElementById("test-cases");
const template = document.getElementById("test-case-template");
const list = document.getElementById("admin-problems");
const status = document.getElementById("admin-status");
const pagination = document.getElementById("admin-pagination");
const loading = document.getElementById("admin-loading");
const empty = document.getElementById("admin-empty");

let editingId = null;
let page = 1;

function renumberCases() {
    for (const [index, entry] of Array.from(caseList.children).entries()) {
        entry.querySelector("legend").textContent = "用例 " + (index + 1);
    }
}

function addCase({ input = "", expectedOutput = "", isSample = false } = {}) {
    const entry = template.content.firstElementChild.cloneNode(true);
    entry.querySelector('[name="input"]').value = input;
    entry.querySelector('[name="expectedOutput"]').value = expectedOutput;
    entry.querySelector('[name="isSample"]').checked = isSample;
    entry.querySelector("button").addEventListener("click", () => {
        entry.remove();
        renumberCases();
    });

    caseList.append(entry);
    renumberCases();
}

function newProblem() {
    editingId = null;
    heading.textContent = "新增题目";
    form.reset();
    caseList.replaceChildren();
    addCase({ isSample: true });
}

async function openProblem(id) {
    const problem = await request(endpoint + "/" + id);
    editingId = problem.id;
    heading.textContent = "编辑题目 #" + editingId;
    form.elements.title.value = problem.title;
    form.elements.description.value = problem.description;
    form.elements.timeLimitMs.value = problem.timeLimitMs;
    form.elements.memoryLimitMb.value = problem.memoryLimitMb;
    form.elements.difficulty.value = problem.difficulty;
    caseList.replaceChildren();
    problem.testCases.forEach(addCase);
}

async function loadPage(target) {
    const result = await request(endpoint + "?" + new URLSearchParams({ page: target, size: 12 }));
    page = result.page;
    list.replaceChildren();
    for (const problem of result.items) {
        const item = document.createElement("li");
        const edit = document.createElement("button");
        edit.type = "button";
        edit.className = "edit-problem";
        edit.textContent = "#" + problem.id + " " + problem.title;
        edit.addEventListener("click", () => perform(() => openProblem(problem.id)));

        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "danger-button";
        remove.textContent = "删除";
        remove.setAttribute("aria-label", "删除 " + problem.title);
        remove.addEventListener("click", async () => {
            if (await confirmModal("删除题目", "确认删除「" + problem.title + "」及其所有测试用例？")) {
                perform(() => removeProblem(problem.id));
            }
        });
        item.append(edit, remove);
        list.append(item);
    }

    empty.hidden = result.total !== 0;
    renderPagination(pagination, result, target => perform(() => loadPage(target)));
}

async function perform(action) {
    controls.disabled = true;
    status.textContent = "";
    try {
        await action();
    } catch (error) {
        showToast(error.message, "error");
    } finally {
        controls.disabled = false;
    }
}

async function removeProblem(id) {
    await request(endpoint + "/" + id, { method: "DELETE" });
    if (editingId === id) {
        newProblem();
    }
    await loadPage(list.childElementCount === 1 && page > 1 ? page - 1 : page);
    showToast("题目已删除。", "success");
}

form.addEventListener("submit", event => {
    event.preventDefault();
    const testCases = Array.from(caseList.children, entry => ({
        input: entry.querySelector('[name="input"]').value,
        expectedOutput: entry.querySelector('[name="expectedOutput"]').value,
        isSample: entry.querySelector('[name="isSample"]').checked
    }));
    if (!testCases.length) {
        status.textContent = "请至少添加一组测试用例。";
        return;
    }

    const data = {
        title: form.elements.title.value,
        description: form.elements.description.value,
        difficulty: form.elements.difficulty.value,
        timeLimitMs: form.elements.timeLimitMs.valueAsNumber,
        memoryLimitMb: form.elements.memoryLimitMb.valueAsNumber,
        testCases
    };
    const creating = editingId === null;
    perform(async () => {
        const saved = await request(creating ? endpoint : endpoint + "/" + editingId, {
            method: creating ? "POST" : "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
        });
        editingId = saved.id;
        heading.textContent = "编辑题目 #" + editingId;
        await loadPage(page);
        showToast("题目已保存。", "success");
    });
});

document.getElementById("new-problem").addEventListener("click", () => {
    status.textContent = "";
    newProblem();
});
document.getElementById("add-case").addEventListener("click", () => addCase());
newProblem();
loading.append(...["45%", "72%", "58%", "68%", "48%"].map(skeleton));
loadPage(1).then(() => {
    controls.hidden = false;
    controls.disabled = false;
    status.textContent = "";
}).catch(error => {
    status.textContent = error.message;
}).finally(() => {
    loading.hidden = true;
});
