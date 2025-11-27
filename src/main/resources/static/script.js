// script.js — robust custom dropdown + pagination + fetch/render + defensive hiding

/* -----------------------------
   Element refs & state
   ----------------------------- */
const eventsEl = document.getElementById('events');
const lastEl = document.getElementById('last');
const statusEl = document.getElementById('status');
const refreshBtn = document.getElementById('refreshBtn');
const filterCategory = document.getElementById('filterCategory');
const filterSource = document.getElementById('filterSource');

let allEvents = [];
const categories = new Set();
const sources = new Set();

let currentPage = 1;
const pageSize = 20;

/* =============================
   Custom dropdown (robust)
   ============================= */

function createCustomDropdown(nativeSelect, placeholder = 'Select') {
  if (!nativeSelect) return null;
  if (nativeSelect._customBtn) return nativeSelect._customBtn;

  console.debug('[custom-dd] creating for', nativeSelect.id || nativeSelect.name || nativeSelect);

  // create button
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'custom-dropdown-btn';
  btn.setAttribute('aria-haspopup', 'listbox');
  btn.setAttribute('aria-expanded', 'false');
  btn.style.minWidth = '160px';

  const updateBtnLabel = () => {
    const opt = nativeSelect.selectedOptions && nativeSelect.selectedOptions[0];
    btn.textContent = opt && opt.textContent ? opt.textContent : (nativeSelect.dataset.placeholder || placeholder);
  };
  updateBtnLabel();

  // floating menu appended to body
  const menu = document.createElement('div');
  menu.className = 'custom-dropdown-menu';
  menu.setAttribute('role', 'listbox');
  menu.style.display = 'none';
  menu.style.position = 'absolute';
  // enforce topmost
  menu.style.zIndex = '2147483647';
  menu.style.boxSizing = 'border-box';
  document.body.appendChild(menu);

  const rebuildMenu = () => {
    menu.innerHTML = '';
    const opts = Array.from(nativeSelect.options);
    if (opts.length === 0) {
      const e = document.createElement('div');
      e.className = 'custom-dropdown-empty';
      e.textContent = 'No options';
      menu.appendChild(e);
      return;
    }
    opts.forEach(o => {
      const item = document.createElement('div');
      item.className = 'custom-dropdown-item';
      item.setAttribute('role', 'option');
      item.dataset.value = o.value;
      item.textContent = o.textContent || o.value;
      if (o.disabled) {
        item.style.opacity = '0.5';
        item.style.pointerEvents = 'none';
      }
      if (nativeSelect.value === o.value) item.setAttribute('aria-selected', 'true');
      item.addEventListener('click', () => {
        nativeSelect.value = o.value;
        nativeSelect.dispatchEvent(new Event('change', { bubbles: true }));
        closeMenu();
        updateBtnLabel();
      });
      menu.appendChild(item);
    });
  };

  const positionMenu = () => {
    const rect = btn.getBoundingClientRect();
    menu.style.minWidth = Math.max(rect.width, 160) + 'px';
    // ensure we can measure menu height
    if (menu.style.display === 'none') {
      menu.style.display = 'block';
      menu.style.visibility = 'hidden';
    }
    const belowTop = rect.bottom + 6;
    const aboveTop = rect.top - 6 - menu.offsetHeight;
    const spaceBelow = window.innerHeight - rect.bottom;
    if (spaceBelow < 200 && aboveTop > 0) {
      menu.style.top = `${Math.max(8, aboveTop)}px`;
    } else {
      menu.style.top = `${Math.max(8, belowTop)}px`;
    }
    let left = rect.left;
    const maxLeft = window.innerWidth - menu.offsetWidth - 8;
    if (left > maxLeft) left = Math.max(8, maxLeft);
    menu.style.left = `${Math.max(8, left)}px`;
    if (menu.style.visibility === 'hidden') {
      menu.style.visibility = '';
      menu.style.display = 'none';
    }
  };

  const onWindowClick = (ev) => {
    if (ev.target === btn || btn.contains(ev.target) || menu.contains(ev.target)) return;
    closeMenu();
  };
  const onKeyDown = (ev) => {
    if (ev.key === 'Escape') { closeMenu(); btn.focus(); }
    if ((ev.key === 'ArrowDown' || ev.key === 'Enter' || ev.key === ' ') && menu.style.display === 'none') {
      ev.preventDefault();
      openMenu();
    }
  };

  const openMenu = () => {
    rebuildMenu();
    menu.style.display = 'block';
    btn.setAttribute('aria-expanded', 'true');
    setTimeout(positionMenu, 0);
    window.addEventListener('click', onWindowClick, true);
    window.addEventListener('resize', positionMenu);
    window.addEventListener('scroll', positionMenu, true);
    window.addEventListener('keydown', onKeyDown);
    console.debug('[custom-dd] opened for', nativeSelect.id || nativeSelect.name);
  };

  const closeMenu = () => {
    menu.style.display = 'none';
    btn.setAttribute('aria-expanded', 'false');
    window.removeEventListener('click', onWindowClick, true);
    window.removeEventListener('resize', positionMenu);
    window.removeEventListener('scroll', positionMenu, true);
    window.removeEventListener('keydown', onKeyDown);
    console.debug('[custom-dd] closed for', nativeSelect.id || nativeSelect.name);
  };

  btn.addEventListener('click', (ev) => {
    ev.stopPropagation();
    if (menu.style.display === 'none') openMenu();
    else closeMenu();
  });

  // hide native select using dedicated class (safer)
  nativeSelect.classList.add('_native-hidden');

  // and inline fallback styles to be extra robust
  nativeSelect.style.left = '-99999px';
  nativeSelect.style.top = '-99999px';
  nativeSelect.style.width = '0px';
  nativeSelect.style.height = '0px';
  nativeSelect.style.opacity = '0';
  nativeSelect.style.pointerEvents = 'none';
  nativeSelect.style.zIndex = '-1';

  // insert button after native select
  nativeSelect.insertAdjacentElement('afterend', btn);
  nativeSelect.addEventListener('change', updateBtnLabel);

  // defensive focus listener: if native select ever gets focused, re-hide it
  nativeSelect.addEventListener('focus', () => {
    console.warn('[debug] native select focused unexpectedly:', nativeSelect.id);
    nativeSelect.classList.add('_native-hidden');
    nativeSelect.style.pointerEvents = 'none';
  }, true);

  nativeSelect._rebuildCustom = rebuildMenu;
  nativeSelect._customBtn = btn;
  nativeSelect._customMenu = menu;

  return btn;
}

/* safe init that retries if DOM is still mutating */
function initCustomDropdownsGuaranteed() {
  const MAX_ATTEMPTS = 8;
  let attempt = 0;
  const tryInit = () => {
    attempt++;
    try {
      if (!filterCategory || !filterSource) {
        console.warn('[custom-dd] selects not yet present, retrying...', attempt);
        if (attempt < MAX_ATTEMPTS) setTimeout(tryInit, 150);
        return;
      }
      // create custom dropdowns
      createCustomDropdown(filterCategory, 'All Categories');
      createCustomDropdown(filterSource, 'All Sources');
      console.info('[custom-dd] initialized custom dropdowns');
      // observe changes to native selects (in case updateFilterDropdowns repopulates)
      observeSelectMutations(filterCategory);
      observeSelectMutations(filterSource);
    } catch (err) {
      console.error('[custom-dd] init error', err);
      if (attempt < MAX_ATTEMPTS) setTimeout(tryInit, 150);
    }
  };
  tryInit();
}

function observeSelectMutations(sel) {
  if (!sel) return;
  const mo = new MutationObserver((records) => {
    for (const r of records) {
      if (r.type === 'childList' || r.type === 'subtree') {
        if (sel._rebuildCustom) {
          sel._rebuildCustom();
          console.debug('[custom-dd] rebuild menu after mutation for', sel.id || sel.name);
        }
        break;
      }
    }
  });
  mo.observe(sel, { childList: true, subtree: true });
}

/* ===========================
   Main app logic (fetch/render)
   =========================== */

async function fetchEvents() {
  try {
    statusEl.textContent = '⏳';
    const params = new URLSearchParams();
    if (filterCategory && filterCategory.value) params.append('category', filterCategory.value);
    if (filterSource && filterSource.value) params.append('source', filterSource.value);
    const res = await fetch('/events?' + params.toString());
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const data = await res.json();
    allEvents = data;

    categories.clear();
    sources.clear();
    data.forEach(e => {
      if (e.category) categories.add(e.category);
      if (e.source) sources.add(e.source);
    });

    updateFilterDropdowns();
    currentPage = 1;
    renderEvents();
    statusEl.textContent = '✅';
    lastEl.textContent = '🕐 Last refresh: ' + new Date().toLocaleString();
  } catch (err) {
    console.error(err);
    statusEl.textContent = '❌';
    eventsEl.innerHTML = '<div class="card empty">⚠️ Error loading events. Check server console.</div>';
  }
}

function updateFilterDropdowns() {
  const currentCategory = filterCategory ? filterCategory.value : '';
  if (filterCategory) {
    filterCategory.innerHTML = '<option value="">All Categories</option>';
    Array.from(categories).sort().forEach(cat => {
      const opt = document.createElement('option');
      opt.value = cat;
      opt.textContent = cat;
      if (cat === currentCategory) opt.selected = true;
      filterCategory.appendChild(opt);
    });
    if (filterCategory._rebuildCustom) filterCategory._rebuildCustom();
  }

  const currentSource = filterSource ? filterSource.value : '';
  if (filterSource) {
    filterSource.innerHTML = '<option value="">All Sources</option>';
    Array.from(sources).sort().forEach(src => {
      const opt = document.createElement('option');
      opt.value = src;
      opt.textContent = src;
      if (src === currentSource) opt.selected = true;
      filterSource.appendChild(opt);
    });
    if (filterSource._rebuildCustom) filterSource._rebuildCustom();
  }
}

function renderEvents() {
  eventsEl.innerHTML = '';
  if (!Array.isArray(allEvents) || allEvents.length === 0) {
    eventsEl.innerHTML = '<div class="card empty">📭 No events right now.</div>';
    return;
  }

  let filteredList = allEvents.filter(e => {
    if (filterCategory && filterCategory.value && e.category !== filterCategory.value) return false;
    if (filterSource && filterSource.value && e.source !== filterSource.value) return false;
    return true;
  });

  filteredList.sort((a,b) => parseDate(b.date) - parseDate(a.date));

  const totalCount = filteredList.length;
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  if (currentPage > totalPages) currentPage = totalPages;
  if (currentPage < 1) currentPage = 1;
  const startIdx = (currentPage - 1) * pageSize;
  const endIdx = Math.min(startIdx + pageSize, totalCount);
  const pageList = filteredList.slice(startIdx, endIdx);

  const summaryCard = document.createElement('div');
  summaryCard.className = 'card summary-card';
  summaryCard.innerHTML = `
    <strong>📊 Current Alerts</strong>
    <div class="meta">
      Showing ${totalCount === 0 ? 0 : `${startIdx + 1}-${endIdx}`} of ${totalCount} event${totalCount !== 1 ? 's' : ''}
      ${filterCategory && filterCategory.value ? ` in category "${filterCategory.value}"` : ''}
      ${filterSource && filterSource.value ? ` from ${filterSource.value}` : ''}
    </div>
  `;
  eventsEl.appendChild(summaryCard);

  if (pageList.length === 0) {
    eventsEl.appendChild(document.createElement('div')).innerHTML = '<div class="card empty">📭 No events on this page.</div>';
  } else {
    for (const e of pageList) {
      const card = document.createElement('div');
      card.className = 'card';
      const catBadge = `<span class="badge">${escapeHtml(e.category || 'unknown')}</span>`;
      const sourceBadge = `<span class="badge source">${escapeHtml(e.source || '')}</span>`;
      const title = `<strong>${escapeHtml(e.title || 'Untitled')}</strong>`;
      const meta = `<div class="meta">${catBadge}${sourceBadge}<span>📅 ${formatDate(e.date)}</span></div>`;
      const latNum = Number(e.latitude || e.lat);
      const lonNum = Number(e.longitude || e.lon);
      let coords = '-';
      if (!Number.isNaN(latNum) && !Number.isNaN(lonNum)) {
        coords = `${Math.abs(latNum).toFixed(3)}°${latNum >= 0 ? 'N' : 'S'}, ${Math.abs(lonNum).toFixed(3)}°${lonNum >= 0 ? 'E' : 'W'}`;
      }
      const body = `<div class="event-details">
                      <div class="detail-item">
                        <span>🔗</span>
                        <a class="link" href="${escapeAttr(e.url)}" target="_blank" rel="noopener">View Source</a>
                      </div>
                      <div class="detail-item">
                        <span>📍</span>
                        <strong>${coords}</strong>
                      </div>
                      ${e.magnitude ? `<div class="detail-item"><span>📊</span><strong>Mag ${Number(e.magnitude).toFixed(1)}</strong></div>` : ''}
                      <div class="detail-item">
                        <button class="send-email-btn" data-id="${escapeAttr(e.id)}" data-source="${escapeAttr(e.source)}">✉️ Send Email</button>
                      </div>
                    </div>`;
      card.innerHTML = title + meta + body;
      eventsEl.appendChild(card);
    }
  }

  eventsEl.appendChild(renderPaginationControls(totalPages));
}

function renderPaginationControls(totalPages) {
  const wrap = document.createElement('div');
  wrap.className = 'pagination-wrap card';
  const prevDisabled = currentPage <= 1 ? 'disabled' : '';
  const nextDisabled = currentPage >= totalPages ? 'disabled' : '';
  wrap.innerHTML = `
    <div style="display:flex;gap:8px;align-items:center;justify-content:center;">
      <button class="page-btn" data-page="${currentPage-1}" ${prevDisabled}>&laquo; Prev</button>
      <span style="padding:8px 12px;color:var(--text-secondary)">Page ${currentPage} of ${totalPages}</span>
      <button class="page-btn" data-page="${currentPage+1}" ${nextDisabled}>Next &raquo;</button>
    </div>
  `;
  wrap.addEventListener('click', (ev) => {
    const btn = ev.target.closest('.page-btn');
    if (!btn) return;
    const p = Number(btn.getAttribute('data-page'));
    if (!isNaN(p)) gotoPage(p, totalPages);
  });
  return wrap;
}

function gotoPage(p, totalPages) {
  if (p < 1) p = 1;
  if (p > totalPages) p = totalPages;
  if (p === currentPage) return;
  currentPage = p;
  renderEvents();
}

/* utilities */
function parseDate(d) {
  if (!d) return 0;
  const num = Number(d);
  if (!Number.isNaN(num) && String(d).length >= 10) return (num > 1e12) ? num : num * 1;
  const t = Date.parse(d);
  return isNaN(t) ? 0 : t;
}
function formatDate(d) { const t = parseDate(d); if (!t) return '-'; return new Date(t).toLocaleString(); }
function escapeHtml(s) { if (!s) return ''; return String(s).replace(/[&<>"']/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function escapeAttr(s){ return escapeHtml(s).replace(/"/g,'&quot;'); }

/* -----------------------------
   Event listeners & startup
   ----------------------------- */

refreshBtn.addEventListener('click', () => { fetchEvents(); });
if (filterCategory) filterCategory.addEventListener('change', () => { currentPage = 1; renderEvents(); });
if (filterSource) filterSource.addEventListener('change', () => { currentPage = 1; renderEvents(); });

document.addEventListener('click', async function(e) {
  if (e.target && e.target.classList.contains('send-email-btn')) {
    const id = e.target.getAttribute('data-id');
    const source = e.target.getAttribute('data-source');
    if (!id || !source) return;
    e.target.disabled = true;
    e.target.textContent = 'Sending...';
    try {
      const res = await fetch(`/send-email?id=${encodeURIComponent(id)}&source=${encodeURIComponent(source)}`);
      const text = await res.text();
      if (res.ok) {
        e.target.textContent = 'Sent!';
        setTimeout(() => { e.target.textContent = '✉️ Send Email'; e.target.disabled = false; }, 2000);
      } else {
        e.target.textContent = 'Error';
        setTimeout(() => { e.target.textContent = '✉️ Send Email'; e.target.disabled = false; }, 2000);
      }
      alert(text);
    } catch (err) {
      e.target.textContent = 'Error';
      setTimeout(() => { e.target.textContent = '✉️ Send Email'; e.target.disabled = false; }, 2000);
      alert('Failed to send email.');
    }
  }
});

document.addEventListener('DOMContentLoaded', () => {
  console.info('[app] DOMContentLoaded - initializing custom dropdowns and fetching events');
  initCustomDropdownsGuaranteed();
  fetchEvents();
  setInterval(fetchEvents, 15000);
});
