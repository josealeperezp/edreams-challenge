import { useQuery } from '@tanstack/react-query'
import { fetchHealth, fetchSystemInfo } from './api/system'

export default function App() {
  const info = useQuery({ queryKey: ['system-info'], queryFn: fetchSystemInfo })
  const health = useQuery({ queryKey: ['health'], queryFn: fetchHealth, refetchInterval: 5000 })

  return (
    <main className="container">
      <header>
        <h1>Flight Aggregation Dashboard</h1>
        <p className="muted">
          Scaffold. Search, filters and charts arrive in the next slices.
        </p>
      </header>

      <section className="cards">
        <article className="card">
          <h2>Backend</h2>
          {health.isPending && <p className="muted">Checking…</p>}
          {health.isError && <p className="status down">UNREACHABLE</p>}
          {health.data && (
            <>
              <p className={`status ${health.data.status === 'UP' ? 'up' : 'down'}`}>
                {health.data.status}
              </p>
              <ul className="plain">
                {Object.entries(health.data.components ?? {}).map(([name, component]) => (
                  <li key={name}>
                    <span className="mono">{name}</span>
                    <span className={`dot ${component.status === 'UP' ? 'up' : 'down'}`} />
                    {component.status}
                  </li>
                ))}
              </ul>
            </>
          )}
          {info.data && (
            <p className="muted">
              {info.data.application} · {info.data.version}
            </p>
          )}
        </article>

        <article className="card">
          <h2>Providers</h2>
          {info.isPending && <p className="muted">Loading…</p>}
          {info.isError && <p className="status down">Could not load provider configuration</p>}
          <ul className="plain">
            {info.data?.providers.map((provider) => (
              <li key={provider.id}>
                <strong>{provider.id}</strong>
                <span className="mono">{provider.baseUrl}</span>
                <span className="muted">timeout {provider.timeoutMillis} ms</span>
              </li>
            ))}
          </ul>
        </article>
      </section>
    </main>
  )
}
