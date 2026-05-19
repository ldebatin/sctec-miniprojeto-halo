import { Route, Routes } from 'react-router-dom';

function Home() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-50">
      <h1 className="text-4xl font-bold text-blue-500">Halo</h1>
    </main>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
    </Routes>
  );
}
