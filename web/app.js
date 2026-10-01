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

async function fetchJson(url, options) {
  const res = await fetch(url, options);
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { error: text };
  }
  if (!res.ok) {
    const err = new Error((data && data.error) || res.statusText);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
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
      <p class="hero-line">今日手术排班。选择一台手术进入工作空间，拖拽视频源到输出目的地完成路由。</p>
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

async function renderWorkspace(caseId, workspaceData) {
  if (!workspaceData) {
    app.innerHTML = `<div class="loading">正在打开手术工作空间…</div>`;
  }
  try {
    const ws = workspaceData || await fetchJson(`/api/v1/or/cases/${encodeURIComponent(caseId)}/workspace`);
    paintWorkspace(caseId, ws);
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

function paintWorkspace(caseId, ws) {
  const c = ws.case || {};
  const p = ws.patient || {};
  const or = ws.operatingRoom || {};
  const sources = ws.sources || [];
  const destinations = ws.destinations || [];
  const activeRoutes = ws.activeRoutes || [];

  app.innerHTML = `
    <div class="toast" id="toast" hidden></div>
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
        <div>活动路由 ${activeRoutes.length} 条</div>
      </div>
    </div>
    <p class="drag-hint">将左侧 <strong>视频源</strong> 拖到右侧 <strong>输出目的地</strong> 完成路由；目的地已有信号时可确认覆盖。</p>
    <div class="ws-grid">
      <section class="panel" aria-label="患者与手术信息">
        <h3>患者与手术信息</h3>
        <dl class="info-block">
          ${infoRow("患者", `${escapeHtml(p.name || "")}（${escapeHtml(p.gender || "")} ${p.age ?? ""}岁）`)}
          ${infoRow("病例号", escapeHtml(p.id || "—"))}
          ${infoRow("床位", escapeHtml(p.bed || "—"))}
          ${infoRow("血型", escapeHtml(p.bloodType || "—"))}
          ${infoRow("诊断", escapeHtml(p.diagnosis || "—"))}
          ${infoRow("过敏", allergyHtml(p.allergies))}
          ${infoRow("术式", escapeHtml(c.procedure || "—"))}
          ${infoRow("科室", escapeHtml(c.department || "—"))}
          ${infoRow("主刀", escapeHtml(c.surgeon || "—"))}
          ${infoRow("麻醉", escapeHtml(c.anesthetist || "—"))}
          ${infoRow("器械", escapeHtml(c.scrubNurse || "—"))}
          ${infoRow("巡回", escapeHtml(c.circulatingNurse || "—"))}
          ${infoRow("备注", escapeHtml(c.notes || "—"))}
        </dl>
      </section>
      <section class="panel" aria-label="视频源">
        <h3>视频源 <span class="panel-note">可拖拽</span></h3>
        <div class="endpoint-list" id="sourceList">
          ${sources.map(sourceCard).join("") || emptyHint("暂无配置视频源")}
        </div>
      </section>
      <section class="panel panel-dropzone" aria-label="输出目的地" id="destPanel">
        <h3>输出目的地 <span class="panel-note">放置此处</span></h3>
        <div class="endpoint-list" id="destList">
          ${destinations.map(destCard).join("") || emptyHint("暂无配置输出目的地")}
        </div>
      </section>
    </div>
  `;

  document.getElementById("backBtn").addEventListener("click", () => {
    history.pushState({}, "", "/");
    renderHome();
  });

  bindDragRouting(caseId);
}

function bindDragRouting(caseId) {
  let draggingSourceId = null;

  app.querySelectorAll(".endpoint.source[draggable='true']").forEach((el) => {
    el.addEventListener("dragstart", (e) => {
      draggingSourceId = el.getAttribute("data-source-id");
      el.classList.add("dragging");
      document.body.classList.add("is-dragging");
      e.dataTransfer.setData("text/plain", draggingSourceId);
      e.dataTransfer.effectAllowed = "copyMove";
    });
    el.addEventListener("dragend", () => {
      el.classList.remove("dragging");
      document.body.classList.remove("is-dragging");
      app.querySelectorAll(".endpoint.dest").forEach((d) => d.classList.remove("drop-hover"));
      draggingSourceId = null;
    });
  });

  app.querySelectorAll(".endpoint.dest").forEach((el) => {
    el.addEventListener("dragover", (e) => {
      e.preventDefault();
      e.dataTransfer.dropEffect = "copy";
      el.classList.add("drop-hover");
    });
    el.addEventListener("dragleave", () => el.classList.remove("drop-hover"));
    el.addEventListener("drop", async (e) => {
      e.preventDefault();
      el.classList.remove("drop-hover");
      const sourceId = e.dataTransfer.getData("text/plain") || draggingSourceId;
      const destinationId = el.getAttribute("data-dest-id");
      if (!sourceId || !destinationId) return;
      await applyRoute(caseId, sourceId, destinationId, false);
    });
  });

  app.querySelectorAll("[data-clear-dest]").forEach((btn) => {
    btn.addEventListener("click", async (e) => {
      e.stopPropagation();
      const destinationId = btn.getAttribute("data-clear-dest");
      try {
        const data = await fetchJson(
          `/api/v1/or/cases/${encodeURIComponent(caseId)}/routes/${encodeURIComponent(destinationId)}`,
          { method: "DELETE" }
        );
        showToast("已清除路由", "ok");
        paintWorkspace(caseId, data.workspace);
      } catch (err) {
        showToast(err.message, "error");
      }
    });
  });
}

async function applyRoute(caseId, sourceId, destinationId, confirmed) {
  try {
    const data = await fetchJson(`/api/v1/or/cases/${encodeURIComponent(caseId)}/routes`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        sourceId,
        destinationId,
        confirmed,
        operator: "or-desk-ui",
      }),
    });
    const route = data.route || {};
    showToast(`已路由：${route.sourceName || sourceId} → ${route.destinationName || destinationId}`, "ok");
    paintWorkspace(caseId, data.workspace);
  } catch (err) {
    if (err.status === 409 && err.data && err.data.conflict) {
      const existing = err.data.existing || {};
      const ok = window.confirm(
        `目的地当前为「${existing.sourceName || "其他源"}」。\n确认覆盖为新的视频源吗？`
      );
      if (ok) {
        await applyRoute(caseId, sourceId, destinationId, true);
      } else {
        showToast("已取消覆盖", "warn");
      }
      return;
    }
    showToast(err.message || "路由失败", "error");
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
  const canDrag = online;
  return `
    <article
      class="endpoint source ${s.critical ? "critical" : ""} ${canDrag ? "" : "disabled"}"
      draggable="${canDrag ? "true" : "false"}"
      data-source-id="${escapeHtml(s.id)}"
      title="${canDrag ? "拖到右侧目的地完成路由" : "设备离线，无法路由"}"
    >
      <div>
        <h4>${escapeHtml(s.name)}</h4>
        <p>${escapeHtml(s.sourceType || "")} · ${escapeHtml(s.deviceId || "")}${s.location ? " · " + escapeHtml(s.location) : ""}</p>
        <p>${s.ipAddress ? escapeHtml(s.ipAddress) : "—"}${s.streamId ? " · " + escapeHtml(s.streamId) : ""}</p>
      </div>
      <div class="flags">
        <span class="flag ${online ? "ok" : "off"}">${escapeHtml(s.deviceStatus || "UNKNOWN")}</span>
        <span class="flag ${s.signalPresent ? "signal" : ""}">${s.signalPresent ? "有信号" : "无信号"}</span>
        ${canDrag ? `<span class="flag drag">拖拽</span>` : ""}
      </div>
    </article>
  `;
}

function destCard(d) {
  const online = d.deviceStatus === "ONLINE";
  const routed = !!d.routed;
  return `
    <article
      class="endpoint dest ${d.ultraLowLatency ? "critical" : ""} ${routed ? "routed" : ""}"
      data-dest-id="${escapeHtml(d.id)}"
    >
      <div>
        <h4>${escapeHtml(d.name)}</h4>
        <p>${escapeHtml(d.role || "")} · ${escapeHtml(d.deviceId || "")}${d.location ? " · " + escapeHtml(d.location) : ""}</p>
        <p class="route-line">
          ${
            routed
              ? `← <strong>${escapeHtml(d.routedSourceName || d.routedSourceId)}</strong> · ${escapeHtml(d.currentStreamId || "")}`
              : d.currentStreamId
                ? `设备流 ${escapeHtml(d.currentStreamId)}`
                : "等待拖入视频源"
          }
        </p>
      </div>
      <div class="flags">
        <span class="flag ${online ? "ok" : "off"}">${escapeHtml(d.deviceStatus || "UNKNOWN")}</span>
        <span class="flag ${routed || d.signalPresent ? "signal" : ""}">${routed ? "已路由" : d.signalPresent ? "有信号" : "空闲"}</span>
        ${routed ? `<button type="button" class="btn-clear" data-clear-dest="${escapeHtml(d.id)}">清除</button>` : ""}
      </div>
    </article>
  `;
}

function emptyHint(text) {
  return `<p style="color:var(--muted)">${escapeHtml(text)}</p>`;
}

function showToast(message, kind) {
  const toast = document.getElementById("toast");
  if (!toast) return;
  toast.hidden = false;
  toast.className = `toast show ${kind || ""}`;
  toast.textContent = message;
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => {
    toast.classList.remove("show");
    toast.hidden = true;
  }, 2600);
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
