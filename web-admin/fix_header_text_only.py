#!/usr/bin/env python3
"""
Hanya ubah text color di header menjadi putih
Jangan ubah tema keseluruhan
"""

import re

def fix_header_text_only(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Hanya ganti text-slate-600 menjadi text-white di dalam <th> tags
    # Pattern: <th className="... text-slate-600 ...">
    
    replacements = [
        # Stock Opname headers
        (r'<th className="p-4 font-semibold text-slate-600 text-right">SISTEM</th>',
         '<th className="p-4 font-semibold text-white text-right">SISTEM</th>'),
        
        (r'<th className="p-4 font-semibold text-slate-600 text-right">FISIK AKTUAL</th>',
         '<th className="p-4 font-semibold text-white text-right">FISIK AKTUAL</th>'),
        
        (r'<th className="p-4 font-semibold text-slate-600 text-right">SELISIH</th>',
         '<th className="p-4 font-semibold text-white text-right">SELISIH</th>'),
        
        # Penjualan - TOTAL TRANSAKSI
        (r'<th className="p-4 font-semibold text-slate-600 text-right">TOTAL TRANSAKSI</th>',
         '<th className="p-4 font-semibold text-white text-right">TOTAL TRANSAKSI</th>'),
        
        # Pembelian - TOTAL
        (r'<th className="p-4 font-semibold text-slate-600 text-right">TOTAL</th>',
         '<th className="p-4 font-semibold text-white text-right">TOTAL</th>'),
        
        # Biaya - NOMINAL
        (r'<th className="p-4 font-semibold text-slate-600 text-right">NOMINAL</th>',
         '<th className="p-4 font-semibold text-white text-right">NOMINAL</th>'),
        
        # Retur/Mutasi - QTY
        (r'<th className="p-4 font-semibold text-slate-600 text-right">QTY</th>',
         '<th className="p-4 font-semibold text-white text-right">QTY</th>'),
    ]
    
    for old, new in replacements:
        content = content.replace(old, new)
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    
    print("✅ Hanya text header yang diubah menjadi putih!")
    print("✅ Tema keseluruhan tetap putih seperti semula!")

if __name__ == '__main__':
    fix_header_text_only('e:\\Client java project\\baletpos-new\\web-admin\\src\\app\\page.tsx')
