const currency = new Intl.NumberFormat("id-ID", {
  style: "currency",
  currency: "IDR",
  maximumFractionDigits: 0,
});

let liveMode = false;

function setConnectionStatus(text, state = "warning") {
  const target = document.querySelector("#connection-status");
  if (!target) return;
  target.textContent = text;
  target.classList.toggle("live", state === "live");
  target.classList.toggle("error", state === "error");
}

const chartData = [];
const transactions = [];
const expenses = [];
const payments = [];
const approvals = [];
const profitRows = [];
let stockItems = [];

let products = [];
let purchases = [];
let suppliers = [];
let returnsData = [];
let movements = [];
let settingsData = [];

function setBusy(isBusy, text = "Memuat data...") {
  const status = document.querySelector("#connection-status");
  if (!status) return;
  if (isBusy) {
    status.textContent = text;
    status.classList.remove("live", "error");
  }
}

function tableEmpty(colspan, text) {
  return `<tr><td class="empty-table-cell" colspan="${colspan}">${text}</td></tr>`;
}

function badge(status) {
  const normalized = status.toLowerCase();
  const cls = normalized.includes("berhasil") || normalized.includes("disetujui") || normalized.includes("completed")
    || normalized.includes("aman")
    ? "success"
    : normalized.includes("review") || normalized.includes("selisih")
      ? "review"
      : "pending";
  return `<span class="badge ${cls}">${status}</span>`;
}

function renderChart() {
  const target = document.querySelector("#cash-chart");
  if (chartData.length === 0) {
    target.innerHTML = `<div class="empty-state">Belum ada data omzet untuk periode ini.</div>`;
    return;
  }
  const max = Math.max(...chartData.map((item) => Math.max(item.revenue, item.profit, 0)), 1);
  target.innerHTML = chartData.map((item) => `
    <div class="bar-group" title="${item.day}: omzet ${item.revenue} juta, laba ${item.profit} juta">
      <div class="bars">
        <div class="bar revenue" style="height:${(Math.max(item.revenue, 0) / max) * 100}%"></div>
        <div class="bar profit" style="height:${(Math.max(item.profit, 0) / max) * 100}%"></div>
      </div>
      <div class="bar-label">${item.day}</div>
    </div>
  `).join("");
}

async function apiGet(path) {
  const response = await fetch(path, {
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(`API ${path} gagal: ${response.status}`);
  }
  return response.json();
}

async function apiPost(path, body) {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.error || `API ${path} gagal: ${response.status}`);
  }
  return response.json();
}

function authHeaders() {
  const token = localStorage.getItem("baletposFinanceToken");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function currentPeriod() {
  return document.querySelector("#period-select").value || "all";
}

function setMetricValue(selector, amount) {
  const target = document.querySelector(selector);
  if (!target) return;
  const value = Number(amount || 0);
  target.textContent = currency.format(value);
  target.classList.toggle("negative", value < 0);
}

function normalizeStatus(status) {
  if (status === "COMPLETED") return "Berhasil";
  if (status === "VOIDED") return "Dibatalkan";
  if (status === "RETURNED") return "Retur";
  return status || "Pending";
}

function renderTransactions() {
  const emptyRow = tableEmpty(5, "Belum ada data transaksi.");
  const fullEmptyRow = tableEmpty(6, "Belum ada data transaksi.");
  const rows = transactions.map((item) => `
    <tr>
      <td><strong>${item.invoice}</strong></td>
      <td>${item.time}</td>
      <td>${item.method}</td>
      <td class="right">${currency.format(item.total)}</td>
      <td>${badge(item.status)}</td>
    </tr>
  `).join("");
  document.querySelector("#transaction-table").innerHTML = rows || emptyRow;
  document.querySelector("#sales-table").innerHTML = transactions.map((item, index) => `
    <tr>
      <td><strong>${item.invoice}</strong></td>
      <td>${index % 2 === 0 ? "Rina" : "Admin Toko"}</td>
      <td>${item.method}</td>
      <td class="right">${currency.format(item.total)}</td>
      <td>${badge(item.status)}</td>
    </tr>
  `).join("") || emptyRow;
  document.querySelector("#full-transaction-table").innerHTML = transactions.map((item, index) => `
    <tr>
      <td><strong>${item.invoice}</strong></td>
      <td>${item.time}</td>
      <td>${item.cashier || (index % 2 === 0 ? "Rina" : "Admin Toko")}</td>
      <td>${item.method}</td>
      <td class="right">${currency.format(item.total)}</td>
      <td>${badge(item.status)}</td>
    </tr>
  `).join("") || fullEmptyRow;
}

function renderExpenses() {
  document.querySelector("#expense-table").innerHTML = expenses.map((item) => `
    <tr>
      <td><strong>${item.code}</strong></td>
      <td>${item.category}</td>
      <td>${item.note}</td>
      <td class="right">${currency.format(item.total)}</td>
      <td>${badge(item.status)}</td>
    </tr>
  `).join("") || tableEmpty(5, "Belum ada data biaya.");
}

function renderPayments() {
  document.querySelector("#payment-list").innerHTML = payments.map((item) => `
    <div class="payment-item">
      <strong>${item.name}</strong>
      <span>${currency.format(item.amount)}</span>
      <div class="payment-track"><i style="width:${item.pct}%"></i></div>
    </div>
  `).join("") || `<div class="empty-state">Belum ada pembayaran untuk periode ini.</div>`;
}

function renderApprovals() {
  document.querySelector("#approval-list").innerHTML = approvals.map((item) => `
    <div class="approval-item">
      <strong>${item.title}</strong>
      <span>${item.meta}</span>
      ${badge(item.status)}
    </div>
  `).join("") || `<div class="empty-state">Belum ada biaya menunggu review.</div>`;
}

function renderStatement() {
  document.querySelector("#profit-statement").innerHTML = profitRows.map(([label, amount, type]) => `
    <div class="statement-row ${type === "total" ? "total" : ""}">
      <strong>${label}</strong>
      <span>${currency.format(amount)}</span>
    </div>
  `).join("") || `<div class="empty-state">Belum ada laporan laba rugi untuk periode ini.</div>`;
}

function renderProducts() {
  document.querySelector("#product-table").innerHTML = products.map((item) => `
    <tr>
      <td><strong>${item.sku}</strong></td>
      <td>${item.name}</td>
      <td>${item.type}</td>
      <td class="right">${item.stock}</td>
      <td class="right">${currency.format(Number(item.selling_price || 0))}</td>
    </tr>
  `).join("") || tableEmpty(5, "Belum ada data produk.");
}

function renderPurchases() {
  document.querySelector("#purchase-table").innerHTML = purchases.map((item) => `
    <tr>
      <td><strong>${item.number}</strong></td>
      <td>${new Date(item.date).toLocaleDateString("id-ID")}</td>
      <td>${item.supplier}</td>
      <td class="right">${currency.format(Number(item.total || 0))}</td>
      <td>${badge(item.status || "Completed")}</td>
    </tr>
  `).join("") || tableEmpty(5, "Belum ada data pembelian.");
}

function renderSuppliers() {
  document.querySelector("#supplier-table").innerHTML = suppliers.map((item) => `
    <tr>
      <td><strong>${item.code}</strong></td>
      <td>${item.name}</td>
      <td>${item.contact || "-"}</td>
      <td>${item.phone || "-"}</td>
      <td>${item.email || "-"}</td>
    </tr>
  `).join("") || tableEmpty(5, "Belum ada data supplier.");
}

function renderReturns() {
  document.querySelector("#return-table").innerHTML = returnsData.map((item) => `
    <tr>
      <td><strong>${item.number}</strong></td>
      <td>${item.invoice}</td>
      <td>${new Date(item.date).toLocaleDateString("id-ID")}</td>
      <td class="right">${currency.format(Number(item.total || 0))}</td>
      <td>${badge(item.status || "Completed")}</td>
    </tr>
  `).join("") || tableEmpty(5, "Belum ada data retur.");

  document.querySelector("#movement-table").innerHTML = movements.map((item) => `
    <tr>
      <td>${new Date(item.date).toLocaleString("id-ID")}</td>
      <td>${item.product}</td>
      <td>${item.type}</td>
      <td class="right">${Number(item.qty || 0)}</td>
    </tr>
  `).join("") || tableEmpty(4, "Belum ada data mutasi stok.");
}

function renderSettings() {
  document.querySelector("#setting-table").innerHTML = settingsData.map((item) => `
    <tr>
      <td><strong>${item.key}</strong></td>
      <td>${item.value}</td>
      <td>${item.description || "-"}</td>
    </tr>
  `).join("") || tableEmpty(3, "Belum ada data setting.");
}

async function loadLiveData() {
  try {
    setBusy(true);
    const period = currentPeriod();
    const [health, dashboard, salesData, expensesData, profitData, stockData, productData, purchaseData, supplierData, returnsPayload, settingsPayload] = await Promise.all([
      apiGet("/api/health"),
      apiGet(`/api/dashboard?period=${period}`),
      apiGet(`/api/sales?period=${period}`),
      apiGet(`/api/expenses?period=${period}`),
      apiGet(`/api/profit?period=${period}`),
      apiGet("/api/stock"),
      apiGet("/api/products"),
      apiGet(`/api/purchases?period=${period}`),
      apiGet("/api/suppliers"),
      apiGet("/api/returns"),
      apiGet("/api/settings"),
    ]);

    liveMode = true;
    setConnectionStatus(`Live DB: ${health.counts.sales} sales, ${health.counts.products} produk`, "live");

    const summary = dashboard.summary || {};
    setMetricValue("#net-revenue", summary.net_revenue);
    setMetricValue("#gross-profit", summary.gross_profit);
    setMetricValue("#expense-total", summary.expenses);
    setMetricValue("#net-profit", summary.net_profit);

    chartData.splice(0, chartData.length, ...(dashboard.trend || []).map((item) => ({
      day: item.day,
      revenue: Number(item.revenue || 0) / 1_000_000,
      profit: Number(item.profit || 0) / 1_000_000,
    })));

    const liveTransactions = (salesData.sales && salesData.sales.length > 0)
      ? salesData.sales
      : (dashboard.transactions || []);
    transactions.splice(0, transactions.length, ...liveTransactions.map((item) => ({
      invoice: item.invoice,
      time: item.time ? new Date(item.time).toLocaleTimeString("id-ID", { hour: "2-digit", minute: "2-digit" }) : "-",
      cashier: item.cashier,
      method: item.method,
      total: Number(item.total || 0),
      status: normalizeStatus(item.status),
    })));

    payments.splice(0, payments.length, ...(dashboard.payments || []).map((item) => ({
      name: item.name,
      amount: Number(item.amount || 0),
      pct: 0,
    })));
    const maxPayment = Math.max(...payments.map((item) => item.amount), 1);
    payments.forEach((item) => {
      item.pct = Math.round((item.amount / maxPayment) * 100);
    });

    approvals.splice(0, approvals.length, ...(dashboard.approvals || []));
    expenses.splice(0, expenses.length, ...(expensesData.expenses || []).map((item) => ({
      code: item.code,
      category: item.category,
      note: item.note || "-",
      total: Number(item.total || 0),
      status: item.status,
    })));
    profitRows.splice(0, profitRows.length, ...(profitData.rows || []));
    stockItems = (stockData.stock || []).map((item) => ({
      sku: item.sku,
      name: item.name,
      system: Number(item.system || 0),
      physical: Number(item.physical || 0),
      hpp: Number(item.hpp || 0),
      status: "Aman",
      note: "",
    }));
    products = productData.products || [];
    purchases = purchaseData.purchases || [];
    suppliers = supplierData.suppliers || [];
    returnsData = returnsPayload.returns || [];
    movements = returnsPayload.movements || [];
    settingsData = settingsPayload.settings || [];

    renderChart();
    renderTransactions();
    renderExpenses();
    renderPayments();
    renderApprovals();
    renderStatement();
    renderProducts();
    renderPurchases();
    renderSuppliers();
    renderReturns();
    renderSettings();
    renderStockOptions();
    renderStock();
  } catch (error) {
    liveMode = false;
    console.warn("Live DB belum aktif.", error);
    setConnectionStatus("DB gagal tersambung", "error");
    throw error;
  }
}

function stockStatus(item) {
  const diff = item.physical - item.system;
  if (diff !== 0) return "Selisih";
  if (item.system <= 5) return "Kritis";
  return "Aman";
}

function renderStock() {
  const filter = document.querySelector("#stock-filter")?.value || "all";
  const query = (document.querySelector("#stock-search")?.value || "").trim().toLowerCase();
  const items = stockItems
    .map((item) => ({ ...item, status: stockStatus(item), diff: item.physical - item.system }))
    .filter((item) => {
      const matchesQuery = `${item.sku} ${item.name}`.toLowerCase().includes(query);
      const matchesFilter = filter === "all"
        || (filter === "critical" && item.status === "Kritis")
        || (filter === "diff" && item.diff !== 0);
      return matchesQuery && matchesFilter;
    });

  document.querySelector("#stock-table").innerHTML = items.map((item) => `
    <tr>
      <td><strong>${item.sku}</strong></td>
      <td>${item.name}</td>
      <td class="right">${item.system}</td>
      <td class="right">${item.physical}</td>
      <td class="right">${item.diff > 0 ? "+" : ""}${item.diff}</td>
      <td>${badge(item.status)}</td>
    </tr>
  `).join("") || tableEmpty(6, "Belum ada data stok.");

  const critical = stockItems.filter((item) => stockStatus(item) === "Kritis").length;
  const counted = stockItems.filter((item) => Number.isFinite(item.physical)).length;
  const diffValue = stockItems.reduce((total, item) => {
    return total + Math.abs(item.physical - item.system) * item.hpp;
  }, 0);

  document.querySelector("#stock-sku-total").textContent = String(stockItems.length);
  document.querySelector("#stock-critical-total").textContent = String(critical);
  document.querySelector("#stock-counted-total").textContent = stockItems.length === 0
    ? "0%"
    : `${Math.round((counted / stockItems.length) * 100)}%`;
  document.querySelector("#stock-diff-total").textContent = currency.format(diffValue);
  renderStockAlerts();
}

function renderStockOptions() {
  const select = document.querySelector("#stock-sku");
  select.innerHTML = stockItems.map((item) => `
    <option value="${item.sku}">${item.sku} - ${item.name}</option>
  `).join("") || `<option value="">Belum ada produk</option>`;
}

function renderStockAlerts() {
  const alerts = stockItems
    .map((item) => ({ ...item, status: stockStatus(item), diff: item.physical - item.system }))
    .filter((item) => item.status !== "Aman")
    .slice(0, 4);
  document.querySelector("#stock-alerts").innerHTML = alerts.map((item) => `
    <div class="stock-alert">
      <strong>${item.status}: ${item.name}</strong>
      <span>${item.sku} - Sistem ${item.system}, fisik ${item.physical}, selisih ${item.diff}</span>
    </div>
  `).join("") || `<div class="empty-state">Tidak ada stok kritis atau selisih.</div>`;
}

function setView(viewId) {
  document.querySelectorAll(".view").forEach((view) => view.classList.remove("active-view"));
  document.querySelector(`#${viewId}`).classList.add("active-view");
  document.querySelectorAll(".nav a").forEach((link) => {
    link.classList.toggle("active", link.dataset.view === viewId);
  });
  const titles = {
    dashboard: "Dashboard Keuangan",
    products: "Produk",
    purchases: "Pembelian",
    suppliers: "Supplier",
    returns: "Retur / Mutasi",
    profit: "Laba Rugi",
    sales: "Laporan Penjualan",
    transactions: "Transaksi",
    expenses: "Laporan Biaya",
    stock: "Stock Opname",
    reconcile: "Rekonsiliasi Kas",
    settings: "Setting",
  };
  document.querySelector("#page-title").textContent = titles[viewId];
}

function setupNavigation() {
  document.querySelectorAll(".nav a").forEach((link) => {
    link.addEventListener("click", (event) => {
      event.preventDefault();
      setView(link.dataset.view);
      history.replaceState(null, "", link.getAttribute("href"));
    });
  });
}

function setupSearch() {
  const input = document.querySelector("#global-search");
  input.addEventListener("input", () => {
    const query = input.value.trim().toLowerCase();
    document.querySelectorAll("tbody tr").forEach((row) => {
      row.hidden = query.length > 0 && !row.textContent.toLowerCase().includes(query);
    });
  });
}

function setupExport() {
  document.querySelector("#export-btn").addEventListener("click", () => {
    const header = "invoice,waktu,metode,total,status";
    const body = transactions.map((item) => [
      item.invoice,
      item.time,
      item.method,
      item.total,
      item.status,
    ].join(","));
    const blob = new Blob([[header, ...body].join("\n")], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "baletpos-laporan-penjualan.csv";
    link.click();
    URL.revokeObjectURL(url);
  });

  document.querySelector("#export-stock-btn").addEventListener("click", () => {
    const header = "sku,produk,stok_sistem,stok_fisik,selisih,status,catatan";
    const body = stockItems.map((item) => {
      const diff = item.physical - item.system;
      return [
        item.sku,
        item.name,
        item.system,
        item.physical,
        diff,
        stockStatus(item),
        item.note,
      ].join(",");
    });
    const blob = new Blob([[header, ...body].join("\n")], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "baletpos-stock-opname.csv";
    link.click();
    URL.revokeObjectURL(url);
  });
}

function setupReconcile() {
  document.querySelector("#reconcile-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const systemCash = Number(form.get("systemCash") || 0);
    const actualCash = Number(form.get("actualCash") || 0);
    const diff = actualCash - systemCash;
    const output = document.querySelector("#cash-diff");
    output.textContent = currency.format(diff);
    output.style.color = diff === 0 ? "var(--good)" : "var(--danger)";
  });
}

function setupStockOpname() {
  document.querySelector("#stock-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const sku = form.get("sku");
    const item = stockItems.find((entry) => entry.sku === sku);
    if (!item) return;

    item.physical = Number(form.get("physical") || 0);
    item.note = String(form.get("note") || "");
    renderStock();

    if (liveMode) {
      apiPost("/api/stock", {
        sku,
        physical: item.physical,
        note: item.note,
      })
        .then(loadLiveData)
        .catch((error) => alert(error.message));
    }
  });

  document.querySelector("#stock-filter").addEventListener("change", renderStock);
  document.querySelector("#stock-search").addEventListener("input", renderStock);
  document.querySelector("#stock-sku").addEventListener("change", (event) => {
    const item = stockItems.find((entry) => entry.sku === event.target.value);
    if (!item) return;
    document.querySelector("#stock-form input[name='physical']").value = item.physical;
    document.querySelector("#stock-form textarea[name='note']").value = item.note;
  });
}

function setupManagementForms() {
  document.querySelector("#product-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await apiPost("/api/products", {
        sku: form.get("sku"),
        name: form.get("name"),
        type: form.get("type"),
        hpp: form.get("hpp"),
        sellingPrice: form.get("sellingPrice"),
        stock: form.get("stock"),
      });
      event.currentTarget.reset();
      await loadLiveData();
    } catch (error) {
      alert(error.message);
    }
  });

  document.querySelector("#supplier-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await apiPost("/api/suppliers", {
        code: form.get("code"),
        name: form.get("name"),
        contact: form.get("contact"),
        phone: form.get("phone"),
        email: form.get("email"),
        address: form.get("address"),
      });
      event.currentTarget.reset();
      await loadLiveData();
    } catch (error) {
      alert(error.message);
    }
  });
}

function showApp() {
  document.querySelector("#login-screen").classList.add("is-hidden");
  document.querySelector("#app-shell").classList.remove("is-hidden");
  const user = JSON.parse(localStorage.getItem("baletposFinanceUser") || "null");
  const label = document.querySelector("#active-user-label");
  if (label && user) {
    label.textContent = user.fullName || user.username || "Admin Keuangan";
  }
}

function showLogin() {
  document.querySelector("#login-screen").classList.remove("is-hidden");
  document.querySelector("#app-shell").classList.add("is-hidden");
}

function setupAuth() {
  const isLoggedIn = localStorage.getItem("baletposFinanceLoggedIn") === "true"
    && Boolean(localStorage.getItem("baletposFinanceToken"));
  if (isLoggedIn) {
    showApp();
  } else {
    showLogin();
  }

  document.querySelector("#login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const username = String(form.get("username") || "").trim();
    const password = String(form.get("password") || "");
    const error = document.querySelector("#login-error");

    try {
      error.textContent = "";
      setBusy(true, "Login...");
      const result = await apiPost("/api/login", { username, password });
      localStorage.setItem("baletposFinanceLoggedIn", "true");
      localStorage.setItem("baletposFinanceUser", JSON.stringify(result.user));
      localStorage.setItem("baletposFinanceToken", result.token);
      error.textContent = "";
      showApp();
      await loadLiveData();
      return;
    } catch (apiError) {
      console.warn("Login DB gagal.", apiError);
      localStorage.removeItem("baletposFinanceLoggedIn");
      localStorage.removeItem("baletposFinanceUser");
      localStorage.removeItem("baletposFinanceToken");
      showLogin();
      error.textContent = apiError.message || "Login gagal. Pastikan akun role Admin Keuangan dan DATABASE_URL memakai DB desktop yang sama.";
    }
  });

  document.querySelector("#logout-btn").addEventListener("click", () => {
    localStorage.removeItem("baletposFinanceLoggedIn");
    localStorage.removeItem("baletposFinanceUser");
    localStorage.removeItem("baletposFinanceToken");
    showLogin();
  });
}

function initializeApp() {
  renderChart();
  renderTransactions();
  renderExpenses();
  renderPayments();
  renderApprovals();
  renderStatement();
  renderProducts();
  renderPurchases();
  renderSuppliers();
  renderReturns();
  renderSettings();
  renderStockOptions();
  renderStock();
  setupNavigation();
  setupSearch();
  setupExport();
  setupReconcile();
  setupStockOpname();
  setupManagementForms();
  setupAuth();

  // sidebar toggle
  const appShell = document.querySelector("#app-shell");
  const toggleBtn = document.querySelector("#sidebar-toggle");
  if (appShell && toggleBtn) {
    // Sidebar: paksa default normal tiap load.
    // Ini penting supaya saat refresh tombol toggle tidak menghilang/ikut ketutup.
    appShell.classList.remove("is-sidebar-collapsed");
    localStorage.removeItem("baletposFinanceSidebarCollapsed");


    toggleBtn.addEventListener("click", () => {
      const isCollapsed = appShell.classList.toggle("is-sidebar-collapsed");
      localStorage.setItem("baletposFinanceSidebarCollapsed", String(isCollapsed));
    });
  }

  document.querySelector("#period-select").addEventListener("change", loadLiveData);

  const initialView = location.hash.replace("#", "");
  if (["dashboard", "products", "purchases", "suppliers", "returns", "profit", "sales", "transactions", "expenses", "stock", "reconcile", "settings"].includes(initialView)) {
    setView(initialView);
  }

  if (localStorage.getItem("baletposFinanceLoggedIn") === "true") {
    loadLiveData().catch(() => {
      localStorage.removeItem("baletposFinanceLoggedIn");
      localStorage.removeItem("baletposFinanceUser");
      localStorage.removeItem("baletposFinanceToken");
      showLogin();
    });
  }
}

initializeApp();

