const app = document.getElementById("app");

function route() {
  const path = location.pathname;
  const m = path.match(/^\/workspace\/([^/]+)\/?$/);
  if (m) {
    renderWorkspace(decodeURIComponent(m[1]));
  } else {
    renderHome();
  }
}

async function fetchJson(url) {
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || res.statusText);
  }
  return res.json();
}

function statusClass(status) {
  return `status ${status || ""}`;
}

async function renderHome() {
  app.innerHTML = `<div class="loading">正在加载今日排班…</div>`;
  try {
    const data = await fetchJson("/api/v1/or/schedule");
    const or = data.operatingRoom || {};
    const summary = data.summary || {};
    const cases = data.cases || [];

    app.innerHTML = `
      <header class="topbar">
        <div>
          <p class="brand">OR <span>Desk</span></p>
        </div>
        <div class="top-meta">
          <strong>${escapeHtml(or.name || "手术室")} · ${escapeHtml(or.id || "")}</strong>
          <div>${escapeHtml(or.building || "")} ${escapeHtml(or.floor || "")}</div>
          <div>排班日期 ${escapeHtml(data.date || "—")}</div>
        </div>
      </header>
      <p class="hero-line">今日手术排班。选择一台手术进入工作空间，管理视频源与输出目的地。</p>
      <div class="summary-row">
        <span class="chip">共 ${summary.total ?? cases.length} 台</span>
        <span class="chip">进行中 ${summary.inProgress ?? 0}</span>
        <span class="chip">待接台 ${summary.scheduled ?? 0}</span>
        <span class="chip">已完成 ${summary.done ?? 0}</span>
      </div>
      <section class="case-list" aria-label="手术列表">
        ${cases.map((c, i) => caseCard(c, i)).join("")}
      </section>
    `;

    app.querySelectorAll("[data-case-id]").forEach((el) => {
      el.addEventListener("click", () => {
        const id = el.getAttribute("data-case-id");
        history.pushState({}, "", `/workspace/${encodeURIComponent(id)}`);
        renderWorkspace(id);
      });
    });
  } catch (err) {
    app.innerHTML = `<div class="error">排班加载失败：${escapeHtml(err.message)}</div>`;
  }
}

function caseCard(c, index) {
  const delay = Math.min(index * 0.05, 0.3);
  return `
    <button class="case-card" data-case-id="${escapeHtml(c.id)}" style="animation-delay:${delay}s">
      <div class="case-time">
        ${escapeHtml(c.scheduledStart || "--:--")}
        <small>${escapeHtml(c.scheduledEnd ? `至 ${c.scheduledEnd}` : "")}</small>
      </div>
      <div class="case-main">
        <h2>${escapeHtml(c.patientName)} <span style="color:var(--muted);font-weight:500;font-size:0.9rem">${escapeHtml(c.patientGender || "")} ${c.patientAge ?? ""}岁</span></h2>
        <p>${escapeHtml(c.department || "")} · 主刀 ${escapeHtml(c.surgeon || "—")} · 麻醉 ${escapeHtml(c.anesthetist || "—")}</p>
        <p class="procedure">${escapeHtml(c.procedure || "")}</p>
      </div>
      <div class="case-side">
        <span class="${statusClass(c.status)}">${escapeHtml(c.statusLabel || c.status)}</span>
        <span class="btn btn-primary">进入工作空间</span>
      </div>
    </button>
  `;
}

async function renderWorkspace(caseId) {
  app.innerHTML = `<div class="loading">正在打开手术工作空间…</div>`;
  try {
    const ws = await fetchJson(`/api/v1/or/cases/${encodeURIComponent(caseId)}/workspace`);
    const c = ws.case || {};
    const p = ws.patient || {};
    const or = ws.operatingRoom || {};
    const sources = ws.sources || [];
    const destinations = ws.destinations || [];

    app.innerHTML = `
      <div class="ws-top">
        <div>
          <button class="btn btn-ghost" id="backBtn" type="button">← 返回排班</button>
          <h1 style="margin-top:14px">${escapeHtml(p.name || c.patientName || "")} · ${escapeHtml(c.procedure || "")}</h1>
          <div class="sub">
            ${escapeHtml(or.name || "")} · ${escapeHtml(c.id || "")} ·
            <span class="${statusClass(c.status)}">${escapeHtml(c.statusLabel || c.status)}</span>
          </div>
        </div>
        <div class="top-meta">
          <strong>OR Desk</strong>
          <div>计划 ${escapeHtml(c.scheduledStart || "—")}–${escapeHtml(c.scheduledEnd || "—")}</div>
          <div>实际开始 ${escapeHtml(c.actualStart || "—")}</div>
        </div>
      </div>
      <div class="ws-grid">
        <section class="panel" aria-label="患者与手术信息">
          <h3>患者与手术信息</h3>
          <dl class="info-block">
            ${infoRow("患者", `${p.name || ""}（${p.gender || ""} ${p.age ?? ""}岁）`)}
            ${infoRow("病例号", p.id || "—")}
            ${infoRow("床位", p.bed || "—")}
            ${infoRow("血型", p.bloodType || "—")}
            ${infoRow("诊断", p.diagnosis || "—")}
            ${infoRow("过敏", allergyHtml(p.allergies))}
            ${infoRow("术式", c.procedure || "—")}
            ${infoRow("科室", c.department || "—")}
            ${infoRow("主刀", c.surgeon || "—")}
            ${infoRow("麻醉", c.anesthetist || "—")}
            ${infoRow("器械", c.scrubNurse || "—")}
            ${infoRow("巡回", c.circulatingNurse || "—")}
            ${infoRow("备注", c.notes || "—")}
          </dl>
        </section>
        <section class="panel" aria-label="视频源">
          <h3>视频源</h3>
          <div class="endpoint-list">
            ${sources.map(sourceCard).join("") || emptyHint("暂无配置视频源")}
          </div>
        </section>
        <section class="panel" aria-label="输出目的地">
          <h3>输出目的地</h3>
          <div class="endpoint-list">
            ${destinations.map(destCard).join("") || emptyHint("暂无配置输出目的地")}
          </div>
        </section>
      </div>
    `;

    document.getElementById("backBtn").addEventListener("click", () => {
      history.pushState({}, "", "/");
      renderHome();
    });
  } catch (err) {
    app.innerHTML = `
      <button class="btn btn-ghost" id="backBtn" type="button">← 返回排班</button>
      <div class="error">工作空间加载失败：${escapeHtml(err.message)}</div>
    `;
    document.getElementById("backBtn")?.addEventListener("click", () => {
      history.pushState({}, "", "/");
      renderHome();
    });
  }
}

function infoRow(label, valueHtml) {
  return `<div class="info-row"><dt>${escapeHtml(label)}</dt><dd>${valueHtml}</dd></div>`;
}

function allergyHtml(allergies) {
  if (!allergies || !allergies.length) return "无";
  return allergies.map((a) => `<span class="allergy">${escapeHtml(a)}</span>`).join("");
}

function sourceCard(s) {
  const online = s.deviceStatus === "ONLINE";
  return `
    <article class="endpoint ${s.critical ? "critical" : ""}">
      <div>
        <h4>${escapeHtml(s.name)}</h4>
        <p>${escapeHtml(s.sourceType || "")} · ${escapeHtml(s.deviceId || "")}${s.location ? " · " + escapeHtml(s.location) : ""}</p>
        <p>${s.ipAddress ? escapeHtml(s.ipAddress) : "—"}${s.streamId ? " · " + escapeHtml(s.streamId) : ""}</p>
      </div>
      <div class="flags">
        <span class="flag ${online ? "ok" : "off"}">${escapeHtml(s.deviceStatus || "UNKNOWN")}</span>
        <span class="flag ${s.signalPresent ? "signal" : ""}">${s.signalPresent ? "有信号" : "无信号"}</span>
      </div>
    </article>
  `;
}

function destCard(d) {
  const online = d.deviceStatus === "ONLINE";
  return `
    <article class="endpoint ${d.ultraLowLatency ? "critical" : ""}">
      <div>
        <h4>${escapeHtml(d.name)}</h4>
        <p>${escapeHtml(d.role || "")} · ${escapeHtml(d.deviceId || "")}${d.location ? " · " + escapeHtml(d.location) : ""}</p>
        <p>${d.ipAddress ? escapeHtml(d.ipAddress) : "—"}${d.currentStreamId ? " · 当前 " + escapeHtml(d.currentStreamId) : ""}</p>
      </div>
      <div class="flags">
        <span class="flag ${online ? "ok" : "off"}">${escapeHtml(d.deviceStatus || "UNKNOWN")}</span>
        <span class="flag ${d.signalPresent ? "signal" : ""}">${d.signalPresent ? "有信号" : "无信号"}</span>
      </div>
    </article>
  `;
}

function emptyHint(text) {
  return `<p style="color:var(--muted)">${escapeHtml(text)}</p>`;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

window.addEventListener("popstate", route);
route();
