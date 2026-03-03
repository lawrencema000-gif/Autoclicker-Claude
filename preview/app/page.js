'use client'

import { useState, useRef, useEffect, useCallback } from 'react'

const PHONE_W = 390
const PHONE_H = 844

export default function Home() {
  const [screen, setScreen] = useState('onboarding') // onboarding, main, overlay, editor, settings
  const [tab, setTab] = useState(0)
  const [mode, setMode] = useState('single')
  const [running, setRunning] = useState(false)
  const [paused, setPaused] = useState(false)
  const [clickCount, setClickCount] = useState(0)
  const [elapsed, setElapsed] = useState(0)
  const [loop, setLoop] = useState(1)
  const [accessibilityEnabled, setAccessibilityEnabled] = useState(false)
  const [targets, setTargets] = useState([])
  const [profiles, setProfiles] = useState([])
  const [settings, setSettings] = useState({ interval: 100, hold: 10, swipe: 350, stopCondition: 'never', stopValue: 0 })
  const [dragging, setDragging] = useState(null)
  const [floatingPos, setFloatingPos] = useState({ x: 5, y: 150 })
  const [pickMode, setPickMode] = useState(false)
  const [currentTouch, setCurrentTouch] = useState(null)
  const timerRef = useRef(null)
  const elapsedRef = useRef(null)

  const stopAll = useCallback(() => {
    setRunning(false); setPaused(false); if (timerRef.current) clearInterval(timerRef.current)
    if (elapsedRef.current) clearInterval(elapsedRef.current)
  }, [])

  const startClicking = useCallback(() => {
    setRunning(true); setPaused(false); setClickCount(0); setElapsed(0); setLoop(1)
    const startTime = Date.now()
    timerRef.current = window.setInterval(() => setClickCount(c => c + 1), settings.interval)
    elapsedRef.current = window.setInterval(() => setElapsed(Math.floor((Date.now() - startTime) / 1000)), 1000)
  }, [settings.interval])

  useEffect(() => () => { if (timerRef.current) clearInterval(timerRef.current); if (elapsedRef.current) clearInterval(elapsedRef.current) }, [])

  const formatTime = (s) => s >= 3600 ? `${Math.floor(s/3600)}:${String(Math.floor((s%3600)/60)).padStart(2,'0')}:${String(s%60).padStart(2,'0')}` : `${Math.floor(s/60)}:${String(s%60).padStart(2,'0')}`

  const handleMouseDown = (e, type, id) => { e.preventDefault(); setDragging({ type, id, sx: e.clientX, sy: e.clientY }) }
  const handleMouseMove = useCallback((e) => {
    if (!dragging) return
    const dx = e.clientX - dragging.sx, dy = e.clientY - dragging.sy
    if (dragging.type === 'target') setTargets(p => p.map(t => t.id === dragging.id ? { ...t, x: t.x + dx, y: t.y + dy } : t))
    else if (dragging.type === 'float') setFloatingPos(p => ({ x: p.x + dx, y: p.y + dy }))
    setDragging(p => p ? { ...p, sx: e.clientX, sy: e.clientY } : null)
  }, [dragging])
  const handleMouseUp = useCallback(() => setDragging(null), [])
  useEffect(() => { if (dragging) { window.addEventListener('mousemove', handleMouseMove); window.addEventListener('mouseup', handleMouseUp); return () => { window.removeEventListener('mousemove', handleMouseMove); window.removeEventListener('mouseup', handleMouseUp) } } }, [dragging, handleMouseMove, handleMouseUp])

  // ===== ONBOARDING =====
  const renderOnboarding = () => (
    <div style={{ padding: 24, display: 'flex', flexDirection: 'column', height: '100%', boxSizing: 'border-box' }}>
      <div style={{ flex: 1 }}>
        <div style={{ width: 80, height: 80, borderRadius: '50%', background: '#0E3A5C', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '40px auto 20px' }}>
          <span style={{ fontSize: 36 }}>👆</span>
        </div>
        <h2 style={{ color: '#E8ECF4', textAlign: 'center', margin: '0 0 4px' }}>Welcome to Auto Clicker</h2>
        <p style={{ color: '#8B95B0', textAlign: 'center', fontSize: 13, margin: '0 0 30px' }}>Complete these steps to get started</p>

        {/* Step 1 */}
        <div style={{ ...S.card, background: accessibilityEnabled ? 'rgba(14,61,46,0.3)' : '#1A2035', marginBottom: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 40, height: 40, borderRadius: '50%', background: accessibilityEnabled ? '#34D399' : '#38BDF8', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#0B0F19', fontWeight: 'bold', fontSize: 14 }}>
              {accessibilityEnabled ? '✓' : '1'}
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ color: '#E8ECF4', fontWeight: 600, fontSize: 14 }}>Enable Accessibility Service</div>
              <div style={{ color: '#8B95B0', fontSize: 11 }}>Required to perform automatic taps</div>
            </div>
            <button style={{ ...S.tonalBtn, opacity: accessibilityEnabled ? 0.5 : 1 }} onClick={() => setAccessibilityEnabled(true)}>
              {accessibilityEnabled ? 'Enabled' : 'Open Settings'}
            </button>
          </div>
        </div>

        {/* Step 2 */}
        <div style={{ ...S.card, background: '#1A2035' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 40, height: 40, borderRadius: '50%', background: '#38BDF8', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#0B0F19', fontWeight: 'bold', fontSize: 14 }}>2</div>
            <div style={{ flex: 1 }}>
              <div style={{ color: '#E8ECF4', fontWeight: 600, fontSize: 14 }}>Disable Battery Optimization</div>
              <div style={{ color: '#8B95B0', fontSize: 11 }}>Recommended to prevent stopping</div>
            </div>
            <button style={S.tonalBtn}>Optimize</button>
          </div>
        </div>
      </div>

      <button style={{ ...S.primaryBtn, opacity: accessibilityEnabled ? 1 : 0.5 }} onClick={() => { if (accessibilityEnabled) setScreen('main') }}>Get Started</button>
      <button style={{ ...S.textBtn, marginTop: 8 }} onClick={() => setScreen('main')}>Skip for now</button>
    </div>
  )

  // ===== HOME SCREEN =====
  const renderHome = () => (
    <div style={{ padding: 20, display: 'flex', flexDirection: 'column', height: '100%', boxSizing: 'border-box' }}>
      <h2 style={{ color: '#E8ECF4', margin: '0 0 16px', fontSize: 22 }}>Auto Clicker</h2>

      <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
        {[['1-Point', 'Single tap', '👆', 'single'], ['Multi-Point', 'Sequence', '📱', 'multi']].map(([t, d, ic, m]) => (
          <div key={m} onClick={() => setMode(m)} style={{
            flex: 1, padding: 16, borderRadius: 16, cursor: 'pointer', textAlign: 'center',
            background: mode === m ? '#0E3A5C' : '#1A2035',
            border: mode === m ? 'none' : '1px solid #2A3250',
            transition: 'all 0.2s'
          }}>
            <div style={{ fontSize: 28, marginBottom: 8, filter: mode === m ? 'none' : 'grayscale(1) opacity(0.5)' }}>{ic}</div>
            <div style={{ color: '#E8ECF4', fontWeight: 600, fontSize: 14 }}>{t}</div>
            <div style={{ color: '#8B95B0', fontSize: 11 }}>{d}</div>
          </div>
        ))}
      </div>

      {running && (
        <div style={{ ...S.card, background: '#1A2035', display: 'flex', justifyContent: 'space-around', padding: 16 }}>
          {[['Taps', clickCount], ['Time', formatTime(elapsed)], ['Loop', loop]].map(([l, v]) => (
            <div key={l} style={{ textAlign: 'center' }}>
              <div style={{ color: '#38BDF8', fontWeight: 'bold', fontSize: 22 }}>{v}</div>
              <div style={{ color: '#8B95B0', fontSize: 11 }}>{l}</div>
            </div>
          ))}
        </div>
      )}

      <div style={{ flex: 1 }} />

      <button style={{ ...S.primaryBtn, background: running ? '#F87171' : '#38BDF8', transition: 'background 0.3s' }}
        onClick={() => { if (running) { stopAll(); } else { setScreen('overlay'); setPickMode(true); setTargets([]) } }}>
        <span style={{ fontSize: 20, marginRight: 8 }}>{running ? '⏹' : '▶'}</span>
        {running ? 'STOP' : 'START'}
      </button>

      {!accessibilityEnabled && (
        <p style={{ color: '#F87171', fontSize: 12, textAlign: 'center', marginTop: 8 }}>
          Accessibility Service not enabled. Go to Settings to enable it.
        </p>
      )}
    </div>
  )

  // ===== PICK / OVERLAY =====
  const renderOverlay = () => (
    <div style={{ position: 'relative', width: '100%', height: '100%', background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)', overflow: 'hidden' }}>
      {/* Background items */}
      <div style={{ padding: 30, opacity: 0.2 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8, marginTop: 20 }}>
          {[...Array(9)].map((_, i) => <div key={i} style={{ background: '#ffffff11', borderRadius: 10, height: 70 }} />)}
        </div>
      </div>

      {pickMode && (
        <>
          {/* Pick overlay */}
          <div style={{ position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.3)', display: 'flex', flexDirection: 'column', alignItems: 'center' }}
            onClick={(e) => {
              const rect = e.currentTarget.getBoundingClientRect()
              const x = e.clientX - rect.left, y = e.clientY - rect.top
              const newTarget = { x, y, id: Date.now() }
              setTargets(p => [...p, newTarget])
              if (mode === 'single') { setPickMode(false); startClicking() }
            }}>
            <p style={{ color: 'rgba(255,255,255,0.8)', marginTop: 60, fontSize: 13 }}>
              {mode === 'multi' ? 'Tap to add points, press DONE when finished' : 'Tap anywhere to select a point'}
            </p>
          </div>
          {mode === 'multi' && targets.length > 0 && (
            <button style={{ position: 'absolute', bottom: 80, right: 30, background: '#38BDF8', color: '#fff', border: 'none', borderRadius: 16, padding: '12px 28px', fontWeight: 'bold', fontSize: 14, cursor: 'pointer', zIndex: 50 }}
              onClick={() => { setPickMode(false); startClicking() }}>DONE</button>
          )}
        </>
      )}

      {/* Targets */}
      {targets.map((t, i) => (
        <div key={t.id} style={{ position: 'absolute', left: t.x - 24, top: t.y - 24, width: 48, height: 48, zIndex: 40 }}
          onMouseDown={(e) => { e.stopPropagation(); handleMouseDown(e, 'target', t.id) }}>
          <svg width="48" height="48"><circle cx="24" cy="24" r="22" fill="rgba(56,189,248,0.15)" stroke="#38BDF8" strokeWidth="2" />
            <line x1="0" y1="24" x2="48" y2="24" stroke="#38BDF8" strokeWidth="1" /><line x1="24" y1="0" x2="24" y2="48" stroke="#38BDF8" strokeWidth="1" />
            <circle cx="24" cy="24" r="12" fill="rgba(56,189,248,0.4)" stroke="#38BDF8" strokeWidth="2" />
            <text x="24" y="28" textAnchor="middle" fill="#fff" fontSize="11" fontWeight="bold">{i + 1}</text></svg>
          {running && !paused && <div style={{ position: 'absolute', inset: 0, borderRadius: '50%', border: '2px solid #38BDF8', animation: 'pulse .6s ease-out infinite' }} />}
        </div>
      ))}

      {/* Floating toolbar */}
      {!pickMode && (
        <div style={{ position: 'absolute', left: floatingPos.x, top: floatingPos.y, width: 130, background: 'rgba(30,35,50,0.92)', borderRadius: 20, padding: 10, zIndex: 100, backdropFilter: 'blur(10px)' }}
          onMouseDown={(e) => handleMouseDown(e, 'float', null)}>
          <div style={{ color: '#8B95B0', fontSize: 11, textAlign: 'center' }}>{clickCount} taps</div>
          <div style={{ color: '#8B95B0', fontSize: 11, textAlign: 'center', marginBottom: 8 }}>{formatTime(elapsed)}</div>
          <div style={{ display: 'flex', gap: 6, justifyContent: 'center' }}>
            {!paused ? (
              <button style={{ ...S.circleBtn, background: '#34D399' }} onClick={(e) => { e.stopPropagation(); setPaused(true); clearInterval(timerRef.current); clearInterval(elapsedRef.current) }}>⏸</button>
            ) : (
              <button style={{ ...S.circleBtn, background: '#38BDF8' }} onClick={(e) => { e.stopPropagation(); setPaused(false); /* simplified resume */ }}>▶</button>
            )}
            <button style={{ ...S.circleBtn, background: '#F87171' }} onClick={(e) => { e.stopPropagation(); stopAll(); setScreen('main') }}>⏹</button>
          </div>
          <div style={{ color: '#8B95B0', fontSize: 10, textAlign: 'center', marginTop: 6 }}>Loop {loop} • Step 1</div>
        </div>
      )}

      <style>{`@keyframes pulse { 0% { transform: scale(1); opacity: 1; } 100% { transform: scale(2); opacity: 0; } }`}</style>
    </div>
  )

  // ===== SCRIPTS =====
  const renderScripts = () => (
    <div style={{ padding: 20, height: '100%', boxSizing: 'border-box' }}>
      <h2 style={{ color: '#E8ECF4', margin: '0 0 16px', fontSize: 20 }}>Scripts</h2>
      {profiles.length === 0 ? (
        <div style={{ textAlign: 'center', marginTop: 80, color: '#8B95B0' }}>
          <div style={{ fontSize: 48, opacity: 0.3, marginBottom: 16 }}>📄</div>
          <div style={{ fontSize: 16 }}>No scripts yet</div>
          <div style={{ fontSize: 12, opacity: 0.7 }}>Start a quick session or import a script</div>
        </div>
      ) : profiles.map(p => (
        <div key={p.id} style={{ ...S.card, background: '#1A2035', marginBottom: 10, display: 'flex', alignItems: 'center', padding: '12px 16px' }}>
          <div style={{ flex: 1 }}>
            <div style={{ color: '#E8ECF4', fontWeight: 600, fontSize: 14 }}>{p.name}</div>
            <div style={{ color: '#8B95B0', fontSize: 11 }}>{p.mode} • {p.steps} steps</div>
          </div>
          <button style={{ ...S.circleBtn, background: '#34D399' }}>▶</button>
          <button style={{ ...S.circleBtn, background: 'transparent', marginLeft: 4 }}>⋮</button>
        </div>
      ))}
    </div>
  )

  // ===== SETTINGS =====
  const renderSettings = () => (
    <div style={{ padding: 20, height: '100%', overflowY: 'auto', boxSizing: 'border-box' }}>
      <h2 style={{ color: '#E8ECF4', margin: '0 0 16px', fontSize: 20 }}>Settings</h2>

      <div style={{ ...S.card, background: '#1A2035', marginBottom: 12 }}>
        <div style={{ color: '#E8ECF4', fontWeight: 600, fontSize: 13, marginBottom: 14 }}>Default Values</div>
        {[['Tap Interval', 'interval', 'ms'], ['Tap & Hold Duration', 'hold', 'ms'], ['Swipe Duration', 'swipe', 'ms']].map(([label, key, unit]) => (
          <div key={key} style={{ marginBottom: 12 }}>
            <div style={{ color: '#8B95B0', fontSize: 11, marginBottom: 4 }}>{label}</div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input style={S.input} type="number" value={settings[key]} onChange={e => setSettings(s => ({ ...s, [key]: +e.target.value }))} />
              <span style={{ background: '#0E3A5C', color: '#38BDF8', padding: '4px 10px', borderRadius: 8, fontSize: 11 }}>{unit}</span>
            </div>
          </div>
        ))}
      </div>

      {/* Accessibility card */}
      <div style={{ ...S.card, background: accessibilityEnabled ? '#1A2035' : '#3D1515', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 12 }}>
        <span style={{ fontSize: 20 }}>{accessibilityEnabled ? '♿' : '⚠'}</span>
        <div style={{ flex: 1 }}>
          <div style={{ color: '#E8ECF4', fontWeight: 600, fontSize: 13 }}>Accessibility Service</div>
          <div style={{ color: accessibilityEnabled ? '#34D399' : '#F87171', fontSize: 11 }}>{accessibilityEnabled ? 'Enabled' : 'Disabled'}</div>
        </div>
        <button style={S.tonalBtn} onClick={() => setAccessibilityEnabled(true)}>Settings</button>
      </div>

      {/* Battery card */}
      <div style={{ ...S.card, background: '#1A2035', display: 'flex', alignItems: 'center', gap: 12 }}>
        <span style={{ fontSize: 20 }}>🔋</span>
        <div style={{ flex: 1 }}>
          <div style={{ color: '#E8ECF4', fontWeight: 600, fontSize: 13 }}>Battery Optimization</div>
          <div style={{ color: '#8B95B0', fontSize: 11 }}>Disable to prevent stopping</div>
        </div>
        <button style={S.tonalBtn}>Optimize</button>
      </div>
    </div>
  )

  // ===== RENDER =====
  const showTabs = screen === 'main'
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', background: '#050508', padding: 20, fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' }}>
      <h1 style={{ color: '#E8ECF4', fontSize: 18, marginBottom: 4 }}>Auto Clicker — Interactive Preview</h1>
      <p style={{ color: '#8B95B0', fontSize: 12, marginBottom: 14 }}>
        {screen === 'onboarding' ? 'Onboarding' : screen === 'overlay' ? 'Overlay Mode — drag targets & toolbar' : ['Clicker', 'Scripts', 'Settings'][tab]}
      </p>

      {/* Phone */}
      <div style={{ width: PHONE_W, height: PHONE_H, borderRadius: 44, border: '3px solid #2A3250', overflow: 'hidden', background: '#0B0F19', position: 'relative', boxShadow: '0 24px 80px rgba(0,0,0,0.6)' }}>
        {/* Status bar */}
        <div style={{ height: 50, background: '#0B0F19', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px', fontSize: 12, color: '#8B95B0' }}>
          <span>12:00</span><span>⚡ 85%</span>
        </div>

        <div style={{ height: showTabs ? PHONE_H - 50 - 60 : PHONE_H - 50, overflow: 'hidden' }}>
          {screen === 'onboarding' && renderOnboarding()}
          {screen === 'main' && tab === 0 && renderHome()}
          {screen === 'main' && tab === 1 && renderScripts()}
          {screen === 'main' && tab === 2 && renderSettings()}
          {screen === 'overlay' && renderOverlay()}
        </div>

        {/* Bottom nav */}
        {showTabs && (
          <div style={{ height: 60, background: '#131825', display: 'flex', borderTop: '1px solid #2A3250' }}>
            {[['👆', 'Clicker'], ['📄', 'Scripts'], ['⚙', 'Settings']].map(([ic, label], i) => (
              <div key={i} onClick={() => setTab(i)} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: tab === i ? '#38BDF8' : '#8B95B0', transition: 'color 0.2s' }}>
                <span style={{ fontSize: 18 }}>{ic}</span>
                <span style={{ fontSize: 10, marginTop: 2 }}>{label}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      <p style={{ color: '#2A3250', fontSize: 10, marginTop: 10 }}>Built with Claude Code — github.com/lawrencema000-gif/Autoclicker-Claude</p>
    </div>
  )
}

const S = {
  card: { borderRadius: 16, padding: 16 },
  primaryBtn: { width: '100%', padding: 16, borderRadius: 16, border: 'none', background: '#38BDF8', color: '#0B0F19', fontSize: 15, fontWeight: 'bold', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' },
  textBtn: { width: '100%', padding: 8, background: 'none', border: 'none', color: '#8B95B0', fontSize: 13, cursor: 'pointer' },
  tonalBtn: { padding: '6px 14px', borderRadius: 12, border: 'none', background: 'rgba(56,189,248,0.15)', color: '#38BDF8', fontSize: 11, fontWeight: 600, cursor: 'pointer', whiteSpace: 'nowrap' },
  circleBtn: { width: 36, height: 36, borderRadius: '50%', border: 'none', color: '#fff', fontSize: 14, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' },
  input: { flex: 1, padding: '8px 12px', borderRadius: 10, border: '1px solid #2A3250', background: '#131825', color: '#E8ECF4', fontSize: 13, outline: 'none', width: '100%', boxSizing: 'border-box' },
}
