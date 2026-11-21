import { Routes, Route } from 'react-router-dom';

function HomePage() {
  return (
    <main className="mx-auto max-w-3xl p-6">
      <h1 className="text-2xl font-bold tracking-tight">
        The Bank of Banco Imobiliário
      </h1>
      <p className="mt-2 text-sm text-slate-600">
        Fast, accurate table banking for your board game session.
      </p>
    </main>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
    </Routes>
  );
}
