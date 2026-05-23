#!/usr/bin/env python3
"""
Script untuk mengubah tema dari dark ke light dengan blue color scheme
"""

import re

def revamp_theme(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Background colors
    replacements = [
        # Backgrounds
        (r'\bbg-slate-950\b', 'bg-slate-50'),
        (r'\bbg-slate-900(?!/)\b', 'bg-white'),
        (r'\bbg-slate-900/40\b', 'bg-slate-50'),
        (r'\bbg-slate-900/60\b', 'bg-slate-50'),
        (r'\bbg-slate-900/50\b', 'bg-slate-50'),
        
        # Text colors
        (r'\btext-slate-100\b', 'text-slate-900'),
        (r'\btext-slate-200\b', 'text-slate-900'),
        (r'\btext-white(?![\w-])\b', 'text-slate-900'),
        (r'\btext-slate-400\b', 'text-slate-500'),
        (r'\btext-slate-300\b', 'text-slate-600'),
        
        # Borders
        (r'\bborder-slate-800/60\b', 'border-slate-200'),
        (r'\bborder-slate-800/80\b', 'border-slate-200'),
        (r'\bborder-slate-800\b', 'border-slate-300'),
        
        # Hover states
        (r'\bhover:bg-slate-800/50\b', 'hover:bg-slate-100'),
        (r'\bhover:bg-slate-800\b', 'hover:bg-slate-100'),
        (r'\bhover:text-slate-200\b', 'hover:text-slate-900'),
        (r'\bhover:text-white\b', 'hover:text-slate-900'),
        
        # Font weights
        (r'\bfont-black\b', 'font-semibold'),
        (r'\bfont-extrabold\b', 'font-semibold'),
        
        # Rounded corners
        (r'\brounded-2xl\b', 'rounded-lg'),
        (r'\brounded-xl\b', 'rounded-md'),
        
        # Shadows
        (r'\bshadow-2xl\b', 'shadow-sm'),
        (r'\bshadow-lg shadow-blue-600/10\b', ''),
        (r'\bshadow-lg shadow-blue-500/20\b', ''),
        (r'\bshadow-md shadow-blue-600/10\b', ''),
        
        # Tracking
        (r'\btracking-widest\b', 'tracking-wide'),
        (r'\btracking-wider\b', 'tracking-wide'),
        
        # Opacity backgrounds
        (r'\bbg-blue-600/10\b', 'bg-blue-100'),
        (r'\bbg-blue-500/20\b', 'bg-blue-100'),
        (r'\bbg-rose-500/10\b', 'bg-red-50'),
        (r'\bborder-rose-500/20\b', 'border-red-200'),
        (r'\btext-rose-400\b', 'text-red-700'),
        (r'\bbg-emerald-500/10\b', 'bg-green-50'),
        (r'\bborder-emerald-500/20\b', 'border-green-200'),
        (r'\btext-emerald-400\b', 'text-green-700'),
        (r'\bbg-amber-500/10\b', 'bg-yellow-50'),
        (r'\bborder-amber-500/20\b', 'border-yellow-200'),
        (r'\btext-amber-400\b', 'text-yellow-700'),
        
        # Gradients to solid
        (r'bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500', 'bg-blue-600 hover:bg-blue-700'),
        (r'bg-gradient-to-r from-blue-600 via-purple-600 to-indigo-600', 'bg-blue-600'),
        (r'bg-gradient-to-br from-slate-900 to-slate-950', 'bg-white'),
        
        # Animations
        (r'\banimate-pulse\b', ''),
        
        # Disabled states
        (r'\bdisabled:opacity-50\b', 'disabled:opacity-40 disabled:cursor-not-allowed'),
    ]
    
    for pattern, replacement in replacements:
        content = re.sub(pattern, replacement, content)
    
    # Special fixes for buttons with bg-blue-600 to ensure white text
    content = re.sub(
        r'(className="[^"]*bg-blue-600[^"]*)"',
        lambda m: m.group(0) if 'text-white' in m.group(0) else m.group(0).replace('"', ' text-white"'),
        content
    )
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    
    print(f"✅ Theme revamp completed for {file_path}")

if __name__ == '__main__':
    revamp_theme('e:\\Client java project\\baletpos-new\\web-admin\\src\\app\\page.tsx')
