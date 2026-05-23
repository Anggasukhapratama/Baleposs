# Manual Revamp Guide - Blue Theme

## ✅ Yang Sudah Selesai

### 1. globals.css
File `src/app/globals.css` sudah diupdate dengan:
- CSS variables untuk blue theme
- Custom scrollbar styling
- Font yang lebih baik

### 2. layout.tsx
Metadata sudah diupdate menjadi "BaletPOS Admin - Sistem Manajemen Keuangan"

## 🔧 Yang Perlu Dilakukan Manual di page.tsx

Karena file page.tsx sangat besar (2000+ baris), berikut adalah panduan untuk melakukan perubahan secara manual menggunakan Find & Replace di VS Code:

### Step 1: Background Colors
```
Find: bg-slate-950
Replace: bg-slate-50

Find: bg-slate-900
Replace: bg-white
(Hati-hati dengan bg-slate-900/40 dan bg-slate-900/60)
```

### Step 2: Text Colors
```
Find: text-slate-100
Replace: text-slate-900

Find: text-slate-200
Replace: text-slate-900

Find: text-slate-400
Replace: text-slate-500

Find: text-slate-300
Replace: text-slate-600
```

### Step 3: Borders
```
Find: border-slate-800/60
Replace: border-slate-200

Find: border-slate-800/80
Replace: border-slate-200

Find: border-slate-800
Replace: border-slate-300
```

### Step 4: Hover States
```
Find: hover:bg-slate-800/50
Replace: hover:bg-slate-100

Find: hover:bg-slate-800
Replace: hover:bg-slate-100

Find: hover:text-slate-200
Replace: hover:text-slate-900
```

### Step 5: Typography
```
Find: font-black
Replace: font-semibold

Find: font-extrabold
Replace: font-semibold

Find: tracking-widest
Replace: tracking-wide
```

### Step 6: Rounded Corners
```
Find: rounded-2xl
Replace: rounded-lg

Find: rounded-xl
Replace: rounded-md
```

### Step 7: Shadows (Remove)
```
Find: shadow-2xl
Replace: shadow-sm

Find:  shadow-lg shadow-blue-600/10
Replace: (kosongkan)

Find:  shadow-lg shadow-blue-500/20
Replace: (kosongkan)
```

### Step 8: Opacity Backgrounds
```
Find: bg-blue-600/10
Replace: bg-blue-100

Find: bg-rose-500/10
Replace: bg-red-50

Find: border-rose-500/20
Replace: border-red-200

Find: text-rose-400
Replace: text-red-700

Find: bg-emerald-500/10
Replace: bg-green-50

Find: border-emerald-500/20
Replace: border-green-200

Find: text-emerald-400
Replace: text-green-700

Find: bg-amber-500/10
Replace: bg-yellow-50

Find: border-amber-500/20
Replace: border-yellow-200

Find: text-amber-400
Replace: text-yellow-700
```

### Step 9: Gradients to Solid
```
Find: bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500
Replace: bg-blue-600 hover:bg-blue-700

Find: bg-gradient-to-r from-blue-600 via-purple-600 to-indigo-600
Replace: bg-blue-600
```

### Step 10: Special Cases

#### Login Page Icon Container
Find:
```tsx
<div className="w-16 h-16 bg-blue-600/10 border border-blue-500/20 rounded-2xl flex items-center justify-center text-blue-500 mb-4">
```
Replace:
```tsx
<div className="w-16 h-16 bg-blue-600 rounded-lg flex items-center justify-center text-white mb-4">
```

#### Sidebar Icon Container
Find:
```tsx
<div className="w-10 h-10 bg-blue-600/10 border border-blue-500/20 rounded-xl flex items-center justify-center text-blue-500 shrink-0">
```
Replace:
```tsx
<div className="w-10 h-10 bg-blue-600 rounded-lg flex items-center justify-center text-white shrink-0">
```

#### Active Button State
Pastikan semua button dengan `bg-blue-600` juga memiliki `text-white`:
```tsx
className="... bg-blue-600 text-white ..."
```

## 🎨 Komponen Spesifik

### Login Page
- Background: `bg-white`
- Card: `bg-white border border-slate-200 shadow-sm`
- Icon: `bg-blue-600 text-white`
- Button: `bg-blue-600 hover:bg-blue-700 text-white`

### Sidebar
- Background: `bg-white border-r border-slate-200`
- Active item: `bg-blue-600 text-white`
- Inactive item: `text-slate-600 hover:bg-slate-100`
- Footer: `bg-slate-50 border-t border-slate-200`

### Header
- Background: `bg-white border-b border-slate-200 shadow-sm`
- Title: `text-slate-900`
- Subtitle: `text-blue-600`

### Status Indicators
- Live: `bg-green-50 border-green-200 text-green-700`
- Error: `bg-red-50 border-red-200 text-red-700`
- Warning: `bg-yellow-50 border-yellow-200 text-yellow-700`

### Cards
- Background: `bg-white border border-slate-200 rounded-lg`
- Header: `bg-slate-50 border-b border-slate-200`

### Tables
- Header: `bg-slate-50 border-b border-slate-200`
- Row hover: `hover:bg-slate-50`
- Border: `border-slate-200`

### Pagination
- Background: `bg-white border-t border-slate-200`
- Active: `bg-blue-600 text-white`
- Inactive: `border border-slate-300 text-slate-600`

## ⚠️ Tips
1. Gunakan Find & Replace dengan "Match Whole Word" enabled
2. Lakukan satu per satu dan test setelah setiap perubahan
3. Simpan backup sebelum mulai
4. Test build dengan `npm run build` setelah selesai
5. Test di browser dengan `npm run dev`

## 🚀 Quick Start
1. Buka `src/app/page.tsx` di VS Code
2. Tekan `Ctrl+H` untuk Find & Replace
3. Ikuti step 1-10 di atas secara berurutan
4. Save file
5. Run `npm run build` untuk verify
6. Run `npm run dev` untuk test di browser

## 📝 Checklist
- [ ] Step 1: Background Colors
- [ ] Step 2: Text Colors
- [ ] Step 3: Borders
- [ ] Step 4: Hover States
- [ ] Step 5: Typography
- [ ] Step 6: Rounded Corners
- [ ] Step 7: Shadows
- [ ] Step 8: Opacity Backgrounds
- [ ] Step 9: Gradients
- [ ] Step 10: Special Cases
- [ ] Build successful
- [ ] Visual test in browser
- [ ] Mobile responsive test
