import { NavLink } from 'react-router-dom';
import { useLanguage } from '../../context/LanguageContext';

export default function SidebarNav({ title, subtitle, items, footerLabel }) {
  const { t } = useLanguage();

  return (
    <aside className="hidden w-72 shrink-0 border-r border-slate-200 bg-slate-950 text-slate-100 lg:flex lg:flex-col">
      <div className="border-b border-slate-800 px-6 py-5">
        <div className="text-xl font-bold tracking-tight">{title}</div>
        <div className="mt-1 text-xs uppercase tracking-[0.2em] text-slate-400">{subtitle}</div>
      </div>

      <nav className="flex-1 space-y-1 p-4">
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              `flex items-center justify-between rounded-xl px-3 py-2.5 text-sm font-medium transition ${
                isActive
                  ? 'bg-slate-800 text-white shadow-sm'
                  : 'text-slate-300 hover:bg-slate-800/70 hover:text-white'
              }`
            }
          >
            <span>{t[item.labelKey] ?? item.label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-slate-800 p-4 text-sm text-slate-400">
        <div className="font-medium text-slate-200">{footerLabel}</div>
        <div className="mt-1">{t.common.dashboard}</div>
      </div>
    </aside>
  );
}
