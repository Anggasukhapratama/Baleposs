"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  LayoutDashboard,
  Package,
  ShoppingCart,
  Users,
  ArrowLeftRight,
  DollarSign,
  FileText,
  ClipboardList,
  Scale,
  Settings,
  LogOut,
  Search,
  RefreshCw,
  TrendingUp,
  TrendingDown,
  Info,
  Calendar,
  AlertTriangle,
  CheckCircle,
  Plus,
  Loader2,
  Lock,
  Menu,
  X,
  ChevronLeft,
  ChevronRight
} from "lucide-react";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer
} from "recharts";

// Currency formatter
const formatCurrency = (value: number) => {
  return new Intl.NumberFormat("id-ID", {
    style: "currency",
    currency: "IDR",
    maximumFractionDigits: 0,
  }).format(value);
};

// Time relative formatter
const formatRelativeTime = (dateString: string) => {
  if (!dateString) return "-";
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);

  if (diffMins < 1) return "Baru saja";
  if (diffMins < 60) return `${diffMins} menit yang lalu`;

  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return `${diffHours} jam yang lalu`;

  return date.toLocaleDateString("id-ID", {
    hour: "2-digit",
    minute: "2-digit",
  }) + " WIB";
};

// Reusable Pagination component
interface PaginationControlProps {
  currentPage: number;
  totalItems: number;
  itemsPerPage: number;
  onPageChange: (page: number) => void;
}

const PaginationControl = ({
  currentPage,
  totalItems,
  itemsPerPage,
  onPageChange,
}: PaginationControlProps) => {
  const totalPages = Math.ceil(totalItems / itemsPerPage);
  if (totalPages <= 1) return null;

  const startIdx = (currentPage - 1) * itemsPerPage + 1;
  const endIdx = Math.min(currentPage * itemsPerPage, totalItems);

  // Generate page numbers to display
  const getPageNumbers = () => {
    const pages = [];
    const maxVisible = 5;
    if (totalPages <= maxVisible) {
      for (let i = 1; i <= totalPages; i++) pages.push(i);
    } else {
      pages.push(1);
      if (currentPage > 3) {
        pages.push("...");
      }
      const start = Math.max(2, currentPage - 1);
      const end = Math.min(totalPages - 1, currentPage + 1);
      for (let i = start; i <= end; i++) {
        pages.push(i);
      }
      if (currentPage < totalPages - 2) {
        pages.push("...");
      }
      pages.push(totalPages);
    }
    return pages;
  };

  return (
    <div className="p-4 border-t border-slate-800/60 bg-slate-900/40 flex flex-col sm:flex-row items-center justify-between gap-4">
      <div className="text-xs text-slate-400 font-semibold">
        Menampilkan <span className="text-slate-200 font-bold">{startIdx} - {endIdx}</span> dari <span className="text-slate-200 font-bold">{totalItems}</span> data
      </div>
      <div className="flex items-center gap-1.5">
        <button
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage === 1}
          className="p-2 border border-slate-800 hover:bg-slate-800 disabled:opacity-30 disabled:hover:bg-transparent rounded-lg text-slate-300 transition cursor-pointer"
        >
          <ChevronLeft size={16} />
        </button>
        {getPageNumbers().map((p, idx) => {
          if (p === "...") {
            return (
              <span key={idx} className="px-3 py-1.5 text-xs font-bold text-slate-600">
                ...
              </span>
            );
          }
          const isSelected = p === currentPage;
          return (
            <button
              key={idx}
              onClick={() => onPageChange(p as number)}
              className={`px-3 py-1.5 text-xs font-bold rounded-lg transition cursor-pointer ${
                isSelected
                  ? "bg-blue-600 text-white shadow-md shadow-blue-600/10"
                  : "border border-slate-800 text-slate-400 hover:bg-slate-800 hover:text-slate-200"
              }`}
            >
              {p}
            </button>
          );
        })}
        <button
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage === totalPages}
          className="p-2 border border-slate-800 hover:bg-slate-800 disabled:opacity-30 disabled:hover:bg-transparent rounded-lg text-slate-300 transition cursor-pointer"
        >
          <ChevronRight size={16} />
        </button>
      </div>
    </div>
  );
};

export default function Home() {
  const [mounted, setMounted] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<{ username: string; fullName: string; role: string } | null>(null);
  const [activeView, setActiveView] = useState("dashboard");
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // Global filters
  const [searchQuery, setSearchQuery] = useState("");
  const [period, setPeriod] = useState("all");

  // Pagination states
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 10;

  // Reset page when filter or view changes
  useEffect(() => {
    setCurrentPage(1);
  }, [activeView, period, searchQuery]);

  // Data states
  const [dashboardData, setDashboardData] = useState<any>({
    summary: { net_revenue: 0, gross_profit: 0, expenses: 0, net_profit: 0 },
    trends: { net_revenue: 0, gross_profit: 0, expenses: 0, net_profit: 0 },
    payments: [],
    trend: [],
    transactions: [],
    approvals: []
  });
  const [products, setProducts] = useState<any[]>([]);
  const [purchases, setPurchases] = useState<any[]>([]);
  const [suppliers, setSuppliers] = useState<any[]>([]);
  const [returnsData, setReturnsData] = useState<any>({ returns: [], movements: [] });
  const [expenses, setExpenses] = useState<any[]>([]);
  const [stock, setStock] = useState<any[]>([]);
  const [settings, setSettings] = useState<any[]>([]);
  const [profitRows, setProfitRows] = useState<any[]>([]);

  // Page loading & form feedback states
  const [loading, setLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [connStatus, setConnStatus] = useState<"connecting" | "live" | "error">("connecting");
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  // Login form state
  const [loginUsername, setLoginUsername] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [loginError, setLoginError] = useState("");

  // Reconcile Form state
  const [reconcileSystemCash, setReconcileSystemCash] = useState(0);
  const [reconcileActualCash, setReconcileActualCash] = useState(0);
  const [reconcileNote, setReconcileNote] = useState("");
  const [reconcileDiff, setReconcileDiff] = useState<number | null>(null);

  // Product Form state
  const [newProdSku, setNewProdSku] = useState("");
  const [newProdName, setNewProdName] = useState("");
  const [newProdType, setNewProdType] = useState("LAPTOP_NEW");
  const [newProdHpp, setNewProdHpp] = useState(0);
  const [newProdPrice, setNewProdPrice] = useState(0);
  const [newProdStock, setNewProdStock] = useState(0);

  // Supplier Form state
  const [newSupCode, setNewSupCode] = useState("");
  const [newSupName, setNewSupName] = useState("");
  const [newSupContact, setNewSupContact] = useState("");
  const [newSupPhone, setNewSupPhone] = useState("");
  const [newSupEmail, setNewSupEmail] = useState("");
  const [newSupAddress, setNewSupAddress] = useState("");

  // Stock Opname Form state
  const [opnameSku, setOpnameSku] = useState("");
  const [opnamePhysical, setOpnamePhysical] = useState(0);
  const [opnameNote, setOpnameNote] = useState("");
  const [opnameAlerts, setOpnameAlerts] = useState<any[]>([]);

  // Fetch all endpoints based on active view and global filters
  const fetchData = useCallback(async (isSilent = false) => {
    if (!token) return;
    if (!isSilent) setLoading(true);
    setConnStatus("connecting");
    try {
      const headers = { Authorization: `Bearer ${token}` };

      // Helper for clean API call
      const getApi = async (path: string) => {
        const res = await fetch(path, { headers });
        if (!res.ok) throw new Error(`Fetch ${path} failed: ${res.status}`);
        return res.json();
      };

      if (activeView === "dashboard") {
        const data = await getApi(`/api/dashboard?period=${period}&search=${encodeURIComponent(searchQuery)}`);
        setDashboardData(data);
      } else if (activeView === "products") {
        const data = await getApi("/api/products");
        setProducts(data.products || []);
      } else if (activeView === "purchases") {
        const data = await getApi(`/api/purchases?period=${period}`);
        setPurchases(data.purchases || []);
      } else if (activeView === "suppliers") {
        const data = await getApi("/api/suppliers");
        setSuppliers(data.suppliers || []);
      } else if (activeView === "returns") {
        const data = await getApi("/api/returns");
        setReturnsData(data);
      } else if (activeView === "expenses") {
        const data = await getApi(`/api/expenses?period=${period}`);
        setExpenses(data.expenses || []);
      } else if (activeView === "stock") {
        const data = await getApi("/api/stock");
        setStock(data.stock || []);
      } else if (activeView === "settings") {
        const data = await getApi("/api/settings");
        setSettings(data.settings || []);
      } else if (activeView === "profit") {
        const data = await getApi(`/api/profit?period=${period}`);
        setProfitRows(data.rows || []);
      } else if (activeView === "transactions" || activeView === "sales") {
        const data = await getApi(`/api/sales?period=${period}`);
        setProducts(data.sales || []); // Reuse products array for generic sales view rendering
      }

      setConnStatus("live");
      setLastUpdated(new Date());
      setError(null);
    } catch (err: any) {
      console.error(err);
      setConnStatus("error");
      setError(err.message || "Gagal memuat data dari database");
    } finally {
      if (!isSilent) setLoading(false);
    }
  }, [token, activeView, period, searchQuery]);

  // Sync effect when filters or views change
  useEffect(() => {
    if (token) {
      fetchData();
    }
  }, [token, activeView, period, searchQuery, fetchData]);

  // Polling every 10 seconds for real-time update
  useEffect(() => {
    if (!token) return;
    const interval = setInterval(() => {
      fetchData(true);
    }, 10000);
    return () => clearInterval(interval);
  }, [token, fetchData]);

  // Initialize and check token
  useEffect(() => {
    setMounted(true);
    const storedToken = localStorage.getItem("baletposFinanceToken");
    const storedUser = localStorage.getItem("baletposFinanceUser");
    if (storedToken && storedUser) {
      setToken(storedToken);
      setUser(JSON.parse(storedUser));
    }
  }, []);

  if (!mounted) return null;

  // Handle Login submission
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoginError("");
    setLoading(true);
    try {
      const res = await fetch("/api/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: loginUsername, password: loginPassword }),
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || "Login gagal");
      }
      localStorage.setItem("baletposFinanceToken", data.token);
      localStorage.setItem("baletposFinanceUser", JSON.stringify(data.user));
      setToken(data.token);
      setUser(data.user);
    } catch (err: any) {
      setLoginError(err.message || "Terjadi kesalahan sistem.");
    } finally {
      setLoading(false);
    }
  };

  // Handle Logout
  const handleLogout = () => {
    localStorage.removeItem("baletposFinanceToken");
    localStorage.removeItem("baletposFinanceUser");
    setToken(null);
    setUser(null);
    setActiveView("dashboard");
  };

  // Submit new product
  const handleAddProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    try {
      const res = await fetch("/api/products", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({
          sku: newProdSku,
          name: newProdName,
          type: newProdType,
          hpp: newProdHpp,
          sellingPrice: newProdPrice,
          stock: newProdStock
        })
      });
      if (!res.ok) throw new Error("Gagal menambah produk");
      // Reset form
      setNewProdSku("");
      setNewProdName("");
      setNewProdHpp(0);
      setNewProdPrice(0);
      setNewProdStock(0);
      fetchData();
      alert("Produk berhasil ditambahkan!");
    } catch (err: any) {
      alert(err.message);
    } finally {
      setIsSaving(false);
    }
  };

  // Submit new supplier
  const handleAddSupplier = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    try {
      const res = await fetch("/api/suppliers", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({
          code: newSupCode,
          name: newSupName,
          contact: newSupContact,
          phone: newSupPhone,
          email: newSupEmail,
          address: newSupAddress
        })
      });
      if (!res.ok) throw new Error("Gagal menambah supplier");
      // Reset form
      setNewSupCode("");
      setNewSupName("");
      setNewSupContact("");
      setNewSupPhone("");
      setNewSupEmail("");
      setNewSupAddress("");
      fetchData();
      alert("Supplier berhasil ditambahkan!");
    } catch (err: any) {
      alert(err.message);
    } finally {
      setIsSaving(false);
    }
  };

  // Submit stock opname adjustment
  const handleStockOpname = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!opnameSku) return alert("Silakan pilih SKU terlebih dahulu");
    setIsSaving(true);
    try {
      const res = await fetch("/api/stock", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({
          sku: opnameSku,
          physical: opnamePhysical,
          note: opnameNote
        })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Gagal menyimpan hitungan fisik");

      // Log to opname alert array locally
      const updatedAlerts = [
        {
          sku: opnameSku,
          physical: opnamePhysical,
          diff: data.diff,
          time: new Date().toLocaleTimeString("id-ID")
        },
        ...opnameAlerts
      ];
      setOpnameAlerts(updatedAlerts);
      setOpnameNote("");
      fetchData();
      alert("Opname berhasil disimpan!");
    } catch (err: any) {
      alert(err.message);
    } finally {
      setIsSaving(false);
    }
  };

  // Handle local Reconcile Math
  const handleReconcile = (e: React.FormEvent) => {
    e.preventDefault();
    const diff = reconcileActualCash - reconcileSystemCash;
    setReconcileDiff(diff);
  };

  // Payment Calculations
  const paymentTotals = dashboardData.payments || [];
  const totalPaymentSum = paymentTotals.reduce((sum: number, p: any) => sum + Number(p.amount), 0);

  // Render Login page if not authenticated
  if (!token) {
    return (
      <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center p-4">
        <div className="bg-slate-900 border border-slate-800 p-8 rounded-2xl w-full max-w-md shadow-2xl relative overflow-hidden">
          <div className="absolute top-0 left-0 w-full h-1.5 bg-gradient-to-r from-blue-600 via-purple-600 to-indigo-600"></div>
          <div className="flex flex-col items-center mb-6">
            <div className="w-16 h-16 bg-blue-600/10 border border-blue-500/20 rounded-2xl flex items-center justify-center text-blue-500 mb-4">
              <Scale size={32} className="animate-pulse" />
            </div>
            <span className="text-xs text-blue-400 font-extrabold uppercase tracking-widest mb-1">
              BaletPOS Web Admin
            </span>
            <h1 className="text-2xl font-black text-white">Login Admin Keuangan</h1>
          </div>

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="block text-xs font-extrabold text-slate-400 uppercase tracking-wider mb-1.5">
                Username
              </label>
              <input
                type="text"
                value={loginUsername}
                onChange={(e) => setLoginUsername(e.target.value)}
                placeholder="adminkeuangan"
                required
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-slate-200 focus:outline-none focus:border-blue-500 transition"
              />
            </div>
            <div>
              <label className="block text-xs font-extrabold text-slate-400 uppercase tracking-wider mb-1.5">
                Password
              </label>
              <input
                type="password"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                placeholder="keuangan123"
                required
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-slate-200 focus:outline-none focus:border-blue-500 transition"
              />
            </div>

            {loginError && (
              <div className="p-3 bg-rose-500/10 border border-rose-500/20 rounded-xl flex gap-2.5 text-xs text-rose-400 font-medium">
                <AlertTriangle size={16} className="shrink-0" />
                <span>{loginError}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white font-bold rounded-xl transition shadow-lg shadow-blue-500/20 flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
            >
              {loading ? (
                <>
                  <Loader2 size={18} className="animate-spin" />
                  Memproses...
                </>
              ) : (
                <>
                  <Lock size={18} />
                  Masuk ke Dashboard
                </>
              )}
            </button>
          </form>

          <div className="mt-6 text-center text-xs text-slate-500 font-semibold border-t border-slate-800/80 pt-4">
            Pakai akun desktop role Admin Keuangan.
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col md:flex-row">
      
      {/* Mobile Header Bar */}
      <div className="md:hidden bg-slate-900 border-b border-slate-800/60 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 bg-blue-600/10 border border-blue-500/20 rounded-lg flex items-center justify-center text-blue-500">
            <Scale size={20} />
          </div>
          <div>
            <h1 className="text-sm font-black text-white leading-tight">BaletPOS</h1>
            <p className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider">Finance Admin</p>
          </div>
        </div>
        <button
          onClick={() => setSidebarOpen(!sidebarOpen)}
          className="p-2 text-slate-400 hover:text-white"
        >
          {sidebarOpen ? <X size={22} /> : <Menu size={22} />}
        </button>
      </div>

      {/* Sidebar Navigation */}
      <aside
        className={`fixed inset-y-0 left-0 z-40 w-64 bg-slate-900 border-r border-slate-800/60 flex flex-col transform transition-transform duration-300 ease-in-out md:translate-x-0 md:static md:h-screen ${
          sidebarOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        <div className="p-6 border-b border-slate-800/60 flex items-center gap-3 shrink-0">
          <div className="w-10 h-10 bg-blue-600/10 border border-blue-500/20 rounded-xl flex items-center justify-center text-blue-500 shrink-0">
            <Scale size={22} />
          </div>
          <div>
            <h1 className="text-base font-black text-white leading-tight">BaletPOS</h1>
            <p className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider">Finance Admin</p>
          </div>
        </div>

        {/* Sidebar Nav with Section Headers */}
        <nav className="flex-1 overflow-y-auto px-4 py-6 space-y-6">
          
          {/* Section: RINGKASAN */}
          <div className="space-y-1">
            <span className="block text-[10px] font-black text-slate-500 uppercase tracking-widest px-3 mb-2">
              Ringkasan
            </span>
            <button
              onClick={() => { setActiveView("dashboard"); setSidebarOpen(false); }}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition ${
                activeView === "dashboard"
                  ? "bg-blue-600 text-white shadow-lg shadow-blue-600/10"
                  : "text-slate-400 hover:bg-slate-800/50 hover:text-slate-200"
              }`}
            >
              <LayoutDashboard size={18} />
              <span>Dashboard</span>
            </button>
          </div>

          {/* Section: MANAJEMEN STOK */}
          <div className="space-y-1">
            <span className="block text-[10px] font-black text-slate-500 uppercase tracking-widest px-3 mb-2">
              Manajemen Stok
            </span>
            <button
              onClick={() => { setActiveView("products"); setSidebarOpen(false); }}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition ${
                activeView === "products"
                  ? "bg-blue-600 text-white shadow-lg shadow-blue-600/10"
                  : "text-slate-400 hover:bg-slate-800/50 hover:text-slate-200"
              }`}
            >
              <Package size={18} />
              <span>Produk</span>
            </button>
            <button
              onClick={() => { setActiveView("stock"); setSidebarOpen(false); }}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition ${
                activeView === "stock"
                  ? "bg-blue-600 text-white shadow-lg shadow-blue-600/10"
                  : "text-slate-400 hover:bg-slate-800/50 hover:text-slate-200"
              }`}
            >
              <ClipboardList size={18} />
              <span>Stock Opname</span>
            </button>
            <button
              onClick={() => { setActiveView("suppliers"); setSidebarOpen(false); }}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition ${
                activeView === "suppliers"
                  ? "bg-blue-600 text-white shadow-lg shadow-blue-600/10"
                  : "text-slate-400 hover:bg-slate-800/50 hover:text-slate-200"
              }`}
            >
              <Users size={18} />
              <span>Supplier</span>
            </button>
          </div>

          {/* Section: TRANSAKSI KAS */}
          <div className="space-y-1">
            <span className="block text-[10px] font-black text-slate-500 uppercase tracking-widest px-3 mb-2">
              Transaksi Kas
            </span>
            <button
              onClick={() => { setActiveView("sales"); setSidebarOpen(false); }}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition ${
                activeView === "sales"
                  ? "bg-blue-600 text-white shadow-lg shadow-blue-600/10"
                  : "text-slate-400 hover:bg-slate-800/50 hover:text-slate-200"
              }`}
            >
              <ShoppingCart size={18} />
              <span>Penjualan</span>
            </button>
            <button
              onClick={() => { setActiveView("purchases"); setSidebarOpen(false); }}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition ${
                activeView === "purchases"
                  ? "bg-blue-600 text-white shadow-lg shadow-blue-600/10"
                  : "text-slate-400 hover:bg-slate-800/50 hover:text-slate-200"
              }`}
            >
              <ShoppingCart size={18} />
              <span>Pembelian</span>
            </button>
            <button
              onClick={() => { setActiveView("expenses"); setSidebarOpen(false); }}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition ${
                activeView === "expenses"
                  ? "bg-blue-600 text-white shadow-lg shadow-blue-600/10"
                  : "text-slate-400 hover:bg-slate-800/50 hover:text-slate-200"
              }`}
            >
              <DollarSign size={18} />
              <span>Biaya</span>
            </button>
            <button
              onClick={() => { setActiveView("returns"); setSidebarOpen(false); }}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition ${
                activeView === "returns"
                  ? "bg-blue-600 text-white shadow-lg shadow-blue-600/10"
                  : "text-slate-400 hover:bg-slate-800/50 hover:text-slate-200"
              }`}
            >
              <ArrowLeftRight size={18} />
              <span>Retur / Mutasi</span>
            </button>
          </div>

          {/* Section: LAPORAN */}
          <div className="space-y-1">
            <span className="block text-[10px] font-black text-slate-500 uppercase tracking-widest px-3 mb-2">
              Laporan
            </span>
            <button
              onClick={() => { setActiveView("profit"); setSidebarOpen(false); }}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition ${
                activeView === "profit"
                  ? "bg-blue-600 text-white shadow-lg shadow-blue-600/10"
                  : "text-slate-400 hover:bg-slate-800/50 hover:text-slate-200"
              }`}
            >
              <FileText size={18} />
              <span>Laba Rugi</span>
            </button>
            <button
              onClick={() => { setActiveView("reconcile"); setSidebarOpen(false); }}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition ${
                activeView === "reconcile"
                  ? "bg-blue-600 text-white shadow-lg shadow-blue-600/10"
                  : "text-slate-400 hover:bg-slate-800/50 hover:text-slate-200"
              }`}
            >
              <Scale size={18} />
              <span>Rekonsiliasi</span>
            </button>
            <button
              onClick={() => { setActiveView("transactions"); setSidebarOpen(false); }}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition ${
                activeView === "transactions"
                  ? "bg-blue-600 text-white shadow-lg shadow-blue-600/10"
                  : "text-slate-400 hover:bg-slate-800/50 hover:text-slate-200"
              }`}
            >
              <ArrowLeftRight size={18} />
              <span>Transaksi</span>
            </button>
          </div>

          {/* Section: SISTEM */}
          <div className="space-y-1">
            <span className="block text-[10px] font-black text-slate-500 uppercase tracking-widest px-3 mb-2">
              Sistem
            </span>
            <button
              onClick={() => { setActiveView("settings"); setSidebarOpen(false); }}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition ${
                activeView === "settings"
                  ? "bg-blue-600 text-white shadow-lg shadow-blue-600/10"
                  : "text-slate-400 hover:bg-slate-800/50 hover:text-slate-200"
              }`}
            >
              <Settings size={18} />
              <span>Setting</span>
            </button>
          </div>

        </nav>

        {/* Sidebar Footer with Session details */}
        <div className="p-6 border-t border-slate-800/60 space-y-4 shrink-0 bg-slate-900/60">
          <div>
            <span className="text-[10px] text-slate-400 font-extrabold uppercase tracking-wide">
              Role Aktif
            </span>
            <span className="block text-sm text-white font-bold leading-snug">
              {user?.fullName || "Admin Keuangan"}
            </span>
          </div>
          <button
            onClick={handleLogout}
            className="w-full flex items-center justify-center gap-2 py-2.5 border border-slate-800 hover:bg-slate-800 rounded-xl text-xs font-bold text-rose-500 cursor-pointer transition"
          >
            <LogOut size={14} />
            <span>Keluar</span>
          </button>
        </div>
      </aside>

      {/* Main Workspace Area */}
      <main className="flex-1 flex flex-col min-w-0">
        
        {/* Workspace Topbar Header */}
        <header className="bg-slate-900 border-b border-slate-800/60 px-6 py-4 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 sticky top-0 z-30">
          <div>
            <span className="text-[10px] text-blue-400 font-black uppercase tracking-wider">
              Monitoring Laporan
            </span>
            <h2 className="text-xl font-black text-white">
              {activeView === "dashboard" && "Dashboard Keuangan"}
              {activeView === "products" && "Manajemen Produk"}
              {activeView === "purchases" && "Riwayat Pembelian"}
              {activeView === "suppliers" && "Database Supplier"}
              {activeView === "returns" && "Retur & Mutasi Stok"}
              {activeView === "expenses" && "Laporan Pengeluaran"}
              {activeView === "sales" && "Laporan Penjualan"}
              {activeView === "transactions" && "Riwayat Transaksi"}
              {activeView === "profit" && "Laporan Laba Rugi"}
              {activeView === "stock" && "Monitoring Stock Opname"}
              {activeView === "reconcile" && "Rekonsiliasi Kas"}
              {activeView === "settings" && "Konfigurasi Sistem"}
            </h2>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {/* Global Search Filter (only for relevant views like dashboard) */}
            {activeView === "dashboard" && (
              <div className="relative shrink-0 w-full sm:w-64">
                <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Cari invoice, metode bayar..."
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-4 py-2 text-xs text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-blue-500 transition"
                />
              </div>
            )}

            {/* Global Period Select (for dashboard, purchases, sales, expenses, profit) */}
            {["dashboard", "purchases", "sales", "expenses", "profit"].includes(activeView) && (
              <div className="relative">
                <Calendar size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <select
                  value={period}
                  onChange={(e) => setPeriod(e.target.value)}
                  className="bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-8 py-2 text-xs font-semibold text-slate-200 focus:outline-none focus:border-blue-500 transition appearance-none cursor-pointer"
                >
                  <option value="all">Semua Data</option>
                  <option value="today">Hari Ini</option>
                  <option value="week">Minggu Ini</option>
                  <option value="month">Bulan Ini</option>
                </select>
              </div>
            )}

            {/* Sync Status Button */}
            <button
              onClick={() => fetchData()}
              disabled={loading}
              className="flex items-center gap-1.5 px-3 py-2 border border-slate-800 hover:bg-slate-800 rounded-xl text-xs font-bold text-slate-300 cursor-pointer disabled:opacity-50"
            >
              <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
              <span className="hidden sm:inline">Refresh</span>
            </button>

            {/* Connection status dot */}
            <div className={`px-2.5 py-1.5 rounded-full flex items-center gap-1.5 text-[10px] font-black uppercase tracking-wider ${
              connStatus === "live"
                ? "bg-emerald-500/10 border border-emerald-500/20 text-emerald-400"
                : connStatus === "error"
                  ? "bg-rose-500/10 border border-rose-500/20 text-rose-400"
                  : "bg-amber-500/10 border border-amber-500/20 text-amber-400"
            }`}>
              <span className={`w-1.5 h-1.5 rounded-full ${
                connStatus === "live"
                  ? "bg-emerald-400 animate-pulse"
                  : connStatus === "error"
                    ? "bg-rose-400"
                    : "bg-amber-400 animate-spin"
              }`}></span>
              <span>
                {connStatus === "live" && "Terhubung"}
                {connStatus === "error" && "Koneksi Putus"}
                {connStatus === "connecting" && "Menghubungkan"}
              </span>
            </div>
          </div>
        </header>

        {/* Outer Workspace Content Frame */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          
          {loading && !dashboardData.payments.length && (
            <div className="h-64 flex flex-col items-center justify-center gap-3">
              <Loader2 className="animate-spin text-blue-500" size={32} />
              <span className="text-sm text-slate-400 font-semibold">Memuat Data Keuangan...</span>
            </div>
          )}

          {error && (
            <div className="p-4 bg-rose-500/10 border border-rose-500/20 rounded-xl flex gap-3 text-sm text-rose-400 font-semibold">
              <AlertTriangle size={20} className="shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {!loading && (
            <>
              {/* -------------------- VIEW: DASHBOARD -------------------- */}
              {activeView === "dashboard" && (
                <div className="space-y-6">
                  
                  {/* Top 4 KPI Cards (Accounting calculations fixed) */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                    
                    {/* Card 1: Omzet Bersih */}
                    <div className="bg-slate-900 border border-slate-800/80 p-5 rounded-2xl shadow-md transition-all duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-blue-500/5 group cursor-pointer">
                      <div className="flex items-center justify-between mb-3">
                        <span className="text-[10px] text-slate-400 font-black uppercase tracking-wider">
                          Omzet Bersih
                        </span>
                        <div className="w-7 h-7 bg-blue-600/10 border border-blue-500/10 rounded-lg flex items-center justify-center text-blue-400">
                          <TrendingUp size={14} />
                        </div>
                      </div>
                      <h3 className="text-2xl font-black text-white group-hover:text-blue-400 transition-colors">
                        {formatCurrency(Number(dashboardData.summary?.net_revenue || 0))}
                      </h3>
                      {period !== "all" && (
                        <div className="mt-2.5 flex items-center gap-1.5 text-xs">
                          <span className={`font-extrabold ${
                            dashboardData.trends?.net_revenue >= 0 ? "text-emerald-400" : "text-rose-400"
                          }`}>
                            {dashboardData.trends?.net_revenue >= 0 ? "▲" : "▼"} {Math.abs(dashboardData.trends?.net_revenue || 0)}%
                          </span>
                          <span className="text-slate-500 font-semibold">vs periode lalu</span>
                        </div>
                      )}
                    </div>

                    {/* Card 2: Gross Profit */}
                    <div className="bg-slate-900 border border-slate-800/80 p-5 rounded-2xl shadow-md transition-all duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-teal-500/5 group cursor-pointer">
                      <div className="flex items-center justify-between mb-3">
                        <span className="text-[10px] text-slate-400 font-black uppercase tracking-wider">
                          Gross Profit (Laba Kotor)
                        </span>
                        <div className="w-7 h-7 bg-teal-600/10 border border-teal-500/10 rounded-lg flex items-center justify-center text-teal-400">
                          <Scale size={14} />
                        </div>
                      </div>
                      <h3 className={`text-2xl font-black transition-colors ${
                        Number(dashboardData.summary?.gross_profit || 0) >= 0
                          ? "text-white group-hover:text-emerald-400"
                          : "text-rose-500"
                      }`}>
                        {formatCurrency(Number(dashboardData.summary?.gross_profit || 0))}
                      </h3>
                      {period !== "all" && (
                        <div className="mt-2.5 flex items-center gap-1.5 text-xs">
                          <span className={`font-extrabold ${
                            dashboardData.trends?.gross_profit >= 0 ? "text-emerald-400" : "text-rose-400"
                          }`}>
                            {dashboardData.trends?.gross_profit >= 0 ? "▲" : "▼"} {Math.abs(dashboardData.trends?.gross_profit || 0)}%
                          </span>
                          <span className="text-slate-500 font-semibold">vs periode lalu</span>
                        </div>
                      )}
                    </div>

                    {/* Card 3: Biaya Operasional */}
                    <div className="bg-slate-900 border border-slate-800/80 p-5 rounded-2xl shadow-md transition-all duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-amber-500/5 group cursor-pointer">
                      <div className="flex items-center justify-between mb-3">
                        <span className="text-[10px] text-slate-400 font-black uppercase tracking-wider">
                          Biaya Operasional
                        </span>
                        <div className="w-7 h-7 bg-amber-600/10 border border-amber-500/10 rounded-lg flex items-center justify-center text-amber-400">
                          <TrendingDown size={14} />
                        </div>
                      </div>
                      <h3 className="text-2xl font-black text-white group-hover:text-amber-400 transition-colors">
                        {formatCurrency(Number(dashboardData.summary?.expenses || 0))}
                      </h3>
                      {period !== "all" && (
                        <div className="mt-2.5 flex items-center gap-1.5 text-xs">
                          <span className={`font-extrabold ${
                            dashboardData.trends?.expenses <= 0 ? "text-emerald-400" : "text-rose-400"
                          }`}>
                            {dashboardData.trends?.expenses <= 0 ? "▼" : "▲"} {Math.abs(dashboardData.trends?.expenses || 0)}%
                          </span>
                          <span className="text-slate-500 font-semibold">vs periode lalu</span>
                        </div>
                      )}
                    </div>

                    {/* Card 4: Laba Bersih */}
                    <div className="bg-slate-900 border border-slate-800/80 p-5 rounded-2xl shadow-md transition-all duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-indigo-500/5 group cursor-pointer">
                      <div className="flex items-center justify-between mb-3">
                        <span className="text-[10px] text-slate-400 font-black uppercase tracking-wider">
                          Laba Bersih
                        </span>
                        <div className="w-7 h-7 bg-indigo-600/10 border border-indigo-500/10 rounded-lg flex items-center justify-center text-indigo-400">
                          <DollarSign size={14} />
                        </div>
                      </div>
                      <h3 className={`text-2xl font-black transition-colors ${
                        Number(dashboardData.summary?.net_profit || 0) >= 0
                          ? "text-white group-hover:text-emerald-400"
                          : "text-rose-500"
                      }`}>
                        {formatCurrency(Number(dashboardData.summary?.net_profit || 0))}
                      </h3>
                      {period !== "all" && (
                        <div className="mt-2.5 flex items-center gap-1.5 text-xs">
                          <span className={`font-extrabold ${
                            dashboardData.trends?.net_profit >= 0 ? "text-emerald-400" : "text-rose-400"
                          }`}>
                            {dashboardData.trends?.net_profit >= 0 ? "▲" : "▼"} {Math.abs(dashboardData.trends?.net_profit || 0)}%
                          </span>
                          <span className="text-slate-500 font-semibold">vs periode lalu</span>
                        </div>
                      )}
                    </div>

                  </div>

                  {/* Main Grid: Trend Chart and Payment methods */}
                  <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    
                    {/* Chart Panel (Chronological X-axis sorting fixed) */}
                    <div className="lg:col-span-2 bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden flex flex-col h-[400px]">
                      <div className="p-5 border-b border-slate-800/60 flex items-center justify-between shrink-0 bg-slate-900/60">
                        <div>
                          <h3 className="text-base font-bold text-white">Tren Kas Masuk</h3>
                          <p className="text-xs text-slate-400">Omzet dan laba kotor tujuh hari terakhir secara kronologis</p>
                        </div>
                        <div className="flex gap-4 text-xs font-semibold text-slate-400">
                          <span className="flex items-center gap-1.5">
                            <span className="w-2.5 h-2.5 bg-blue-600 rounded-full"></span> Omzet
                          </span>
                          <span className="flex items-center gap-1.5">
                            <span className="w-2.5 h-2.5 bg-teal-500 rounded-full"></span> Laba
                          </span>
                        </div>
                      </div>
                      
                      <div className="flex-1 p-5 min-h-0">
                        {dashboardData.trend && dashboardData.trend.length > 0 ? (
                          <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={dashboardData.trend} margin={{ top: 10, right: 10, left: 10, bottom: 5 }}>
                              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                              <XAxis dataKey="day" stroke="#64748b" fontSize={11} fontWeight={600} />
                              <YAxis stroke="#64748b" fontSize={11} fontWeight={600} tickFormatter={(v) => `${(v / 1000000).toFixed(1)}M`} />
                              <Tooltip
                                contentStyle={{ backgroundColor: "#0f172a", border: "1px solid #1e293b", borderRadius: "12px" }}
                                formatter={(value: any, name: any) => [formatCurrency(Number(value)), name === "revenue" ? "Omzet" : "Laba"]}
                                labelStyle={{ color: "#94a3b8", fontWeight: 700 }}
                              />
                              <Bar dataKey="revenue" fill="#2563eb" radius={[4, 4, 0, 0]} maxBarSize={30} />
                              <Bar dataKey="profit" fill="#14b8a6" radius={[4, 4, 0, 0]} maxBarSize={30} />
                            </BarChart>
                          </ResponsiveContainer>
                        ) : (
                          <div className="h-full flex items-center justify-center text-xs text-slate-500 font-semibold">
                            Belum ada data tren kas masuk untuk periode ini.
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Payment Contribution Panel (percentage sum fixed) */}
                    <div className="bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden flex flex-col h-[400px]">
                      <div className="p-5 border-b border-slate-800/60 shrink-0 bg-slate-900/60">
                        <h3 className="text-base font-bold text-white">Metode Pembayaran</h3>
                        <p className="text-xs text-slate-400">Kontribusi transaksi dari yang tertinggi ke terendah</p>
                      </div>

                      <div className="flex-1 overflow-y-auto p-5 space-y-4">
                        {paymentTotals.length > 0 ? (
                          paymentTotals.map((item: any, idx: number) => {
                            const contribPercent = totalPaymentSum > 0
                              ? Math.round((Number(item.amount) / totalPaymentSum) * 100)
                              : 0;
                            return (
                              <div key={idx} className="space-y-1.5">
                                <div className="flex justify-between text-xs font-bold">
                                  <span className="text-slate-300 uppercase tracking-wide">{item.name}</span>
                                  <div className="flex items-center gap-2">
                                    <span className="text-slate-400">{formatCurrency(Number(item.amount))}</span>
                                    <span className="px-1.5 py-0.5 bg-blue-600/10 border border-blue-500/20 text-blue-400 rounded-md text-[10px]">
                                      {contribPercent}%
                                    </span>
                                  </div>
                                </div>
                                <div className="h-2 bg-slate-950 border border-slate-800/40 rounded-full overflow-hidden">
                                  <div
                                    className="h-full bg-gradient-to-r from-blue-600 to-indigo-500 rounded-full"
                                    style={{ width: `${contribPercent}%` }}
                                  ></div>
                                </div>
                              </div>
                            );
                          })
                        ) : (
                          <div className="h-full flex items-center justify-center text-xs text-slate-500 font-semibold">
                            Belum ada transaksi pembayaran.
                          </div>
                        )}
                      </div>
                    </div>

                  </div>

                  {/* Lower Grid: Recent Transactions and Expense Approvals */}
                  <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    
                    {/* Recent Transaction Panel (time relative fixed) */}
                    <div className="lg:col-span-2 bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden flex flex-col">
                      <div className="p-5 border-b border-slate-800/60 shrink-0 bg-slate-900/60">
                        <h3 className="text-base font-bold text-white">Transaksi Terbaru</h3>
                        <p className="text-xs text-slate-400">Data live dari kasir yang masuk ke laporan keuangan</p>
                      </div>

                      <div className="overflow-x-auto">
                        <table className="w-full text-left text-xs border-collapse">
                          <thead>
                            <tr className="bg-slate-950/60 border-b border-slate-800/60">
                              <th className="p-4 font-bold text-slate-400">INVOICE</th>
                              <th className="p-4 font-bold text-slate-400">WAKTU</th>
                              <th className="p-4 font-bold text-slate-400">METODE</th>
                              <th className="p-4 font-bold text-slate-400 text-right">TOTAL</th>
                              <th className="p-4 font-bold text-slate-400">STATUS</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-slate-800/50">
                            {dashboardData.transactions && dashboardData.transactions.length > 0 ? (
                              dashboardData.transactions.map((tx: any, idx: number) => (
                                <tr key={idx} className="hover:bg-slate-800/20 transition">
                                  <td className="p-4 font-bold text-slate-200">{tx.invoice}</td>
                                  <td className="p-4 font-semibold text-slate-400">
                                    {formatRelativeTime(tx.time)}
                                  </td>
                                  <td className="p-4 font-bold text-slate-300 uppercase tracking-wide">{tx.method}</td>
                                  <td className="p-4 font-bold text-slate-200 text-right">{formatCurrency(Number(tx.total))}</td>
                                  <td className="p-4">
                                    <span className={`px-2 py-1 rounded-md text-[10px] font-black uppercase ${
                                      tx.status === "COMPLETED"
                                        ? "bg-emerald-500/10 border border-emerald-500/20 text-emerald-400"
                                        : "bg-amber-500/10 border border-amber-500/20 text-amber-400"
                                    }`}>
                                      {tx.status}
                                    </span>
                                  </td>
                                </tr>
                              ))
                            ) : (
                              <tr>
                                <td colSpan={5} className="p-8 text-center text-slate-500 font-semibold">
                                  Belum ada transaksi terekam.
                                </td>
                              </tr>
                            )}
                          </tbody>
                        </table>
                      </div>
                    </div>

                    {/* Expense Approval Panel */}
                    <div className="bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden flex flex-col">
                      <div className="p-5 border-b border-slate-800/60 shrink-0 bg-slate-900/60">
                        <h3 className="text-base font-bold text-white">Approval Biaya</h3>
                        <p className="text-xs text-slate-400">Menunggu validasi & otorisasi dari Anda</p>
                      </div>

                      <div className="divide-y divide-slate-800/40 overflow-y-auto">
                        {dashboardData.approvals && dashboardData.approvals.length > 0 ? (
                          dashboardData.approvals.map((appr: any, idx: number) => (
                            <div key={idx} className="p-4 hover:bg-slate-800/20 transition flex items-center justify-between gap-3">
                              <div className="min-w-0">
                                <strong className="block text-xs font-bold text-slate-200 truncate">{appr.title}</strong>
                                <span className="block text-[11px] text-slate-400 font-medium truncate mt-0.5">{appr.meta}</span>
                              </div>
                              <span className="px-2 py-0.5 bg-rose-500/10 border border-rose-500/20 text-rose-400 rounded-md text-[9px] font-black uppercase tracking-wider">
                                {appr.status}
                              </span>
                            </div>
                          ))
                        ) : (
                          <div className="p-8 text-center text-xs text-slate-500 font-semibold">
                            Tidak ada pengajuan biaya aktif.
                          </div>
                        )}
                      </div>
                    </div>

                  </div>

                </div>
              )}

              {/* -------------------- VIEW: PRODUCTS -------------------- */}
              {activeView === "products" && (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
                  
                  {/* Products Table */}
                  <div className="lg:col-span-2 bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden">
                    <div className="p-5 border-b border-slate-800/60 bg-slate-900/60">
                      <h3 className="text-base font-bold text-white">Daftar Produk</h3>
                      <p className="text-xs text-slate-400">Data tipe laptop, spareparts, dan aksesoris</p>
                    </div>
                    <div className="overflow-x-auto">
                      <table className="w-full text-left text-xs border-collapse">
                        <thead>
                          <tr className="bg-slate-950/60 border-b border-slate-800/60">
                            <th className="p-4 font-bold text-slate-400">SKU</th>
                            <th className="p-4 font-bold text-slate-400">PRODUK</th>
                            <th className="p-4 font-bold text-slate-400">TIPE</th>
                            <th className="p-4 font-bold text-slate-400 text-right">STOK</th>
                            <th className="p-4 font-bold text-slate-400 text-right">HARGA JUAL</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-800/50">
                          {products.length > 0 ? (
                            products
                              .slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage)
                              .map((p: any, idx: number) => (
                                <tr key={idx} className="hover:bg-slate-800/20 transition">
                                  <td className="p-4 font-bold text-slate-200">{p.sku}</td>
                                  <td className="p-4 font-bold text-slate-200">{p.name}</td>
                                  <td className="p-4 font-semibold text-slate-400 uppercase tracking-wider">{p.type}</td>
                                  <td className="p-4 font-bold text-slate-200 text-right">{p.stock}</td>
                                  <td className="p-4 font-bold text-slate-200 text-right">{formatCurrency(Number(p.selling_price))}</td>
                                </tr>
                              ))
                          ) : (
                            <tr>
                              <td colSpan={5} className="p-8 text-center text-slate-500 font-semibold">
                                Belum ada produk terdaftar.
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                    <PaginationControl
                      currentPage={currentPage}
                      totalItems={products.length}
                      itemsPerPage={itemsPerPage}
                      onPageChange={setCurrentPage}
                    />
                  </div>

                  {/* Add Product Form */}
                  <div className="bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden p-5 space-y-4">
                    <div>
                      <h3 className="text-base font-bold text-white">Tambah Produk</h3>
                      <p className="text-xs text-slate-400">Form input cepat admin web</p>
                    </div>

                    <form onSubmit={handleAddProduct} className="space-y-3.5">
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1">SKU</label>
                        <input
                          type="text"
                          required
                          value={newProdSku}
                          onChange={(e) => setNewProdSku(e.target.value)}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1">Nama Produk</label>
                        <input
                          type="text"
                          required
                          value={newProdName}
                          onChange={(e) => setNewProdName(e.target.value)}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1">Tipe Produk</label>
                        <select
                          value={newProdType}
                          onChange={(e) => setNewProdType(e.target.value)}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition cursor-pointer"
                        >
                          <option value="LAPTOP_NEW">Laptop Baru</option>
                          <option value="LAPTOP_SECOND">Laptop Second</option>
                          <option value="SPAREPARTS">Spareparts</option>
                          <option value="PERIPHERAL">Peripheral</option>
                          <option value="SERVICE">Service</option>
                        </select>
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1">HPP</label>
                        <input
                          type="number"
                          required
                          min={0}
                          value={newProdHpp}
                          onChange={(e) => setNewProdHpp(Number(e.target.value))}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1">Harga Jual</label>
                        <input
                          type="number"
                          required
                          min={0}
                          value={newProdPrice}
                          onChange={(e) => setNewProdPrice(Number(e.target.value))}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1">Stok Awal</label>
                        <input
                          type="number"
                          required
                          min={0}
                          value={newProdStock}
                          onChange={(e) => setNewProdStock(Number(e.target.value))}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition"
                        />
                      </div>

                      <button
                        type="submit"
                        disabled={isSaving}
                        className="w-full py-2.5 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-lg text-xs cursor-pointer transition flex items-center justify-center gap-1.5"
                      >
                        {isSaving ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
                        Simpan Produk
                      </button>
                    </form>
                  </div>

                </div>
              )}

              {/* -------------------- VIEW: PURCHASES -------------------- */}
              {activeView === "purchases" && (
                <div className="bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden">
                  <div className="p-5 border-b border-slate-800/60 bg-slate-900/60">
                    <h3 className="text-base font-bold text-white">Pembelian (PO)</h3>
                    <p className="text-xs text-slate-400">Riwayat pengadaan stok barang supplier</p>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead>
                        <tr className="bg-slate-950/60 border-b border-slate-800/60">
                          <th className="p-4 font-bold text-slate-400">NO PEMBELIAN</th>
                          <th className="p-4 font-bold text-slate-400">TANGGAL</th>
                          <th className="p-4 font-bold text-slate-400">SUPPLIER</th>
                          <th className="p-4 font-bold text-slate-400 text-right">TOTAL</th>
                          <th className="p-4 font-bold text-slate-400">STATUS</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800/50">
                        {purchases.length > 0 ? (
                          purchases
                            .slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage)
                            .map((po: any, idx: number) => (
                              <tr key={idx} className="hover:bg-slate-800/20 transition">
                                <td className="p-4 font-bold text-slate-200">{po.number}</td>
                                <td className="p-4 font-semibold text-slate-400">{new Date(po.date).toLocaleDateString("id-ID")}</td>
                                <td className="p-4 font-bold text-slate-200">{po.supplier}</td>
                                <td className="p-4 font-bold text-slate-200 text-right">{formatCurrency(Number(po.total))}</td>
                                <td className="p-4">
                                <span className={`px-2 py-0.5 rounded-md text-[10px] font-black uppercase ${
                                  po.status === "COMPLETED"
                                    ? "bg-emerald-500/10 border border-emerald-500/20 text-emerald-400"
                                    : "bg-amber-500/10 border border-amber-500/20 text-amber-400"
                                }`}>
                                  {po.status}
                                </span>
                              </td>
                            </tr>
                          ))
                        ) : (
                          <tr>
                            <td colSpan={5} className="p-8 text-center text-slate-500 font-semibold">
                              Tidak ada PO tercatat dalam periode ini.
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                  <PaginationControl
                    currentPage={currentPage}
                    totalItems={purchases.length}
                    itemsPerPage={itemsPerPage}
                    onPageChange={setCurrentPage}
                  />
                </div>
              )}

              {/* -------------------- VIEW: SUPPLIERS -------------------- */}
              {activeView === "suppliers" && (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
                  
                  {/* Suppliers List */}
                  <div className="lg:col-span-2 bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden">
                    <div className="p-5 border-b border-slate-800/60 bg-slate-900/60">
                      <h3 className="text-base font-bold text-white">Database Supplier</h3>
                      <p className="text-xs text-slate-400">Daftar supplier aktif terhubung database desktop</p>
                    </div>
                    <div className="overflow-x-auto">
                      <table className="w-full text-left text-xs border-collapse">
                        <thead>
                          <tr className="bg-slate-950/60 border-b border-slate-800/60">
                            <th className="p-4 font-bold text-slate-400">KODE</th>
                            <th className="p-4 font-bold text-slate-400">NAMA</th>
                            <th className="p-4 font-bold text-slate-400">CONTACT PERSON</th>
                            <th className="p-4 font-bold text-slate-400">TELEPON</th>
                            <th className="p-4 font-bold text-slate-400">EMAIL</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-800/50">
                          {suppliers.length > 0 ? (
                            suppliers
                              .slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage)
                              .map((s: any, idx: number) => (
                                <tr key={idx} className="hover:bg-slate-800/20 transition">
                                  <td className="p-4 font-bold text-slate-200">{s.code}</td>
                                  <td className="p-4 font-bold text-slate-200">{s.name}</td>
                                  <td className="p-4 font-semibold text-slate-400">{s.contact || "-"}</td>
                                  <td className="p-4 font-semibold text-slate-400">{s.phone || "-"}</td>
                                  <td className="p-4 font-semibold text-slate-400">{s.email || "-"}</td>
                                </tr>
                              ))
                          ) : (
                            <tr>
                              <td colSpan={5} className="p-8 text-center text-slate-500 font-semibold">
                                Belum ada supplier terdaftar.
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                    <PaginationControl
                      currentPage={currentPage}
                      totalItems={suppliers.length}
                      itemsPerPage={itemsPerPage}
                      onPageChange={setCurrentPage}
                    />
                  </div>

                  {/* Add Supplier Form */}
                  <div className="bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden p-5 space-y-4">
                    <div>
                      <h3 className="text-base font-bold text-white">Tambah Supplier</h3>
                      <p className="text-xs text-slate-400">Masukkan kontak supplier baru</p>
                    </div>

                    <form onSubmit={handleAddSupplier} className="space-y-3.5">
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1">Kode Supplier</label>
                        <input
                          type="text"
                          required
                          value={newSupCode}
                          onChange={(e) => setNewSupCode(e.target.value)}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1">Nama Supplier</label>
                        <input
                          type="text"
                          required
                          value={newSupName}
                          onChange={(e) => setNewSupName(e.target.value)}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1">Contact Person</label>
                        <input
                          type="text"
                          value={newSupContact}
                          onChange={(e) => setNewSupContact(e.target.value)}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1">Telepon</label>
                        <input
                          type="text"
                          value={newSupPhone}
                          onChange={(e) => setNewSupPhone(e.target.value)}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1">Email</label>
                        <input
                          type="email"
                          value={newSupEmail}
                          onChange={(e) => setNewSupEmail(e.target.value)}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1">Alamat</label>
                        <textarea
                          rows={3}
                          value={newSupAddress}
                          onChange={(e) => setNewSupAddress(e.target.value)}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition resize-none"
                        ></textarea>
                      </div>

                      <button
                        type="submit"
                        disabled={isSaving}
                        className="w-full py-2.5 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-lg text-xs cursor-pointer transition flex items-center justify-center gap-1.5"
                      >
                        {isSaving ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
                        Simpan Supplier
                      </button>
                    </form>
                  </div>

                </div>
              )}

              {/* -------------------- VIEW: RETURNS & MUTATION -------------------- */}
              {activeView === "returns" && (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
                  
                  {/* Sales Return Table */}
                  <div className="bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden">
                    <div className="p-5 border-b border-slate-800/60 bg-slate-900/60">
                      <h3 className="text-base font-bold text-white">Retur Penjualan</h3>
                      <p className="text-xs text-slate-400">Riwayat klaim retur barang dari pelanggan</p>
                    </div>
                    <div className="overflow-x-auto">
                      <table className="w-full text-left text-xs border-collapse">
                        <thead>
                          <tr className="bg-slate-950/60 border-b border-slate-800/60">
                            <th className="p-4 font-bold text-slate-400">NO RETUR</th>
                            <th className="p-4 font-bold text-slate-400">INVOICE</th>
                            <th className="p-4 font-bold text-slate-400 text-right">TOTAL</th>
                            <th className="p-4 font-bold text-slate-400">STATUS</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-800/50">
                          {returnsData.returns && returnsData.returns.length > 0 ? (
                            returnsData.returns
                              .slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage)
                              .map((ret: any, idx: number) => (
                                <tr key={idx} className="hover:bg-slate-800/20 transition">
                                  <td className="p-4 font-bold text-slate-200">{ret.number}</td>
                                  <td className="p-4 font-bold text-slate-200">{ret.invoice}</td>
                                <td className="p-4 font-bold text-slate-200 text-right">{formatCurrency(Number(ret.total))}</td>
                                <td className="p-4">
                                  <span className="px-2 py-0.5 bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 rounded-md text-[10px] font-black uppercase">
                                    {ret.status}
                                  </span>
                                </td>
                              </tr>
                            ))
                          ) : (
                            <tr>
                              <td colSpan={4} className="p-8 text-center text-slate-500 font-semibold">
                                Belum ada transaksi retur.
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                    <PaginationControl
                      currentPage={currentPage}
                      totalItems={returnsData.returns?.length || 0}
                      itemsPerPage={itemsPerPage}
                      onPageChange={setCurrentPage}
                    />
                  </div>

                  {/* Stock Movements Table */}
                  <div className="bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden">
                    <div className="p-5 border-b border-slate-800/60 bg-slate-900/60">
                      <h3 className="text-base font-bold text-white">Mutasi Stok</h3>
                      <p className="text-xs text-slate-400">Riwayat selisih, opname, dan perpindahan stok</p>
                    </div>
                    <div className="overflow-x-auto">
                      <table className="w-full text-left text-xs border-collapse">
                        <thead>
                          <tr className="bg-slate-950/60 border-b border-slate-800/60">
                            <th className="p-4 font-bold text-slate-400">TANGGAL</th>
                            <th className="p-4 font-bold text-slate-400">PRODUK</th>
                            <th className="p-4 font-bold text-slate-400">TIPE MUTASI</th>
                            <th className="p-4 font-bold text-slate-400 text-right">QTY</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-800/50">
                          {returnsData.movements && returnsData.movements.length > 0 ? (
                            returnsData.movements
                              .slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage)
                              .map((mov: any, idx: number) => (
                                <tr key={idx} className="hover:bg-slate-800/20 transition">
                                  <td className="p-4 font-semibold text-slate-400">{new Date(mov.date).toLocaleDateString("id-ID")}</td>
                                <td className="p-4 font-bold text-slate-200">{mov.product}</td>
                                <td className="p-4 font-bold text-slate-300 uppercase tracking-wide">{mov.type}</td>
                                <td className={`p-4 font-bold text-right ${mov.qty >= 0 ? "text-emerald-400" : "text-rose-400"}`}>
                                  {mov.qty > 0 ? `+${mov.qty}` : mov.qty}
                                </td>
                              </tr>
                            ))
                          ) : (
                            <tr>
                              <td colSpan={4} className="p-8 text-center text-slate-500 font-semibold">
                                Belum ada mutasi stok.
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                    <PaginationControl
                      currentPage={currentPage}
                      totalItems={returnsData.movements?.length || 0}
                      itemsPerPage={itemsPerPage}
                      onPageChange={setCurrentPage}
                    />
                  </div>

                </div>
              )}

              {/* -------------------- VIEW: EXPENSES -------------------- */}
              {activeView === "expenses" && (
                <div className="bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden">
                  <div className="p-5 border-b border-slate-800/60 bg-slate-900/60 flex items-center justify-between">
                    <div>
                      <h3 className="text-base font-bold text-white">Laporan Pengeluaran Toko</h3>
                      <p className="text-xs text-slate-400">Beban operasional ter-approve admin</p>
                    </div>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead>
                        <tr className="bg-slate-950/60 border-b border-slate-800/60">
                          <th className="p-4 font-bold text-slate-400">KODE</th>
                          <th className="p-4 font-bold text-slate-400">KATEGORI</th>
                          <th className="p-4 font-bold text-slate-400">KETERANGAN</th>
                          <th className="p-4 font-bold text-slate-400 text-right">NOMINAL</th>
                          <th className="p-4 font-bold text-slate-400">STATUS</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800/50">
                        {expenses.length > 0 ? (
                          expenses
                            .slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage)
                            .map((e: any, idx: number) => (
                              <tr key={idx} className="hover:bg-slate-800/20 transition">
                                <td className="p-4 font-bold text-slate-200">{e.code}</td>
                              <td className="p-4 font-bold text-slate-200">{e.category}</td>
                              <td className="p-4 font-semibold text-slate-400 max-w-xs truncate">{e.note || "-"}</td>
                              <td className="p-4 font-bold text-slate-200 text-right">{formatCurrency(Number(e.total))}</td>
                              <td className="p-4">
                                <span className="px-2 py-0.5 bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 rounded-md text-[10px] font-black uppercase">
                                  {e.status}
                                </span>
                              </td>
                            </tr>
                          ))
                        ) : (
                          <tr>
                            <td colSpan={5} className="p-8 text-center text-slate-500 font-semibold">
                              Tidak ada pengeluaran dalam periode ini.
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                  <PaginationControl
                    currentPage={currentPage}
                    totalItems={expenses.length}
                    itemsPerPage={itemsPerPage}
                    onPageChange={setCurrentPage}
                  />
                </div>
              )}

              {/* -------------------- VIEW: SALES / PENJUALAN / TRANSAKSI -------------------- */}
              {(activeView === "sales" || activeView === "transactions") && (
                <div className="bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden">
                  <div className="p-5 border-b border-slate-800/60 bg-slate-900/60">
                    <h3 className="text-base font-bold text-white">Riwayat Transaksi Lengkap</h3>
                    <p className="text-xs text-slate-400">Tabel monitoring penjualan dari mesin kasir</p>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead>
                        <tr className="bg-slate-950/60 border-b border-slate-800/60">
                          <th className="p-4 font-bold text-slate-400">INVOICE</th>
                          <th className="p-4 font-bold text-slate-400">WAKTU</th>
                          <th className="p-4 font-bold text-slate-400">KASIR</th>
                          <th className="p-4 font-bold text-slate-400">METODE BAYAR</th>
                          <th className="p-4 font-bold text-slate-400 text-right">TOTAL TRANSAKSI</th>
                          <th className="p-4 font-bold text-slate-400">STATUS</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800/50">
                        {products.length > 0 ? (
                          products
                            .slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage)
                            .map((item: any, idx: number) => (
                              <tr key={idx} className="hover:bg-slate-800/20 transition">
                                <td className="p-4 font-bold text-slate-200">{item.invoice}</td>
                              <td className="p-4 font-semibold text-slate-400">{new Date(item.time).toLocaleString("id-ID")}</td>
                              <td className="p-4 font-semibold text-slate-300">{item.cashier || "-"}</td>
                              <td className="p-4 font-bold text-slate-300 uppercase tracking-wide">{item.method}</td>
                              <td className="p-4 font-bold text-slate-200 text-right">{formatCurrency(Number(item.total))}</td>
                              <td className="p-4">
                                <span className={`px-2 py-0.5 rounded-md text-[10px] font-black uppercase ${
                                  item.status === "COMPLETED"
                                    ? "bg-emerald-500/10 border border-emerald-500/20 text-emerald-400"
                                    : "bg-amber-500/10 border border-amber-500/20 text-amber-400"
                                }`}>
                                  {item.status}
                                </span>
                              </td>
                            </tr>
                          ))
                        ) : (
                          <tr>
                            <td colSpan={6} className="p-8 text-center text-slate-500 font-semibold">
                              Tidak ada transaksi terekam untuk periode ini.
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                  <PaginationControl
                    currentPage={currentPage}
                    totalItems={products.length}
                    itemsPerPage={itemsPerPage}
                    onPageChange={setCurrentPage}
                  />
                </div>
              )}

              {/* -------------------- VIEW: LABA RUGI -------------------- */}
              {activeView === "profit" && (
                <div className="bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden max-w-3xl mx-auto">
                  <div className="p-5 border-b border-slate-800/60 bg-slate-900/60">
                    <h3 className="text-base font-bold text-white">Laporan Laba Rugi</h3>
                    <p className="text-xs text-slate-400">Ringkasan akun pendapatan, HPP, biaya, dan laba bersih (Fiqh Muamalah)</p>
                  </div>
                  <div className="divide-y divide-slate-800/50">
                    {profitRows.length > 0 ? (
                      profitRows.map((row: any, idx: number) => {
                        const isTotal = row[2] === "total";
                        return (
                          <div
                            key={idx}
                            className={`p-4 flex items-center justify-between text-xs font-semibold ${
                              isTotal ? "bg-slate-950/60 border-t border-b border-slate-800/80 py-4.5" : ""
                            }`}
                          >
                            <span className={isTotal ? "text-white font-bold uppercase tracking-wider" : "text-slate-400"}>
                              {row[0]}
                            </span>
                            <span className={`font-bold ${
                              isTotal
                                ? Number(row[1]) >= 0 ? "text-emerald-400 text-sm font-black" : "text-rose-400 text-sm font-black"
                                : Number(row[1]) >= 0 ? "text-slate-200" : "text-rose-400/90"
                            }`}>
                              {formatCurrency(Number(row[1]))}
                            </span>
                          </div>
                        );
                      })
                    ) : (
                      <div className="p-8 text-center text-slate-500 font-semibold">
                        Gagal mengkalkulasi laba rugi.
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* -------------------- VIEW: STOCK OPNAME -------------------- */}
              {activeView === "stock" && (
                <div className="space-y-6">
                  
                  {/* Stock metrics */}
                  <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <div className="bg-slate-900 border border-slate-800/60 p-4 rounded-xl">
                      <span className="text-[10px] text-slate-500 font-bold uppercase tracking-wider block mb-1">Total SKU Aktif</span>
                      <strong className="text-xl font-bold text-white">{stock.length}</strong>
                    </div>
                    <div className="bg-slate-900 border border-slate-800/60 p-4 rounded-xl">
                      <span className="text-[10px] text-slate-500 font-bold uppercase tracking-wider block mb-1">Stok Kritis</span>
                      <strong className="text-xl font-bold text-rose-500">{stock.filter((s) => s.system < 5).length}</strong>
                    </div>
                    <div className="bg-slate-900 border border-slate-800/60 p-4 rounded-xl">
                      <span className="text-[10px] text-slate-500 font-bold uppercase tracking-wider block mb-1">Selisih Fisik vs Sistem</span>
                      <strong className="text-xl font-bold text-amber-500">
                        {stock.filter((s) => s.system !== s.physical).length} SKU
                      </strong>
                    </div>
                    <div className="bg-slate-900 border border-slate-800/60 p-4 rounded-xl">
                      <span className="text-[10px] text-slate-500 font-bold uppercase tracking-wider block mb-1">Nilai Taksiran Stok</span>
                      <strong className="text-xl font-bold text-blue-500">
                        {formatCurrency(stock.reduce((sum, s) => sum + (s.system * Number(s.hpp || 0)), 0))}
                      </strong>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
                    
                    {/* Stock Opname Table */}
                    <div className="lg:col-span-2 bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden">
                      <div className="p-5 border-b border-slate-800/60 bg-slate-900/60">
                        <h3 className="text-base font-bold text-white">Monitoring Penyesuaian Stok</h3>
                        <p className="text-xs text-slate-400">Bandingkan stok sistem POS dengan hitungan fisik</p>
                      </div>
                      <div className="overflow-x-auto">
                        <table className="w-full text-left text-xs border-collapse">
                          <thead>
                            <tr className="bg-slate-950/60 border-b border-slate-800/60">
                              <th className="p-4 font-bold text-slate-400">SKU</th>
                              <th className="p-4 font-bold text-slate-400">PRODUK</th>
                              <th className="p-4 font-bold text-slate-400 text-right">SISTEM</th>
                              <th className="p-4 font-bold text-slate-400 text-right">FISIK AKTUAL</th>
                              <th className="p-4 font-bold text-slate-400 text-right">SELISIH</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-slate-800/50">
                            {stock.length > 0 ? (
                              stock
                                .slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage)
                                .map((s: any, idx: number) => {
                                  const diff = s.physical - s.system;
                                  return (
                                    <tr key={idx} className="hover:bg-slate-800/20 transition">
                                    <td className="p-4 font-bold text-slate-200">{s.sku}</td>
                                    <td className="p-4 font-bold text-slate-200">{s.name}</td>
                                    <td className="p-4 font-bold text-slate-300 text-right">{s.system}</td>
                                    <td className="p-4 font-bold text-slate-200 text-right">{s.physical}</td>
                                    <td className={`p-4 font-bold text-right ${diff === 0 ? "text-slate-400" : diff > 0 ? "text-emerald-400" : "text-rose-400"}`}>
                                      {diff > 0 ? `+${diff}` : diff}
                                    </td>
                                  </tr>
                                );
                              })
                            ) : (
                              <tr>
                                <td colSpan={5} className="p-8 text-center text-slate-500 font-semibold">
                                  Belum ada monitoring stok opname.
                                </td>
                              </tr>
                            )}
                          </tbody>
                        </table>
                      </div>
                      <PaginationControl
                        currentPage={currentPage}
                        totalItems={stock.length}
                        itemsPerPage={itemsPerPage}
                        onPageChange={setCurrentPage}
                      />
                    </div>

                    {/* Stock Opname Adjustment Form */}
                    <div className="bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden p-5 space-y-4">
                      <div>
                        <h3 className="text-base font-bold text-white">Input Hitung Fisik</h3>
                        <p className="text-xs text-slate-400">Sesuaikan fisik barang di toko</p>
                      </div>

                      <form onSubmit={handleStockOpname} className="space-y-4">
                        <div>
                          <label className="block text-xs font-bold text-slate-400 mb-1.5">Pilih Produk (SKU)</label>
                          <select
                            value={opnameSku}
                            onChange={(e) => {
                              setOpnameSku(e.target.value);
                              const item = stock.find((s) => s.sku === e.target.value);
                              if (item) setOpnamePhysical(item.system);
                            }}
                            className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition cursor-pointer"
                          >
                            <option value="">-- Pilih SKU Produk --</option>
                            {stock.map((item: any, idx: number) => (
                              <option key={idx} value={item.sku}>
                                {item.sku} - {item.name}
                              </option>
                            ))}
                          </select>
                        </div>
                        <div>
                          <label className="block text-xs font-bold text-slate-400 mb-1.5">Jumlah Fisik Aktual</label>
                          <input
                            type="number"
                            required
                            min={0}
                            value={opnamePhysical}
                            onChange={(e) => setOpnamePhysical(Number(e.target.value))}
                            className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition"
                          />
                        </div>
                        <div>
                          <label className="block text-xs font-bold text-slate-400 mb-1.5">Catatan Opname</label>
                          <textarea
                            rows={3}
                            value={opnameNote}
                            onChange={(e) => setOpnameNote(e.target.value)}
                            placeholder="Kondisi rak, selisih hitung, dll"
                            className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition resize-none"
                          ></textarea>
                        </div>

                        <button
                          type="submit"
                          disabled={isSaving}
                          className="w-full py-2.5 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-lg text-xs cursor-pointer transition flex items-center justify-center gap-1.5"
                        >
                          {isSaving ? <Loader2 size={14} className="animate-spin" /> : <CheckCircle size={14} />}
                          Simpan Hasil Hitung
                        </button>
                      </form>

                      {/* Stock Adjustment Local Feed */}
                      {opnameAlerts.length > 0 && (
                        <div className="border-t border-slate-800/80 pt-4 space-y-2.5">
                          <span className="block text-[10px] text-slate-400 font-extrabold uppercase tracking-wider">
                            Aktivitas Opname Sesi Ini
                          </span>
                          <div className="space-y-2 max-h-36 overflow-y-auto divide-y divide-slate-800/40">
                            {opnameAlerts.map((al: any, idx: number) => (
                              <div key={idx} className="text-xs pt-2 flex items-start justify-between gap-2.5">
                                <div>
                                  <strong className="block text-slate-200">{al.sku}</strong>
                                  <span className="text-[10px] text-slate-500 font-semibold">{al.time} WIB</span>
                                </div>
                                <span className={`font-bold ${al.diff === 0 ? "text-slate-400" : al.diff > 0 ? "text-emerald-400" : "text-rose-400"}`}>
                                  {al.diff > 0 ? `+${al.diff}` : al.diff} (Fisik: {al.physical})
                                </span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>

                  </div>
                </div>
              )}

              {/* -------------------- VIEW: RECONCILE -------------------- */}
              {activeView === "reconcile" && (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start max-w-4xl mx-auto">
                  
                  {/* Reconcile Input Form */}
                  <div className="bg-slate-900 border border-slate-800/60 rounded-2xl p-5 space-y-4">
                    <div>
                      <h3 className="text-base font-bold text-white">Cocokkan Kas</h3>
                      <p className="text-xs text-slate-400">Rekonsiliasi nilai kas sistem POS dengan kas fisik aktual</p>
                    </div>

                    <form onSubmit={handleReconcile} className="space-y-4">
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1.5">Total Kas Sistem (IDR)</label>
                        <input
                          type="number"
                          required
                          value={reconcileSystemCash}
                          onChange={(e) => setReconcileSystemCash(Number(e.target.value))}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1.5">Total Kas Aktual (IDR)</label>
                        <input
                          type="number"
                          required
                          value={reconcileActualCash}
                          onChange={(e) => setReconcileActualCash(Number(e.target.value))}
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-slate-400 mb-1.5">Catatan Perbedaan</label>
                        <textarea
                          rows={3}
                          value={reconcileNote}
                          onChange={(e) => setReconcileNote(e.target.value)}
                          placeholder="Faktor selisih kas, kembalian, nominal tidak sinkron dll..."
                          className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-blue-500 transition resize-none"
                        ></textarea>
                      </div>

                      <button
                        type="submit"
                        className="w-full py-2.5 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-lg text-xs cursor-pointer transition flex items-center justify-center gap-1.5"
                      >
                        <Scale size={14} />
                        Kalkulasi Selisih Kas
                      </button>
                    </form>
                  </div>

                  {/* Reconcile Result Display */}
                  <div className="bg-slate-900 border border-slate-800/60 rounded-2xl p-6 h-64 flex flex-col justify-center items-center text-center space-y-4">
                    {reconcileDiff === null ? (
                      <div className="text-slate-500 space-y-2">
                        <Info size={32} className="mx-auto text-slate-600" />
                        <p className="text-xs font-semibold max-w-xs">
                          Masukkan kas sistem dan kas fisik aktual di sebelah kiri untuk menghitung selisih rekonsiliasi.
                        </p>
                      </div>
                    ) : (
                      <div className="space-y-2">
                        <span className="text-[10px] text-slate-400 font-extrabold uppercase tracking-widest block">Selisih Kas Rekonsiliasi</span>
                        <strong className={`text-3xl font-black block ${
                          reconcileDiff === 0 ? "text-emerald-400" : reconcileDiff > 0 ? "text-teal-400" : "text-rose-500"
                        }`}>
                          {formatCurrency(reconcileDiff)}
                        </strong>
                        <p className="text-xs text-slate-400 font-medium max-w-xs">
                          {reconcileDiff === 0
                            ? "Kas seimbang secara sempurna (Aman)."
                            : reconcileDiff > 0
                              ? "Ada kelebihan dana (selisih lebih) dari kas aktual."
                              : "Ada kekurangan dana (selisih kurang) pada kas fisik."}
                        </p>
                      </div>
                    )}
                  </div>

                </div>
              )}

              {/* -------------------- VIEW: SETTINGS -------------------- */}
              {activeView === "settings" && (
                <div className="bg-slate-900 border border-slate-800/60 rounded-2xl overflow-hidden max-w-4xl mx-auto">
                  <div className="p-5 border-b border-slate-800/60 bg-slate-900/60">
                    <h3 className="text-base font-bold text-white">Pengaturan Aplikasi</h3>
                    <p className="text-xs text-slate-400">Konfigurasi variabel sistem dari database settings</p>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead>
                        <tr className="bg-slate-950/60 border-b border-slate-800/60">
                          <th className="p-4 font-bold text-slate-400">KEY</th>
                          <th className="p-4 font-bold text-slate-400">VALUE</th>
                          <th className="p-4 font-bold text-slate-400">DESKRIPSI</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800/50">
                        {settings.length > 0 ? (
                          settings.map((set: any, idx: number) => (
                            <tr key={idx} className="hover:bg-slate-800/20 transition">
                              <td className="p-4 font-bold text-blue-400 font-mono">{set.key}</td>
                              <td className="p-4 font-bold text-slate-200">{set.value}</td>
                              <td className="p-4 font-semibold text-slate-400 max-w-sm whitespace-normal">{set.description || "-"}</td>
                            </tr>
                          ))
                        ) : (
                          <tr>
                            <td colSpan={3} className="p-8 text-center text-slate-500 font-semibold">
                              Tidak ada konfigurasi ditemukan.
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </>
          )}

        </div>

        {/* Footer timestamp */}
        {lastUpdated && (
          <footer className="px-6 py-3 border-t border-slate-800/50 text-[10px] text-slate-500 font-bold text-right shrink-0 bg-slate-900/40">
            Terakhir disinkronisasi: {lastUpdated.toLocaleTimeString("id-ID")} WIB
          </footer>
        )}
      </main>
    </div>
  );
}
