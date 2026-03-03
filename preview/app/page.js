'use client'

import { useState, useRef, useEffect, useCallback } from 'react'

const PHONE_WIDTH = 380
const PHONE_HEIGHT = 780
const HEADER_HEIGHT = 24

export default function Home() {
  const [screen, setScreen] = useState('main')
  const [mode, setMode] = useState('single')
  const [interval, setInterval] = useState(300)
  const [repeatCount, setRepeatCount] = useState('')
  const [running, setRunning] = useState(false)
  const [paused, setPaused] = useState(false)
  const [clickCount, setClickCount] = useState(0)
  const [targets, setTargets] = useState([])
  const [dragging, setDragging] = useState(null)
  const [floatingPos, setFloatingPos] = useState({ x: 10, y: 100 })
  const [floatingExpanded, setFloatingExpanded] = useState(true)
  const [accessibilityEnabled, setAccessibilityEnabled] = useState(false)
  const [overlayEnabled, setOverlayEnabled] = useState(false)
  const timerRef = useRef(null)
  const phoneRef = useRef(null)

  const stopClicking = useCallback(() => {
    setRunning(false)
    setPaused(false)
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = null
  }, [])

  const startClicking = useCallback(() => {
    if (targets.length === 0) {
      setTargets([{ x: PHONE_WIDTH / 2, y: PHONE_HEIGHT / 2, id: 1 }])
    }
    setRunning(true)
    setPaused(false)
    setClickCount(0)
    const maxClicks = repeatCount ? parseInt(repeatCount) : Infinity
    let count = 0
    timerRef.current = window.setInterval(() => {
      count++
      setClickCount(count)
      if (count >= maxClicks) {
        stopClicking()
      }
    }, interval)
  }, [interval, repeatCount, targets.length, stopClicking])

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [])

  const addTarget = () => {
    const id = targets.length + 1
    setTargets([...targets, {
      x: 100 + Math.random() * 180,
      y: 200 + Math.random() * 300,
      id
    }])
  }

  const removeTarget = (id) => {
    setTargets(targets.filter(t => t.id !== id))
  }

  const handleMouseDown = (e, type, id) => {
    e.preventDefault()
    setDragging({ type, id, startX: e.clientX, startY: e.clientY })
  }

  const handleMouseMove = useCallback((e) => {
    if (!dragging) return
    const dx = e.clientX - dragging.startX
    const dy = e.clientY - dragging.startY

    if (dragging.type === 'target') {
      setTargets(prev => prev.map(t =>
        t.id === dragging.id ? { ...t, x: t.x + dx, y: t.y + dy } : t
      ))
    } else if (dragging.type === 'floating') {
      setFloatingPos(prev => ({ x: prev.x + dx, y: prev.y + dy }))
    }
    setDragging(prev => prev ? { ...prev, startX: e.clientX, startY: e.clientY } : null)
  }, [dragging])

  const handleMouseUp = useCallback(() => {
    setDragging(null)
  }, [])

  useEffect(() => {
    if (dragging) {
      window.addEventListener('mousemove', handleMouseMove)
      window.addEventListener('mouseup', handleMouseUp)
      return () => {
        window.removeEventListener('mousemove', handleMouseMove)
        window.removeEventListener('mouseup', handleMouseUp)
      }
    }
  }, [dragging, handleMouseMove, handleMouseUp])

  const renderMainScreen = () => (
    <div style={{ padding: 20, overflowY: 'auto', height: PHONE_HEIGHT - HEADER_HEIGHT - 20 }}>
      <h1 style={{ fontSize: 26, fontWeight: 'bold', color: '#fff', margin: '0 0 4px 0' }}>Auto Clicker</h1>
      <p style={{ fontSize: 13, color: '#b0b0c0', margin: '0 0 20px 0' }}>Automatic Tap</p>

      {/* Permissions Card */}
      <div style={styles.card}>
        <h3 style={styles.cardTitle}>Permissions</h3>
        <div style={styles.row}>
          <span style={styles.label}>Accessibility Service</span>
          <span style={{ ...styles.status, color: accessibilityEnabled ? '#4CAF50' : '#F44336' }}>
            {accessibilityEnabled ? 'Enabled' : 'Disabled'}
          </span>
        </div>
        <div style={styles.row}>
          <span style={styles.label}>Overlay Permission</span>
          <span style={{ ...styles.status, color: overlayEnabled ? '#4CAF50' : '#F44336' }}>
            {overlayEnabled ? 'Enabled' : 'Disabled'}
          </span>
        </div>
        <button
          style={styles.tonalButton}
          onClick={() => { setAccessibilityEnabled(true); setOverlayEnabled(true) }}>
          Enable All Permissions
        </button>
      </div>

      {/* Configuration Card */}
      <div style={styles.card}>
        <h3 style={styles.cardTitle}>Configuration</h3>
        <p style={{ fontSize: 13, color: '#b0b0c0', margin: '0 0 8px 0' }}>Click Mode</p>
        <div style={styles.toggleGroup}>
          <button
            style={{ ...styles.toggleBtn, ...(mode === 'single' ? styles.toggleActive : {}) }}
            onClick={() => setMode('single')}>
            Single
          </button>
          <button
            style={{ ...styles.toggleBtn, ...(mode === 'multi' ? styles.toggleActive : {}) }}
            onClick={() => setMode('multi')}>
            Multi
          </button>
        </div>
        <p style={{ fontSize: 11, color: '#666680', margin: '4px 0 14px 0' }}>
          {mode === 'single' ? 'Tap one point repeatedly' : 'Set multiple tap points in sequence'}
        </p>

        <div style={styles.inputGroup}>
          <label style={styles.inputLabel}>Tap Interval (ms)</label>
          <input
            type="number"
            value={interval}
            onChange={(e) => setInterval(Number(e.target.value) || 300)}
            style={styles.input}
          />
        </div>

        <div style={styles.inputGroup}>
          <label style={styles.inputLabel}>Repeat Count (empty = infinite)</label>
          <input
            type="number"
            value={repeatCount}
            onChange={(e) => setRepeatCount(e.target.value)}
            style={styles.input}
            placeholder="∞"
          />
        </div>
      </div>

      {/* Start Button */}
      <button
        style={{
          ...styles.primaryButton,
          opacity: (accessibilityEnabled && overlayEnabled) ? 1 : 0.5,
        }}
        disabled={!accessibilityEnabled || !overlayEnabled}
        onClick={() => { setScreen('overlay'); startClicking() }}>
        Start Auto Clicker
      </button>

      <button style={styles.outlinedButton} onClick={stopClicking}>
        Stop Service
      </button>

      {/* Scripts Card */}
      <div style={styles.card}>
        <h3 style={styles.cardTitle}>Scripts</h3>
        <div style={{ display: 'flex', gap: 6 }}>
          <button style={{ ...styles.tonalButton, flex: 1 }}>Save</button>
          <button style={{ ...styles.tonalButton, flex: 1 }}>Export</button>
          <button style={{ ...styles.tonalButton, flex: 1 }}>Import</button>
        </div>
      </div>
    </div>
  )

  const renderOverlayScreen = () => (
    <div style={{
      position: 'relative',
      width: '100%',
      height: PHONE_HEIGHT - HEADER_HEIGHT,
      background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
      overflow: 'hidden',
    }}>
      {/* Simulated app behind overlay */}
      <div style={{ padding: 40, textAlign: 'center', opacity: 0.3 }}>
        <p style={{ color: '#fff', fontSize: 14 }}>Other app running underneath...</p>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10, marginTop: 30 }}>
          {[...Array(9)].map((_, i) => (
            <div key={i} style={{
              background: '#ffffff11',
              borderRadius: 12,
              height: 80,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#ffffff44',
              fontSize: 12,
            }}>Item {i + 1}</div>
          ))}
        </div>
      </div>

      {/* Click targets */}
      {targets.map((target, idx) => (
        <div
          key={target.id}
          style={{
            position: 'absolute',
            left: target.x - 30,
            top: target.y - 30,
            width: 60,
            height: 60,
            cursor: 'grab',
          }}
          onMouseDown={(e) => handleMouseDown(e, 'target', target.id)}
        >
          {/* Crosshair */}
          <svg width="60" height="60" viewBox="0 0 60 60">
            <circle cx="30" cy="30" r="28" fill="rgba(255,87,34,0.15)" stroke="#FF5722" strokeWidth="2" />
            <line x1="0" y1="30" x2="60" y2="30" stroke="#FF5722" strokeWidth="1.5" />
            <line x1="30" y1="0" x2="30" y2="60" stroke="#FF5722" strokeWidth="1.5" />
            <circle cx="30" cy="30" r="12" fill="#FF5722" />
            <text x="30" y="35" textAnchor="middle" fill="#fff" fontSize="12" fontWeight="bold">{idx + 1}</text>
          </svg>
          {/* Pulse animation when running */}
          {running && !paused && (
            <div style={{
              position: 'absolute',
              top: 0, left: 0, width: 60, height: 60,
              borderRadius: '50%',
              border: '2px solid #FF5722',
              animation: 'pulse 0.5s ease-out infinite',
            }} />
          )}
          {/* Remove button */}
          <div
            style={{
              position: 'absolute', top: -5, right: -5,
              width: 18, height: 18, borderRadius: '50%',
              background: '#F44336', display: 'flex',
              alignItems: 'center', justifyContent: 'center',
              cursor: 'pointer', fontSize: 10, color: '#fff',
            }}
            onClick={(e) => { e.stopPropagation(); removeTarget(target.id) }}
          >x</div>
        </div>
      ))}

      {/* Floating control panel */}
      <div
        style={{
          position: 'absolute',
          left: floatingPos.x,
          top: floatingPos.y,
          background: 'rgba(30,30,46,0.95)',
          borderRadius: 20,
          border: '1px solid rgba(255,255,255,0.2)',
          padding: 6,
          zIndex: 100,
          backdropFilter: 'blur(10px)',
        }}
      >
        {/* Drag handle / toggle */}
        <div
          style={{
            width: 44, height: 44, borderRadius: '50%',
            background: 'rgba(255,255,255,0.25)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            cursor: 'grab', margin: '0 auto 4px',
          }}
          onMouseDown={(e) => handleMouseDown(e, 'floating', null)}
          onClick={() => setFloatingExpanded(!floatingExpanded)}
        >
          <span style={{ color: '#fff', fontSize: 18 }}>{floatingExpanded ? '▼' : '▲'}</span>
        </div>

        {floatingExpanded && (
          <div style={{ padding: '4px 6px' }}>
            <p style={{
              color: '#fff', fontSize: 11, textAlign: 'center',
              margin: '0 0 6px 0', fontVariantNumeric: 'tabular-nums',
            }}>
              {clickCount} clicks
            </p>
            <div style={{ display: 'flex', gap: 6 }}>
              {!running || paused ? (
                <button onClick={() => { if (!running) startClicking(); else { setPaused(false) } }}
                  style={{ ...styles.circleBtn, background: '#4CAF50' }}>▶</button>
              ) : (
                <button onClick={() => { setPaused(true); if(timerRef.current) clearInterval(timerRef.current) }}
                  style={styles.circleBtn}>⏸</button>
              )}
              <button onClick={() => { stopClicking(); setClickCount(0) }}
                style={{ ...styles.circleBtn, background: '#F44336' }}>⏹</button>
              <button onClick={addTarget} style={styles.circleBtn}>+</button>
              <button onClick={() => { stopClicking(); setScreen('main'); setClickCount(0) }}
                style={styles.circleBtn}>✕</button>
            </div>
          </div>
        )}
      </div>

      <style>{`
        @keyframes pulse {
          0% { transform: scale(1); opacity: 1; }
          100% { transform: scale(1.8); opacity: 0; }
        }
      `}</style>
    </div>
  )

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      background: '#0a0a0f',
      padding: 20,
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    }}>
      <h1 style={{ color: '#fff', fontSize: 20, marginBottom: 4 }}>Auto Clicker - App Preview</h1>
      <p style={{ color: '#888', fontSize: 13, marginBottom: 16 }}>
        Interactive preview &bull; {screen === 'main' ? 'Main Screen' : 'Overlay Mode'}
        {screen === 'overlay' && ' (drag targets & floating panel)'}
      </p>

      {/* Phone frame */}
      <div ref={phoneRef} style={{
        width: PHONE_WIDTH,
        height: PHONE_HEIGHT,
        borderRadius: 40,
        border: '3px solid #333',
        overflow: 'hidden',
        background: '#121218',
        position: 'relative',
        boxShadow: '0 20px 60px rgba(0,0,0,0.5)',
      }}>
        {/* Status bar */}
        <div style={{
          height: HEADER_HEIGHT,
          background: '#121218',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 20px',
          fontSize: 11,
          color: '#888',
        }}>
          <span>12:00</span>
          <span>⚡ 85%</span>
        </div>

        {screen === 'main' ? renderMainScreen() : renderOverlayScreen()}
      </div>

      <p style={{ color: '#555', fontSize: 11, marginTop: 12 }}>
        Built with Claude Code
      </p>
    </div>
  )
}

const styles = {
  card: {
    background: '#1E1E2E',
    borderRadius: 16,
    padding: 16,
    marginBottom: 14,
  },
  cardTitle: {
    color: '#fff',
    fontSize: 15,
    fontWeight: 'bold',
    margin: '0 0 10px 0',
  },
  row: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  label: {
    color: '#fff',
    fontSize: 13,
  },
  status: {
    fontSize: 13,
    fontWeight: 'bold',
  },
  tonalButton: {
    width: '100%',
    padding: '10px 16px',
    borderRadius: 12,
    border: 'none',
    background: 'rgba(108,99,255,0.2)',
    color: '#8B85FF',
    fontSize: 13,
    fontWeight: 600,
    cursor: 'pointer',
    marginTop: 4,
  },
  toggleGroup: {
    display: 'flex',
    gap: 0,
    borderRadius: 8,
    overflow: 'hidden',
    border: '1px solid #6C63FF44',
  },
  toggleBtn: {
    flex: 1,
    padding: '8px 16px',
    border: 'none',
    background: 'transparent',
    color: '#b0b0c0',
    fontSize: 13,
    cursor: 'pointer',
    fontWeight: 500,
  },
  toggleActive: {
    background: '#6C63FF',
    color: '#fff',
  },
  inputGroup: {
    marginBottom: 12,
  },
  inputLabel: {
    display: 'block',
    color: '#b0b0c0',
    fontSize: 12,
    marginBottom: 4,
  },
  input: {
    width: '100%',
    padding: '10px 12px',
    borderRadius: 10,
    border: '1px solid #333',
    background: '#2A2A3E',
    color: '#fff',
    fontSize: 14,
    outline: 'none',
    boxSizing: 'border-box',
  },
  primaryButton: {
    width: '100%',
    padding: '14px',
    borderRadius: 16,
    border: 'none',
    background: '#6C63FF',
    color: '#fff',
    fontSize: 15,
    fontWeight: 'bold',
    cursor: 'pointer',
    marginBottom: 8,
  },
  outlinedButton: {
    width: '100%',
    padding: '12px',
    borderRadius: 16,
    border: '1px solid #6C63FF',
    background: 'transparent',
    color: '#6C63FF',
    fontSize: 13,
    fontWeight: 600,
    cursor: 'pointer',
    marginBottom: 14,
  },
  circleBtn: {
    width: 36,
    height: 36,
    borderRadius: '50%',
    border: 'none',
    background: 'rgba(255,255,255,0.25)',
    color: '#fff',
    fontSize: 14,
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
}
