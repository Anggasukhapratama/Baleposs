# Design Guide - BaletPOS Web Admin

## Tema Warna

### Primary Colors
```css
Blue 600: #2563eb  /* Primary actions, active states */
Blue 700: #1d4ed8  /* Hover states */
Blue 800: #1e40af  /* Dark accents */
```

### Neutral Colors
```css
White: #ffffff     /* Main background */
Slate 50: #f8fafc  /* Secondary background */
Slate 100: #f1f5f9 /* Hover backgrounds */
Slate 200: #e2e8f0 /* Borders */
Slate 300: #cbd5e1 /* Input borders */
Slate 500: #64748b /* Secondary text */
Slate 600: #475569 /* Body text */
Slate 900: #0f172a /* Headings */
```

### Status Colors
```css
/* Success */
Green 50: #f0fdf4
Green 200: #bbf7d0
Green 500: #22c55e
Green 700: #15803d

/* Error */
Red 50: #fef2f2
Red 200: #fecaca
Red 500: #ef4444
Red 700: #b91c1c

/* Warning */
Yellow 50: #fefce8
Yellow 200: #fef08a
Yellow 500: #eab308
Yellow 700: #a16207
```

## Typography

### Font Sizes
- **Heading 1**: `text-2xl` (24px) - Page titles
- **Heading 2**: `text-xl` (20px) - Section titles
- **Heading 3**: `text-lg` (18px) - Card titles
- **Body**: `text-sm` (14px) - Default text
- **Small**: `text-xs` (12px) - Labels, captions
- **Tiny**: `text-[10px]` (10px) - Badges, tags

### Font Weights
- **Semibold**: `font-semibold` (600) - Headings, important text
- **Medium**: `font-medium` (500) - Labels, buttons
- **Normal**: `font-normal` (400) - Body text

## Spacing

### Padding
- **Extra Small**: `p-2` (8px)
- **Small**: `p-3` (12px)
- **Medium**: `p-4` (16px)
- **Large**: `p-5` (20px)
- **Extra Large**: `p-6` (24px)

### Gap
- **Tight**: `gap-1` (4px)
- **Small**: `gap-2` (8px)
- **Medium**: `gap-3` (12px)
- **Large**: `gap-4` (16px)

## Components

### Buttons

#### Primary Button
```tsx
<button className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-md transition">
  Primary Action
</button>
```

#### Secondary Button
```tsx
<button className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium rounded-md transition">
  Secondary Action
</button>
```

#### Outline Button
```tsx
<button className="px-4 py-2 border border-slate-300 hover:bg-slate-100 text-slate-700 font-medium rounded-md transition">
  Outline Action
</button>
```

### Cards
```tsx
<div className="bg-white border border-slate-200 rounded-lg p-4 shadow-sm">
  {/* Card content */}
</div>
```

### Input Fields
```tsx
<input 
  type="text"
  className="w-full bg-white border border-slate-300 rounded-md px-4 py-2.5 text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
  placeholder="Enter text..."
/>
```

### Badges

#### Info Badge
```tsx
<span className="px-2 py-1 bg-blue-100 border border-blue-200 text-blue-700 rounded-md text-xs font-medium">
  Info
</span>
```

#### Success Badge
```tsx
<span className="px-2 py-1 bg-green-50 border border-green-200 text-green-700 rounded-md text-xs font-medium">
  Success
</span>
```

#### Error Badge
```tsx
<span className="px-2 py-1 bg-red-50 border border-red-200 text-red-700 rounded-md text-xs font-medium">
  Error
</span>
```

### Tables
```tsx
<table className="w-full">
  <thead className="bg-slate-50 border-b border-slate-200">
    <tr>
      <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wide">
        Header
      </th>
    </tr>
  </thead>
  <tbody className="divide-y divide-slate-200">
    <tr className="hover:bg-slate-50">
      <td className="px-4 py-3 text-sm text-slate-900">
        Cell
      </td>
    </tr>
  </tbody>
</table>
```

## Layout

### Sidebar Width
- Desktop: `w-64` (256px)
- Mobile: Full width overlay

### Content Max Width
- No max width, uses `flex-1` for fluid layout

### Breakpoints
- Mobile: `< 768px`
- Tablet: `768px - 1024px`
- Desktop: `> 1024px`

## Icons
- Library: Lucide React
- Default size: `18px` for navigation, `16px` for inline
- Color: Inherits from parent text color

## Borders
- Default: `border-slate-200` (1px)
- Hover: `border-slate-300`
- Focus: `ring-2 ring-blue-500`

## Shadows
- Minimal usage
- Cards: `shadow-sm`
- Modals: `shadow-lg`
- Avoid: `shadow-2xl`, colored shadows

## Rounded Corners
- Small: `rounded-md` (6px) - buttons, inputs
- Medium: `rounded-lg` (8px) - cards, containers
- Large: `rounded-xl` (12px) - modals (if needed)
- Full: `rounded-full` - avatars, status dots

## Transitions
- Default: `transition` (all properties, 150ms)
- Specific: `transition-colors`, `transition-transform`
- Duration: Keep default (150ms)

## Best Practices

### DO ✅
- Use consistent spacing (multiples of 4px)
- Maintain color contrast for accessibility
- Use semantic HTML elements
- Keep font weights consistent
- Use border-based separation over shadows
- Test on mobile devices

### DON'T ❌
- Mix different blue shades randomly
- Use excessive shadows or glows
- Use gradients (keep it simple)
- Use very bold fonts (font-black)
- Use uppercase text everywhere
- Animate everything

## Accessibility
- Minimum contrast ratio: 4.5:1 for normal text
- Minimum contrast ratio: 3:1 for large text
- Focus indicators: Always visible
- Interactive elements: Minimum 44x44px touch target
- Color: Not the only indicator of state
