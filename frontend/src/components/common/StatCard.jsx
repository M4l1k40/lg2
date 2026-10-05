export default function StatCard({ label, value, trend, accent = 'blue' }) {
  const accentClasses = {
    blue: 'bg-blue-100 text-blue-700',
    violet: 'bg-violet-100 text-violet-700',
    amber: 'bg-amber-100 text-amber-700',
    emerald: 'bg-emerald-100 text-emerald-700',
    rose: 'bg-rose-100 text-rose-700',
    slate: 'bg-slate-100 text-slate-700',
    cyan: 'bg-cyan-100 text-cyan-700'
  };

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <span className="text-sm text-slate-500">{label}</span>
        <span className={`rounded-full px-2 py-1 text-xs font-medium ${accentClasses[accent] || accentClasses.blue}`}>
          {trend}
        </span>
      </div>
      <div className="mt-6 text-3xl font-bold text-slate-900">{value}</div>
    </div>
  );
}
