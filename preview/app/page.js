'use client'

import { useState, useRef, useEffect, useCallback, useMemo } from 'react'

const PHONE_W = 390
const PHONE_H = 844

export default function Home() {
  const [screen, setScreen] = useState('onboarding')
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
  const [settings, setSettings] = useState({
    interval: 100, hold: 10, swipe: 350,
    stopCondition: 'never', stopValue: 0,
    speedMode: 'interval',
    antiDetection: {
      randomPositionOffset: false, positionOffsetRadius: 15,
      intervalJitter: false, jitterPercent: 20,
      humanizeHold: false, avoidRepetition: false, microPauses: false
    }
  })
  const [dragging, setDragging] = useState(null)
  const [floatingPos, setFloatingPos] = useState({ x: 5, y: 150 })
  const [pickMode, setPickMode] = useState(false)
  const [patternType, setPatternType] = useState('circle')
  const [patternConfig, setPatternConfig] = useState({
    pointCount: 8, radius: 100,
    gridRows: 3, gridCols: 3, gridSpacing: 40,
    spiralRevolutions: 2,
    areaWidth: 200, areaHeight: 200
  })
  const [customPoints, setCustomPoints] = useState([])
  const [customPickMode, setCustomPickMode] = useState(false)
  const timerRef = useRef(null)
  const elapsedRef = useRef(null)
  const randomSeedRef = useRef(Date.now())

  const stopAll = useCallback(() => {
    setRunning(false); setPaused(false)
    if (timerRef.current) clearInterval(timerRef.current)
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

  // ===== PATTERN GENERATOR =====
  const generatePatternPoints = useCallback((type, config) => {
    const { pointCount, radius, gridRows, gridCols, gridSpacing, spiralRevolutions, areaWidth, areaHeight } = config
    const points = []
    switch (type) {
      case 'circle':
        for (let i = 0; i < pointCount; i++) {
          const angle = (2 * Math.PI * i) / pointCount
          points.push({ x: radius * Math.cos(angle), y: radius * Math.sin(angle), order: i })
        }
        break
      case 'zigzag': {
        const stepX = areaWidth / Math.max(pointCount - 1, 1)
        const startX = -areaWidth / 2
        const topY = -areaHeight / 2
        const bottomY = areaHeight / 2
        for (let i = 0; i < pointCount; i++) {
          points.push({ x: startX + stepX * i, y: i % 2 === 0 ? topY : bottomY, order: i })
        }
        break
      }
      case 'grid': {
        let idx = 0
        const gStartX = -(gridCols - 1) * gridSpacing / 2
        const gStartY = -(gridRows - 1) * gridSpacing / 2
        for (let r = 0; r < gridRows; r++) {
          for (let c = 0; c < gridCols; c++) {
            points.push({ x: gStartX + c * gridSpacing, y: gStartY + r * gridSpacing, order: idx++ })
          }
        }
        break
      }
      case 'spiral': {
        const maxAngle = 2 * Math.PI * spiralRevolutions
        for (let i = 0; i < pointCount; i++) {
          const t = i / Math.max(pointCount - 1, 1)
          const angle = maxAngle * t
          const r = radius * t
          points.push({ x: r * Math.cos(angle), y: r * Math.sin(angle), order: i })
        }
        break
      }
      case 'diamond': {
        const perSide = Math.max(Math.floor(pointCount / 4), 1)
        const corners = [[0, -radius], [radius, 0], [0, radius], [-radius, 0]]
        let dIdx = 0
        for (let side = 0; side < 4; side++) {
          const [sx, sy] = corners[side]
          const [ex, ey] = corners[(side + 1) % 4]
          for (let p = 0; p < perSide; p++) {
            const t = p / perSide
            points.push({ x: sx + (ex - sx) * t, y: sy + (ey - sy) * t, order: dIdx++ })
          }
        }
        break
      }
      case 'random': {
        // Use seeded pseudo-random for stable preview
        let seed = randomSeedRef.current
        const seededRandom = () => { seed = (seed * 16807 + 0) % 2147483647; return seed / 2147483647 }
        for (let i = 0; i < pointCount; i++) {
          points.push({
            x: (seededRandom() - 0.5) * areaWidth,
            y: (seededRandom() - 0.5) * areaHeight,
            order: i
          })
        }
        break
      }
    }
    return points
  }, [])

  // Compute preview points
  const previewPoints = useMemo(() => {
    if (patternType === 'custom') {
      return customPoints.map((p, i) => ({ x: p.x - PHONE_W / 2, y: p.y - PHONE_H / 2, order: i }))
    }
    return generatePatternPoints(patternType, patternConfig)
  }, [patternType, patternConfig, customPoints, generatePatternPoints])

  // Scale preview points to fit SVG
  const scaledPreview = useMemo(() => {
    if (previewPoints.length === 0) return []
    const svgW = 180, svgH = 140, pad = 20
    const maxAbsX = Math.max(...previewPoints.map(p => Math.abs(p.x)), 1)
    const maxAbsY = Math.max(...previewPoints.map(p => Math.abs(p.y)), 1)
    const scaleX = (svgW - pad * 2) / 2 / maxAbsX
    const scaleY = (svgH - pad * 2) / 2 / maxAbsY
    const scale = Math.min(scaleX, scaleY)
    return previewPoints.map(p => ({
      x: svgW / 2 + p.x * scale,
      y: svgH / 2 + p.y * scale,
      order: p.order
    }))
  }, [previewPoints])

  // ===== ONBOARDING =====
  const renderOnboarding = () => (
    <div style={{ padding: 24, display: 'flex', flexDirection: 'column', height: '100%', boxSizing: 'border-box' }}>
      <div style={{ flex: 1 }}>
        <div style={{ width: 80, height: 80, borderRadius: '50%', background: '#0E3A5C', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '40px auto 20px' }}>
          <span style={{ fontSize: 36 }}>👆</span>
        </div>
        <h2 style={{ color: '#E8ECF4', textAlign: 'center', margin: '0 0 4px' }}>Welcome to Auto Clicker</h2>
        <p style={{ color: '#8B95B0', textAlign: 'center', fontSize: 13, margin: '0 0 30px' }}>Complete these steps to get started</p>

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

  // ===== PATTERN CONFIG SLIDERS =====
  const renderSlider = (label, value, min, max, step, onChange) => (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <span style={{ color: '#8B95B0', fontSize: 10, width: 60, flexShrink: 0 }}>{label}</span>
      <input type="range" min={min} max={max} step={step || 1} value={value}
        onChange={e => onChange(+e.target.value)}
        style={{ flex: 1, accentColor: '#38BDF8', height: 4 }} />
      <span style={{ color: '#38BDF8', fontSize: 10, width: 32, textAlign: 'right', flexShrink: 0 }}>
        {Number.isInteger(value) ? value : value.toFixed(1)}
      </span>
    </div>
  )

  const renderPatternSliders = () => {
    const update = (key, val) => setPatternConfig(prev => ({ ...prev, [key]: val }))
    switch (patternType) {
      case 'circle':
        return <>
          {renderSlider('Points', patternConfig.pointCount, 3, 24, 1, v => update('pointCount', v))}
          {renderSlider('Radius', patternConfig.radius, 30, 200, 1, v => update('radius', v))}
        </>
      case 'zigzag':
        return <>
          {renderSlider('Points', patternConfig.pointCount, 3, 20, 1, v => update('pointCount', v))}
          {renderSlider('Width', patternConfig.areaWidth, 50, 300, 1, v => update('areaWidth', v))}
          {renderSlider('Height', patternConfig.areaHeight, 50, 300, 1, v => update('areaHeight', v))}
        </>
      case 'grid':
        return <>
          {renderSlider('Rows', patternConfig.gridRows, 2, 8, 1, v => update('gridRows', v))}
          {renderSlider('Columns', patternConfig.gridCols, 2, 8, 1, v => update('gridCols', v))}
          {renderSlider('Spacing', patternConfig.gridSpacing, 20, 100, 1, v => update('gridSpacing', v))}
        </>
      case 'spiral':
        return <>
          {renderSlider('Points', patternConfig.pointCount, 4, 30, 1, v => update('pointCount', v))}
          {renderSlider('Radius', patternConfig.radius, 30, 200, 1, v => update('radius', v))}
          {renderSlider('Turns', patternConfig.spiralRevolutions, 1, 5, 0.5, v => update('spiralRevolutions', v))}
        </>
      case 'diamond':
        return <>
          {renderSlider('Points', patternConfig.pointCount, 4, 24, 4, v => update('pointCount', v))}
          {renderSlider('Radius', patternConfig.radius, 30, 200, 1, v => update('radius', v))}
        </>
      case 'random':
        return <>
          {renderSlider('Points', patternConfig.pointCount, 3, 20, 1, v => update('pointCount', v))}
          {renderSlider('Width', patternConfig.areaWidth, 50, 300, 1, v => update('areaWidth', v))}
          {renderSlider('Height', patternConfig.areaHeight, 50, 300, 1, v => update('areaHeight', v))}
        </>
      default:
        return null
    }
  }

  // ===== CUSTOM POINTS LIST =====
  const renderCustomPointsList = () => {
    if (customPoints.length === 0) return null
    return (
      <div style={{ marginTop: 6 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
          <span style={{ color: '#8B95B0', fontSize: 10, fontWeight: 600 }}>Custom Points ({customPoints.length})</span>
          <button onClick={() => setCustomPoints([])}
            style={{ background: 'none', border: 'none', color: '#F87171', fontSize: 10, cursor: 'pointer', padding: '2px 6px' }}>
            Clear All
          </button>
        </div>
        <div style={{ maxHeight: 100, overflowY: 'auto' }}>
          {customPoints.map((pt, i) => (
            <div key={pt.id} style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '2px 0' }}>
              <span style={{ color: '#38BDF8', fontSize: 10, width: 20 }}>#{i + 1}</span>
              <span style={{ color: '#E8ECF4', fontSize: 10, flex: 1 }}>({Math.round(pt.x)}, {Math.round(pt.y)})</span>
              <button onClick={() => {
                if (i > 0) {
                  const pts = [...customPoints]
                  ;[pts[i], pts[i - 1]] = [pts[i - 1], pts[i]]
                  setCustomPoints(pts)
                }
              }} style={{ ...S.miniBtn, opacity: i > 0 ? 1 : 0.3 }}>↑</button>
              <button onClick={() => {
                if (i < customPoints.length - 1) {
                  const pts = [...customPoints]
                  ;[pts[i], pts[i + 1]] = [pts[i + 1], pts[i]]
                  setCustomPoints(pts)
                }
              }} style={{ ...S.miniBtn, opacity: i < customPoints.length - 1 ? 1 : 0.3 }}>↓</button>
              <button onClick={() => setCustomPoints(prev => prev.filter(p => p.id !== pt.id))}
                style={{ ...S.miniBtn, color: '#F87171' }}>✕</button>
            </div>
          ))}
        </div>
      </div>
    )
  }

  // ===== HOME SCREEN =====
  const renderHome = () => (
    <div style={{ padding: 20, display: 'flex', flexDirection: 'column', height: '100%', boxSizing: 'border-box', overflowY: 'auto' }}>
      <h2 style={{ color: '#E8ECF4', margin: '0 0 16px', fontSize: 22 }}>Auto Clicker</h2>

      {/* Mode selection - 3 cards */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 16 }}>
        {[['1-Point', 'Single tap', '👆', 'single'], ['Multi-Point', 'Sequence', '📱', 'multi'], ['Pattern', 'Shapes', '✨', 'pattern']].map(([t, d, ic, m]) => (
          <div key={m} onClick={() => setMode(m)} style={{
            flex: 1, padding: 14, borderRadius: 16, cursor: 'pointer', textAlign: 'center',
            background: mode === m ? '#0E3A5C' : '#1A2035',
            border: mode === m ? 'none' : '1px solid #2A3250',
            transition: 'all 0.2s'
          }}>
            <div style={{ fontSize: 24, marginBottom: 6, filter: mode === m ? 'none' : 'grayscale(1) opacity(0.5)' }}>{ic}</div>
            <div style={{ color: '#E8ECF4', fontWeight: 600, fontSize: 12 }}>{t}</div>
            <div style={{ color: '#8B95B0', fontSize: 10 }}>{d}</div>
          </div>
        ))}
      </div>

      {/* Pattern type selector + preview + config */}
      {mode === 'pattern' && (
        <div style={{ ...S.card, background: '#1A2035', marginBottom: 12 }}>
          <div style={{ color: '#E8ECF4', fontWeight: 600, fontSize: 13, marginBottom: 10 }}>Pattern Type</div>

          {/* Type buttons */}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 10 }}>
            {['Circle', 'Zigzag', 'Grid', 'Spiral', 'Diamond', 'Random', 'Custom'].map(p => (
              <button key={p} onClick={() => {
                setPatternType(p.toLowerCase())
                if (p.toLowerCase() === 'random') randomSeedRef.current = Date.now()
              }}
                style={{
                  padding: '6px 10px', borderRadius: 12, border: 'none', fontSize: 11, fontWeight: 600, cursor: 'pointer',
                  background: patternType === p.toLowerCase() ? '#38BDF8' : 'rgba(56,189,248,0.15)',
                  color: patternType === p.toLowerCase() ? '#0B0F19' : '#38BDF8'
                }}>{p}</button>
            ))}
          </div>

          {/* SVG Pattern Preview */}
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 8 }}>
            <svg width="180" height="140" style={{ background: 'rgba(0,0,0,0.25)', borderRadius: 10 }}>
              {scaledPreview.length > 0 && (
                <>
                  {/* Connecting lines (execution order) */}
                  {scaledPreview.map((pt, i) => {
                    if (i === 0) return null
                    const prev = scaledPreview[i - 1]
                    return <line key={`l${i}`} x1={prev.x} y1={prev.y} x2={pt.x} y2={pt.y}
                      stroke="rgba(56,189,248,0.3)" strokeWidth="1.5" strokeDasharray="4 3" />
                  })}
                  {/* Point dots */}
                  {scaledPreview.map((pt, i) => (
                    <g key={`p${i}`}>
                      <circle cx={pt.x} cy={pt.y} r="10" fill="rgba(56,189,248,0.15)" />
                      <circle cx={pt.x} cy={pt.y} r="4" fill="#38BDF8" />
                      <text x={pt.x} y={pt.y - 13} textAnchor="middle" fill="#38BDF8" fontSize="8" fontWeight="bold">{i + 1}</text>
                    </g>
                  ))}
                </>
              )}
              {scaledPreview.length === 0 && patternType === 'custom' && (
                <text x="90" y="75" textAnchor="middle" fill="#8B95B0" fontSize="10">Tap START to place points</text>
              )}
            </svg>
          </div>

          {/* Parameter sliders */}
          {patternType !== 'custom' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {renderPatternSliders()}
            </div>
          )}

          {/* Custom points info / list */}
          {patternType === 'custom' && customPoints.length === 0 && (
            <div style={{ color: '#8B95B0', fontSize: 11, textAlign: 'center', padding: '4px 0' }}>
              Press START to place custom points on screen
            </div>
          )}
          {patternType === 'custom' && renderCustomPointsList()}
        </div>
      )}

      {/* Live stats */}
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
        onClick={() => {
          if (running) { stopAll(); }
          else if (mode === 'pattern' && patternType === 'custom') {
            // Custom pattern: enter overlay pick mode
            setScreen('overlay'); setCustomPickMode(true)
          }
          else if (mode === 'pattern') {
            // Preset pattern: generate points and show on overlay
            const pts = generatePatternPoints(patternType, patternConfig)
            const cx = PHONE_W / 2, cy = PHONE_H / 2
            // Scale to fit overlay
            const maxAbsX = Math.max(...pts.map(p => Math.abs(p.x)), 1)
            const maxAbsY = Math.max(...pts.map(p => Math.abs(p.y)), 1)
            const scaleX = 140 / maxAbsX
            const scaleY = 280 / maxAbsY
            const scale = Math.min(scaleX, scaleY, 1.5)
            const overlayTargets = pts.map((p, i) => ({
              x: cx + p.x * scale,
              y: cy + p.y * scale,
              id: Date.now() + i
            }))
            setTargets(overlayTargets)
            startClicking()
            setScreen('overlay')
          }
          else { setScreen('overlay'); setPickMode(true); setTargets([]) }
        }}>
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
      <div style={{ padding: 30, opacity: 0.2 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8, marginTop: 20 }}>
          {[...Array(9)].map((_, i) => <div key={i} style={{ background: '#ffffff11', borderRadius: 10, height: 70 }} />)}
        </div>
      </div>

      {/* Single/Multi pick mode */}
      {pickMode && (
        <>
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

      {/* Custom pattern pick mode */}
      {customPickMode && (
        <>
          <div style={{ position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.3)', display: 'flex', flexDirection: 'column', alignItems: 'center' }}
            onClick={(e) => {
              const rect = e.currentTarget.getBoundingClientRect()
              const x = e.clientX - rect.left, y = e.clientY - rect.top
              setCustomPoints(prev => [...prev, { x, y, id: Date.now() }])
            }}>
            <p style={{ color: 'rgba(255,255,255,0.8)', marginTop: 60, fontSize: 13 }}>
              Tap to place custom pattern points ({customPoints.length} placed)
            </p>
          </div>

          {/* Custom point markers */}
          {customPoints.map((pt, i) => (
            <div key={pt.id} style={{ position: 'absolute', left: pt.x - 16, top: pt.y - 16, width: 32, height: 32, zIndex: 40, pointerEvents: 'none' }}>
              <svg width="32" height="32">
                <circle cx="16" cy="16" r="14" fill="rgba(56,189,248,0.15)" stroke="#38BDF8" strokeWidth="2" />
                <circle cx="16" cy="16" r="6" fill="rgba(56,189,248,0.5)" />
                <text x="16" y="20" textAnchor="middle" fill="#fff" fontSize="10" fontWeight="bold">{i + 1}</text>
              </svg>
            </div>
          ))}

          {/* Connecting lines between custom points */}
          <svg style={{ position: 'absolute', inset: 0, zIndex: 35, pointerEvents: 'none' }} width="100%" height="100%">
            {customPoints.map((pt, i) => {
              if (i === 0) return null
              const prev = customPoints[i - 1]
              return <line key={i} x1={prev.x} y1={prev.y} x2={pt.x} y2={pt.y}
                stroke="rgba(56,189,248,0.4)" strokeWidth="2" strokeDasharray="6 4" />
            })}
          </svg>

          {customPoints.length > 0 && (
            <button style={{ position: 'absolute', bottom: 80, right: 30, background: '#38BDF8', color: '#fff', border: 'none', borderRadius: 16, padding: '12px 28px', fontWeight: 'bold', fontSize: 14, cursor: 'pointer', zIndex: 50 }}
              onClick={() => {
                setCustomPickMode(false)
                const overlayTargets = customPoints.map((p, i) => ({ x: p.x, y: p.y, id: p.id }))
                setTargets(overlayTargets)
                startClicking()
              }}>DONE ({customPoints.length})</button>
          )}

          {/* Back button */}
          <button style={{ position: 'absolute', bottom: 80, left: 30, background: 'rgba(248,113,113,0.9)', color: '#fff', border: 'none', borderRadius: 16, padding: '12px 20px', fontWeight: 'bold', fontSize: 12, cursor: 'pointer', zIndex: 50 }}
            onClick={() => { setCustomPickMode(false); setScreen('main') }}>Cancel</button>
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

      {/* Connecting lines between targets on overlay */}
      {targets.length > 1 && !pickMode && !customPickMode && (
        <svg style={{ position: 'absolute', inset: 0, zIndex: 35, pointerEvents: 'none' }} width="100%" height="100%">
          {targets.map((t, i) => {
            if (i === 0) return null
            const prev = targets[i - 1]
            return <line key={i} x1={prev.x} y1={prev.y} x2={t.x} y2={t.y}
              stroke="rgba(56,189,248,0.25)" strokeWidth="1.5" strokeDasharray="6 4" />
          })}
        </svg>
      )}

      {/* Floating toolbar */}
      {!pickMode && !customPickMode && (
        <div style={{ position: 'absolute', left: floatingPos.x, top: floatingPos.y, width: 130, background: 'rgba(30,35,50,0.92)', borderRadius: 20, padding: 10, zIndex: 100, backdropFilter: 'blur(10px)' }}
          onMouseDown={(e) => handleMouseDown(e, 'float', null)}>
          <div style={{ color: '#8B95B0', fontSize: 11, textAlign: 'center' }}>{clickCount} taps</div>
          <div style={{ color: '#8B95B0', fontSize: 11, textAlign: 'center', marginBottom: 8 }}>{formatTime(elapsed)}</div>
          <div style={{ display: 'flex', gap: 6, justifyContent: 'center' }}>
            {!paused ? (
              <button style={{ ...S.circleBtn, background: '#34D399' }} onClick={(e) => { e.stopPropagation(); setPaused(true); clearInterval(timerRef.current); clearInterval(elapsedRef.current) }}>⏸</button>
            ) : (
              <button style={{ ...S.circleBtn, background: '#38BDF8' }} onClick={(e) => { e.stopPropagation(); setPaused(false) }}>▶</button>
            )}
            <button style={{ ...S.circleBtn, background: '#F87171' }} onClick={(e) => { e.stopPropagation(); stopAll(); setScreen('main') }}>⏹</button>
          </div>
          <div style={{ color: '#8B95B0', fontSize: 10, textAlign: 'center', marginTop: 6 }}>Loop {loop} • Step 1/{targets.length || '?'}</div>
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
  const speedPresets = [
    { label: 'Turbo', ms: 10 }, { label: 'Fast', ms: 100 }, { label: 'Normal', ms: 500 },
    { label: 'Slow', ms: 2000 }, { label: 'Crawl', ms: 30000 }, { label: 'Hourly', ms: 3600000 }
  ]
  const fmtMs = (ms) => ms < 1000 ? `${ms}ms` : ms < 60000 ? `${ms/1000}s` : ms < 3600000 ? `${ms/60000}min` : `${ms/3600000}hr`

  const renderSettings = () => (
    <div style={{ padding: 16, height: '100%', overflowY: 'auto', boxSizing: 'border-box' }}>
      <h2 style={{ color: '#E8ECF4', margin: '0 0 4px', fontSize: 20 }}>Settings</h2>
      <div style={{ color: '#38BDF8', fontSize: 11, marginBottom: 12 }}>Applied to new scripts</div>

      {/* Speed Presets */}
      <div style={{ color: '#8B95B0', fontSize: 10, fontWeight: 700, letterSpacing: 1, marginBottom: 8 }}>SPEED PRESETS</div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 6, marginBottom: 12 }}>
        {speedPresets.map(p => (
          <button key={p.label} onClick={() => setSettings(s => ({ ...s, interval: p.ms }))}
            style={{
              padding: '8px 4px', borderRadius: 10, cursor: 'pointer', textAlign: 'center',
              border: settings.interval === p.ms ? '1px solid #38BDF8' : '1px solid #2A3250',
              background: settings.interval === p.ms ? '#0E3A5C' : '#1A2035',
              color: settings.interval === p.ms ? '#38BDF8' : '#E8ECF4'
            }}>
            <div style={{ fontWeight: 600, fontSize: 12 }}>{p.label}</div>
            <div style={{ fontSize: 10, opacity: 0.7 }}>{fmtMs(p.ms)}</div>
          </button>
        ))}
      </div>

      {/* Main settings */}
      <div style={{ ...S.card, background: '#1A2035', marginBottom: 12 }}>
        {/* Speed mode toggle */}
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 14 }}>
          <span style={{ color: '#8B95B0', fontSize: 13, marginRight: 4 }}>⚡</span>
          <span style={{ color: '#E8ECF4', fontWeight: 600, fontSize: 13, flex: 1 }}>Speed Mode</span>
          <div style={{ display: 'flex' }}>
            {['interval', 'rate'].map(m => (
              <button key={m} onClick={() => setSettings(s => ({ ...s, speedMode: m }))}
                style={{
                  padding: '4px 12px', border: 'none', fontSize: 11, fontWeight: 600, cursor: 'pointer',
                  background: settings.speedMode === m ? '#38BDF8' : 'rgba(56,189,248,0.15)',
                  color: settings.speedMode === m ? '#0B0F19' : '#38BDF8',
                  borderRadius: m === 'interval' ? '8px 0 0 8px' : '0 8px 8px 0'
                }}>{m.charAt(0).toUpperCase() + m.slice(1)}</button>
            ))}
          </div>
        </div>

        {[['⏱', 'Interval', 'interval', 'ms'], ['👆', 'Tap & Hold Duration', 'hold', 'ms'], ['👉', 'Swipe Duration', 'swipe', 'ms']].map(([icon, label, key, unit]) => (
          <div key={key} style={{ display: 'flex', alignItems: 'center', marginBottom: 12 }}>
            <span style={{ color: '#8B95B0', fontSize: 13, marginRight: 8 }}>{icon}</span>
            <span style={{ color: '#E8ECF4', fontSize: 12, fontWeight: 500, flex: 1 }}>{label}</span>
            <input style={{ ...S.input, width: 80, flex: 'none', textAlign: 'right' }} type="number" value={settings[key]} onChange={e => setSettings(s => ({ ...s, [key]: +e.target.value }))} />
            <span style={{ background: '#0E3A5C', color: '#38BDF8', padding: '3px 8px', borderRadius: 6, fontSize: 10, marginLeft: 6 }}>{unit}</span>
          </div>
        ))}

        {/* Stop condition */}
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <span style={{ color: '#8B95B0', fontSize: 13, marginRight: 8 }}>⏹</span>
          <span style={{ color: '#E8ECF4', fontSize: 12, fontWeight: 500, flex: 1 }}>Stop After</span>
          <select style={{ ...S.input, width: 120, flex: 'none' }}
            value={settings.stopCondition}
            onChange={e => setSettings(s => ({ ...s, stopCondition: e.target.value }))}>
            <option value="never">Never Stop</option>
            <option value="taps">After Taps</option>
            <option value="time">After Time</option>
            <option value="loops">After Loops</option>
          </select>
        </div>
      </div>

      {/* Tap Randomization */}
      <div style={{ color: '#8B95B0', fontSize: 10, fontWeight: 700, letterSpacing: 1, marginBottom: 8 }}>TAP RANDOMIZATION</div>
      <div style={{ ...S.card, background: '#1A2035', marginBottom: 12 }}>
        {[
          { icon: '🎯', label: 'Random Position Offset', desc: 'Slightly shifts each tap to avoid detection', key: 'randomPositionOffset' },
          { icon: '🔀', label: 'Interval Jitter', desc: 'Varies timing between taps', key: 'intervalJitter' },
          { icon: '🤲', label: 'Humanize Hold Duration', desc: 'Varies how long each tap is held', key: 'humanizeHold' },
          { icon: '🚫', label: 'Avoid Exact Repetition', desc: 'No two taps hit the same pixel', key: 'avoidRepetition' },
          { icon: '☕', label: 'Random Micro-Pauses', desc: 'Simulates human distraction', key: 'microPauses' }
        ].map((item, i) => (
          <div key={item.key}>
            <div style={{ display: 'flex', alignItems: 'center', padding: '6px 0' }}>
              <span style={{ fontSize: 14, marginRight: 8 }}>{item.icon}</span>
              <div style={{ flex: 1 }}>
                <div style={{ color: '#E8ECF4', fontSize: 12, fontWeight: 500 }}>{item.label}</div>
                <div style={{ color: '#8B95B0', fontSize: 10 }}>{item.desc}</div>
              </div>
              <div onClick={() => setSettings(s => ({
                ...s,
                antiDetection: { ...s.antiDetection, [item.key]: !s.antiDetection[item.key] }
              }))}
                style={{
                  width: 40, height: 22, borderRadius: 11, cursor: 'pointer', position: 'relative', transition: 'background 0.2s',
                  background: settings.antiDetection[item.key] ? '#38BDF8' : '#2A3250'
                }}>
                <div style={{
                  width: 18, height: 18, borderRadius: '50%', background: '#fff', position: 'absolute', top: 2, transition: 'left 0.2s',
                  left: settings.antiDetection[item.key] ? 20 : 2
                }} />
              </div>
            </div>
            {/* Slider for offset radius */}
            {item.key === 'randomPositionOffset' && settings.antiDetection.randomPositionOffset && (
              <div style={{ display: 'flex', alignItems: 'center', padding: '2px 0 6px 26px' }}>
                <span style={{ color: '#8B95B0', fontSize: 10, width: 70 }}>Offset radius</span>
                <input type="range" min="1" max="50" value={settings.antiDetection.positionOffsetRadius}
                  onChange={e => setSettings(s => ({ ...s, antiDetection: { ...s.antiDetection, positionOffsetRadius: +e.target.value } }))}
                  style={{ flex: 1, accentColor: '#38BDF8' }} />
                <span style={{ color: '#38BDF8', fontSize: 10, width: 36, textAlign: 'right' }}>{settings.antiDetection.positionOffsetRadius}px</span>
              </div>
            )}
            {/* Slider for jitter percent */}
            {item.key === 'intervalJitter' && settings.antiDetection.intervalJitter && (
              <div style={{ display: 'flex', alignItems: 'center', padding: '2px 0 6px 26px' }}>
                <span style={{ color: '#8B95B0', fontSize: 10, width: 70 }}>Jitter amount</span>
                <input type="range" min="5" max="50" value={settings.antiDetection.jitterPercent}
                  onChange={e => setSettings(s => ({ ...s, antiDetection: { ...s.antiDetection, jitterPercent: +e.target.value } }))}
                  style={{ flex: 1, accentColor: '#38BDF8' }} />
                <span style={{ color: '#38BDF8', fontSize: 10, width: 28, textAlign: 'right' }}>{settings.antiDetection.jitterPercent}%</span>
              </div>
            )}
            {i < 4 && <div style={{ height: 1, background: '#2A325040', margin: '4px 0' }} />}
          </div>
        ))}
      </div>

      {/* Accessibility card */}
      <div style={{ ...S.card, background: accessibilityEnabled ? '#1A2035' : '#3D1515', marginBottom: 10, display: 'flex', alignItems: 'center', gap: 12 }}>
        <span style={{ fontSize: 18 }}>{accessibilityEnabled ? '♿' : '⚠'}</span>
        <div style={{ flex: 1 }}>
          <div style={{ color: '#E8ECF4', fontWeight: 600, fontSize: 12 }}>Accessibility Service</div>
          <div style={{ color: accessibilityEnabled ? '#34D399' : '#F87171', fontSize: 10 }}>{accessibilityEnabled ? 'Enabled' : 'Disabled'}</div>
        </div>
        <button style={S.tonalBtn} onClick={() => setAccessibilityEnabled(true)}>Settings</button>
      </div>

      {/* Battery card */}
      <div style={{ ...S.card, background: '#1A2035', display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <span style={{ fontSize: 18 }}>🔋</span>
        <div style={{ flex: 1 }}>
          <div style={{ color: '#E8ECF4', fontWeight: 600, fontSize: 12 }}>Battery Optimization</div>
          <div style={{ color: '#8B95B0', fontSize: 10 }}>Disable to prevent stopping</div>
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
        {screen === 'onboarding' ? 'Onboarding' : screen === 'overlay' ? (customPickMode ? 'Custom Pattern — tap to place points' : 'Overlay Mode — drag targets & toolbar') : ['Clicker', 'Scripts', 'Settings'][tab]}
      </p>

      <div style={{ width: PHONE_W, height: PHONE_H, borderRadius: 44, border: '3px solid #2A3250', overflow: 'hidden', background: '#0B0F19', position: 'relative', boxShadow: '0 24px 80px rgba(0,0,0,0.6)' }}>
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
  miniBtn: { background: 'none', border: 'none', color: '#8B95B0', fontSize: 12, cursor: 'pointer', padding: '1px 4px', lineHeight: 1 },
}
