export default function PageNotFound() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center shadow-sm">
        <div className="text-sm font-semibold uppercase tracking-[0.15em] text-slate-500">404</div>
        <h1 className="mt-3 text-3xl font-bold text-slate-900">Page introuvable</h1>
        <p className="mt-2 text-slate-600">Cette page n’existe pas ou a été déplacée.</p>
      </div>
    </div>
  );
}
