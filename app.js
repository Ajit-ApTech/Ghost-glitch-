// ================================================================
// Notch Buddy — Glitch Ghost Companion  (60 FPS, Canvas 2D)
// ================================================================
(function () {
  'use strict';

  // ── State ──────────────────────────────────────────────────────
  const state = {
    cpu: 8, targetCpu: 8,
    downSpeed: 50,   // KB/s
    upSpeed:   12,   // KB/s
    targetDown: 50, targetUp: 12,
    currentTier: 'idle',
    hudExpanded: false,
    sessionBytes: 1420 * 1024 * 1024,
    cursorX: window.innerWidth  / 2,
    cursorY: window.innerHeight / 2,
    isPettedUntil: 0,
    blinkProgress: 0,
    lastBlinkTime: 0,
    sparkline: new Array(20).fill(2)
  };

  // ── DOM ────────────────────────────────────────────────────────
  const el = {
    notchContainer: document.getElementById('notchContainer'),
    notchBar:       document.getElementById('notchBar'),
    downVal:        document.getElementById('downVal'),
    downUnit:       document.getElementById('downUnit'),
    upVal:          document.getElementById('upVal'),
    upUnit:         document.getElementById('upUnit'),
    cpuVal:         document.getElementById('cpuVal'),
    pingDot:        document.getElementById('pingDot'),
    sparkCanvas:    document.getElementById('sparklineCanvas'),
    ghostBay:       document.getElementById('ghostBay'),
    ghostCanvas:    document.getElementById('ghostCanvas'),
    heartEmitter:   document.getElementById('heartEmitter'),
    hudMiniCanvas:  document.getElementById('hudMiniCanvas'),
    hudStatusDesc:  document.getElementById('hudStatusDesc'),
    hudDownNum:     document.getElementById('hudDownNum'),
    hudDownUnit:    document.getElementById('hudDownUnit'),
    hudUpNum:       document.getElementById('hudUpNum'),
    hudUpUnit:      document.getElementById('hudUpUnit'),
    hudPingText:    document.getElementById('hudPingText'),
    hudSessionBytes:document.getElementById('hudSessionBytes'),
    ipChip:         document.getElementById('ipChip'),
    hudIp:          document.getElementById('hudIp'),
    btnQuickTest:   document.getElementById('btnQuickTest'),
    btnPetTreat:    document.getElementById('btnPetTreat'),
    perfCards:      document.querySelectorAll('.perf-card'),
    cardCanvases:   document.querySelectorAll('.card-canvas'),
    sliderDown:     document.getElementById('sliderDown'),
    sliderDownTxt:  document.getElementById('sliderDownTxt'),
    sliderCpu:      document.getElementById('sliderCpu'),
    sliderCpuTxt:   document.getElementById('sliderCpuTxt'),
    // Settings elements
    btnToggleSettings: document.getElementById('btnToggleSettings'),
    hudSettingsPanel:  document.getElementById('hudSettingsPanel'),
    btnCloseSettings:  document.getElementById('btnCloseSettings'),
    cfgHappy:          document.getElementById('cfgHappy'),
    cfgFocus:          document.getElementById('cfgFocus'),
    cfgStrain:         document.getElementById('cfgStrain'),
    cfgPanic:          document.getElementById('cfgPanic'),
    cfgDead:           document.getElementById('cfgDead'),
    btnResetLimits:    document.getElementById('btnResetLimits'),
    btnSaveLimits:     document.getElementById('btnSaveLimits'),
    stHappy:           document.getElementById('stHappy'),
    stFocus:           document.getElementById('stFocus'),
    stStrain:          document.getElementById('stStrain'),
    stPanic:           document.getElementById('stPanic'),
    stDead:            document.getElementById('stDead'),
    btnStReset:        document.getElementById('btnStReset'),
    btnStApply:        document.getElementById('btnStApply'),
    // Essential features elements
    cfgRefresh:        document.getElementById('cfgRefresh'),
    cfgUnitMode:       document.getElementById('cfgUnitMode'),
    btnToggleUnit:     document.getElementById('btnToggleUnit'),
    btnUnitLabel:      document.getElementById('btnUnitLabel'),
    appUsageChrome:    document.getElementById('appUsageChrome'),
    appUsageSteam:     document.getElementById('appUsageSteam'),
    appUsageSlack:     document.getElementById('appUsageSlack'),
    quotaText:         document.getElementById('quotaText'),
    quotaFill:         document.getElementById('quotaFill')
  };

  const ghostCtx   = el.ghostCanvas.getContext('2d');
  const hudMiniCtx = el.hudMiniCanvas.getContext('2d');
  const sparkCtx   = el.sparkCanvas.getContext('2d');

  // State extensions
  state.unitMode         = localStorage.getItem('notch_buddy_unit') || 'bytes';
  state.refreshInterval  = parseInt(localStorage.getItem('notch_buddy_refresh') || '1000', 10);
  state.baseMonthlyBytes = 48.2 * 1024 * 1024 * 1024; // 48.2 GB base usage
  state.quotaLimitBytes  = 100 * 1024 * 1024 * 1024;  // 100 GB limit

  window.addEventListener('mousemove', e => {
    state.cursorX = e.clientX;
    state.cursorY = e.clientY;
  });

  // ── Speed formatting: Bytes (KB/s, MB/s) vs Bits (Kbps, Mbps) ──
  function formatSpeed(kbps) {
    if (state.unitMode === 'bits') {
      const kbpsBits = kbps * 8;
      if (kbpsBits < 1)              return { val: '0',                             unit: 'Kbps' };
      if (kbpsBits >= 1024 * 1024)   return { val: (kbpsBits / (1024 * 1024)).toFixed(2), unit: 'Gbps' };
      if (kbpsBits >= 1024)          return { val: (kbpsBits / 1024).toFixed(1),        unit: 'Mbps' };
      return                                { val: Math.round(kbpsBits).toString(),      unit: 'Kbps' };
    } else {
      if (kbps < 1)              return { val: '0',                             unit: 'KB/s' };
      if (kbps >= 1024 * 1024)   return { val: (kbps / (1024 * 1024)).toFixed(2), unit: 'GB/s' };
      if (kbps >= 1024)          return { val: (kbps / 1024).toFixed(1),        unit: 'MB/s' };
      return                            { val: Math.round(kbps).toString(),      unit: 'KB/s' };
    }
  }

  function formatBytes(bytes) {
    const mb = bytes / (1024 * 1024);
    return mb >= 1024 ? (mb / 1024).toFixed(2) + ' GB' : mb.toFixed(0) + ' MB';
  }


  // ── Dynamic Custom Thresholds ──────────────────────────────────
  const DEFAULT_THRESHOLDS = {
    happy:  10,        // 10 KB/s
    focus:  500,       // 500 KB/s
    strain: 1024,      // 1 MB/s
    panic:  5 * 1024,  // 5 MB/s
    dead:   10 * 1024  // 10 MB/s
  };

  function loadThresholds() {
    try {
      const saved = localStorage.getItem('glitch_ghost_thresholds');
      if (saved) return { ...DEFAULT_THRESHOLDS, ...JSON.parse(saved) };
    } catch(e) {}
    return { ...DEFAULT_THRESHOLDS };
  }

  state.thresholds = loadThresholds();

  function calcTier() {
    const spd = Math.max(state.downSpeed, state.upSpeed);
    const th = state.thresholds;
    if (spd >= th.dead)   return 'peak';     // Dead
    if (spd >= th.panic)  return 'critical'; // Panic
    if (spd >= th.strain) return 'high';     // Strain
    if (spd >= th.focus)  return 'moderate'; // Focus
    if (spd >= th.happy)  return 'low';      // Happy
    return 'idle';                           // Sleep
  }

  function getTierDesc(tier) {
    const th = state.thresholds;
    const fmt = kb => kb >= 1024 ? (kb / 1024).toFixed(kb % 1024 === 0 ? 0 : 1) + ' MB/s' : Math.round(kb) + ' KB/s';
    switch (tier) {
      case 'peak':     return `> ${fmt(th.dead)} · Overload! Maximum throughput dead`;
      case 'critical': return `${fmt(th.panic)}–${fmt(th.dead)} · PANIC! Red alert jittering`;
      case 'high':     return `${fmt(th.strain)}–${fmt(th.panic)} · Straining under network load!`;
      case 'moderate': return `${fmt(th.focus)}–${fmt(th.strain)} · Focused & locked on target`;
      case 'low':      return `${fmt(th.happy)}–${fmt(th.focus)} · Happy & curious (eye tracking)`;
      case 'idle':
      default:         return `Idle (< ${fmt(th.happy)}) · Sleeping & drifting peacefully...`;
    }
  }


  // ================================================================
  //  GHOST DRAWING ENGINE
  // ================================================================
  const GLOW = {
    idle: '#94a3b8', low: '#3fb950', moderate: '#58a6ff',
    high: '#f59e0b', critical: '#f85149', peak: '#bc8cff'
  };
  const FACE = {
    idle: 'sleep', low: 'happy', moderate: 'focus',
    high: 'strain', critical: 'panic', peak: 'dead'
  };

  // ── Rounded-rect helper (works even without ctx.roundRect) ──────
  function rrect(ctx, x, y, w, h, r) {
    r = Math.min(r, w/2, h/2);
    ctx.beginPath();
    ctx.moveTo(x+r, y);
    ctx.lineTo(x+w-r, y);
    ctx.quadraticCurveTo(x+w, y,   x+w, y+r);
    ctx.lineTo(x+w, y+h-r);
    ctx.quadraticCurveTo(x+w, y+h, x+w-r, y+h);
    ctx.lineTo(x+r, y+h);
    ctx.quadraticCurveTo(x, y+h,   x, y+h-r);
    ctx.lineTo(x, y+r);
    ctx.quadraticCurveTo(x, y,     x+r, y);
    ctx.closePath();
  }

  function drawGhost(ctx, w, h, tier, t, gazeX, gazeY, blink, isPetted) {
    ctx.clearRect(0, 0, w, h);
    ctx.save();

    const cx = w / 2;
    const floatY = Math.sin(t * 2.3) * 5;
    // Place ghost vertically: center of body ~55% down the canvas
    const cy = h * 0.52 + floatY;

    ctx.translate(cx, cy);

    const glowCol = isPetted ? '#ec4899' : (GLOW[tier] || '#94a3b8');
    const faceType = isPetted ? 'love' : (FACE[tier] || 'sleep');

    // Jitter for panic states
    let jx = 0;
    if (tier === 'critical') jx = Math.sin(t * 34) * 3;
    if (tier === 'peak')     jx = Math.sin(t * 50) * 5;
    ctx.translate(jx, 0);

    const BW = 30;   // body half-width (restored to compact size)
    const BH = 50;   // body height
    const TOP = -BH * 0.5;

    // ── Ambient glow ──────────────────────────────────────────────
    const glowGrad = ctx.createRadialGradient(0, -4, 3, 0, -4, 52);
    glowGrad.addColorStop(0, glowCol + '3a');
    glowGrad.addColorStop(1, 'transparent');
    ctx.fillStyle = glowGrad;
    ctx.beginPath();
    ctx.ellipse(0, -4, 52, 52, 0, 0, Math.PI * 2);
    ctx.fill();

    // ── Ghost body path ───────────────────────────────────────────
    ctx.beginPath();
    ctx.arc(0, TOP + BW, BW, Math.PI, 0, false);    // rounded top
    ctx.lineTo(BW, TOP + BH - 12);                   // right side

    // Wavy scalloped bottom (3 bumps, animated)
    const wt = t * 3.2;
    const wa = 7;
    ctx.quadraticCurveTo(
      BW * 0.68 + Math.sin(wt) * 1.5,        TOP + BH + wa * 0.85,
      BW * 0.34,                              TOP + BH - 3
    );
    ctx.quadraticCurveTo(
      Math.sin(wt + 1.05) * 2,               TOP + BH + wa,
      -BW * 0.34,                             TOP + BH - 3
    );
    ctx.quadraticCurveTo(
      -BW * 0.68 + Math.sin(wt + 2.1) * 1.5, TOP + BH + wa * 0.85,
      -BW,                                    TOP + BH - 12
    );
    ctx.lineTo(-BW, TOP + BW);
    ctx.closePath();

    // Body fill
    const bodyGrad = ctx.createLinearGradient(0, TOP, 0, TOP + BH);
    if (tier === 'critical' || tier === 'peak') {
      bodyGrad.addColorStop(0,   'rgba(255, 215, 215, 0.95)');
      bodyGrad.addColorStop(0.6, 'rgba(255, 165, 165, 0.88)');
      bodyGrad.addColorStop(1,   'rgba(255, 120, 120, 0.76)');
    } else {
      bodyGrad.addColorStop(0,   'rgba(242, 250, 255, 0.96)');
      bodyGrad.addColorStop(0.55,'rgba(218, 238, 255, 0.90)');
      bodyGrad.addColorStop(1,   'rgba(185, 218, 255, 0.78)');
    }
    ctx.fillStyle = bodyGrad;
    ctx.fill();

    // Body outline
    ctx.strokeStyle = 'rgba(255,255,255,0.48)';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Inner sheen
    ctx.save();
    ctx.strokeStyle = 'rgba(255,255,255,0.60)';
    ctx.lineWidth = 1.4;
    ctx.beginPath();
    ctx.arc(-9, TOP + 13, 11, Math.PI * 1.12, Math.PI * 1.72);
    ctx.stroke();
    ctx.restore();

    // ── LCD Screen face (Compact & Crisp) ─────────────────────────
    const SW = 46, SH = 26;
    const SX = -SW / 2;
    const SY = TOP + BW * 0.42 - SH / 2;

    // Screen body (dark glossy display)
    ctx.fillStyle = '#060b13';
    ctx.shadowColor = glowCol;
    ctx.shadowBlur = 10;
    rrect(ctx, SX, SY, SW, SH, 6);
    ctx.fill();

    // Screen bezel & glow
    ctx.strokeStyle = glowCol + 'dd';
    ctx.lineWidth = 1.6;
    rrect(ctx, SX, SY, SW, SH, 6);
    ctx.stroke();
    ctx.shadowBlur = 0;

    // Subtle CRT scanline effect inside visor
    ctx.save();
    rrect(ctx, SX + 1, SY + 1, SW - 2, SH - 2, 5);
    ctx.clip();
    ctx.fillStyle = 'rgba(255, 255, 255, 0.03)';
    for (let sy = SY + 2; sy < SY + SH; sy += 3) {
      ctx.fillRect(SX, sy, SW, 1);
    }

    // Draw the expression inside the visor
    drawLCDFace(ctx, 0, SY + SH / 2, faceType, t, gazeX, gazeY, blink, glowCol);
    ctx.restore();

    // (Arms removed as requested)
    ctx.restore();
  }

  // ── LCD Face expressions (High-Contrast & Scaled) ─────────────
  function drawLCDFace(ctx, cx, cy, faceType, t, gazeX, gazeY, blink, glowCol) {
    const ES = 11;   // eye x-offset from center for wider, clearer visor

    switch (faceType) {

      case 'sleep': {
        ctx.strokeStyle = '#94a3b8';
        ctx.lineWidth = 2.4;
        ctx.lineCap = 'round';
        ctx.shadowColor = '#94a3b8';
        ctx.shadowBlur = 5;
        // Closed crescent eyes (peaceful sleepy eyelids)
        ctx.beginPath();
        ctx.arc(cx - ES, cy + 1, 4.5, Math.PI * 1.15, Math.PI * 1.85);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(cx + ES, cy + 1, 4.5, Math.PI * 1.15, Math.PI * 1.85);
        ctx.stroke();
        ctx.shadowBlur = 0;

        // Animated drifting 'z's drifting up & fading
        const zProg1 = (t * 1.6) % 2.5;
        const z1Y = cy - 2 - zProg1 * 4;
        const z1A = Math.max(0, 1 - zProg1 / 2.5);
        ctx.fillStyle = `rgba(148, 163, 184, ${z1A * 0.9})`;
        ctx.font = 'bold 9px monospace';
        ctx.textAlign = 'center';
        ctx.fillText('z', cx + 15, z1Y);

        const zProg2 = (t * 1.6 + 1.25) % 2.5;
        const z2Y = cy - 2 - zProg2 * 4;
        const z2A = Math.max(0, 1 - zProg2 / 2.5);
        ctx.fillStyle = `rgba(148, 163, 184, ${z2A * 0.65})`;
        ctx.font = 'bold 7px monospace';
        ctx.fillText('z', cx + 11, z2Y - 1);
        break;
      }

      case 'love': {
        ctx.fillStyle = '#f43f5e';
        ctx.shadowColor = '#f43f5e';
        ctx.shadowBlur = 8;
        miniHeart(ctx, cx - ES, cy, 6);
        miniHeart(ctx, cx + ES, cy, 6);
        ctx.shadowBlur = 0;
        // Cute rosy cheeks
        ctx.fillStyle = 'rgba(244, 63, 94, 0.45)';
        ctx.beginPath();
        ctx.arc(cx - ES - 5, cy + 4, 2.5, 0, Math.PI * 2);
        ctx.arc(cx + ES + 5, cy + 4, 2.5, 0, Math.PI * 2);
        ctx.fill();
        break;
      }

      case 'happy': {
        const eyeCol = '#22c55e';
        if (blink > 0.5) {
          // Blink line
          ctx.strokeStyle = eyeCol;
          ctx.lineWidth = 2.4; ctx.lineCap = 'round';
          ctx.beginPath();
          ctx.moveTo(cx - ES - 4, cy); ctx.lineTo(cx - ES + 4, cy);
          ctx.moveTo(cx + ES - 4, cy); ctx.lineTo(cx + ES + 4, cy);
          ctx.stroke();
        } else {
          const gx = Math.max(-3.5, Math.min(3.5, gazeX * 1.2));
          const gy = Math.max(-2.8, Math.min(2.8, gazeY * 1.1));
          // Glowing green dot eyes
          ctx.fillStyle = eyeCol;
          ctx.shadowColor = eyeCol;
          ctx.shadowBlur = 8;
          ctx.beginPath();
          ctx.arc(cx - ES + gx, cy + gy - 1, 4.2, 0, Math.PI * 2);
          ctx.arc(cx + ES + gx, cy + gy - 1, 4.2, 0, Math.PI * 2);
          ctx.fill();
          ctx.shadowBlur = 0;
          // Crisp catchlights
          ctx.fillStyle = '#ffffff';
          ctx.beginPath();
          ctx.arc(cx - ES + gx - 1.4, cy + gy - 2.4, 1.4, 0, Math.PI * 2);
          ctx.arc(cx + ES + gx - 1.4, cy + gy - 2.4, 1.4, 0, Math.PI * 2);
          ctx.fill();
          // Friendly digital smile
          ctx.strokeStyle = '#22c55e';
          ctx.lineWidth = 1.8;
          ctx.lineCap = 'round';
          ctx.beginPath();
          ctx.arc(cx, cy + 3.5, 4.5, 0.25, Math.PI - 0.25);
          ctx.stroke();
        }
        break;
      }

      case 'focus': {
        const eyeCol = '#38bdf8';
        if (blink > 0.5) {
          ctx.strokeStyle = eyeCol;
          ctx.lineWidth = 2.4; ctx.lineCap = 'round';
          ctx.beginPath();
          ctx.moveTo(cx - ES - 4, cy); ctx.lineTo(cx - ES + 4, cy);
          ctx.moveTo(cx + ES - 4, cy); ctx.lineTo(cx + ES + 4, cy);
          ctx.stroke();
        } else {
          const gx = Math.max(-3.5, Math.min(3.5, gazeX * 1.2));
          const gy = Math.max(-2.8, Math.min(2.8, gazeY * 1.1));
          // Concentric ocular focus rings
          ctx.strokeStyle = 'rgba(56, 189, 248, 0.45)';
          ctx.lineWidth = 1.4;
          ctx.beginPath();
          ctx.arc(cx - ES, cy, 5.8, 0, Math.PI * 2);
          ctx.arc(cx + ES, cy, 5.8, 0, Math.PI * 2);
          ctx.stroke();

          // Intense focused pupil
          ctx.fillStyle = eyeCol;
          ctx.shadowColor = eyeCol;
          ctx.shadowBlur = 8;
          ctx.beginPath();
          ctx.arc(cx - ES + gx, cy + gy, 3.8, 0, Math.PI * 2);
          ctx.arc(cx + ES + gx, cy + gy, 3.8, 0, Math.PI * 2);
          ctx.fill();
          ctx.shadowBlur = 0;

          // Catchlights
          ctx.fillStyle = '#ffffff';
          ctx.beginPath();
          ctx.arc(cx - ES + gx - 1.2, cy + gy - 1.2, 1.2, 0, Math.PI * 2);
          ctx.arc(cx + ES + gx - 1.2, cy + gy - 1.2, 1.2, 0, Math.PI * 2);
          ctx.fill();

          // Focused brow line
          ctx.strokeStyle = eyeCol;
          ctx.lineWidth = 1.6;
          ctx.beginPath();
          ctx.moveTo(cx - ES - 5, cy - 6); ctx.lineTo(cx - 2, cy - 4.5);
          ctx.moveTo(cx + 2, cy - 4.5);     ctx.lineTo(cx + ES + 5, cy - 6);
          ctx.stroke();
        }
        break;
      }

      case 'strain': {
        ctx.strokeStyle = '#f59e0b';
        ctx.lineWidth = 2.4; ctx.lineCap = 'round';
        ctx.shadowColor = '#f59e0b';
        ctx.shadowBlur = 6;

        // Worried downward-arc eyes
        ctx.beginPath();
        ctx.arc(cx - ES, cy + 2, 4.5, 0.25, Math.PI - 0.25);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(cx + ES, cy + 2, 4.5, 0.25, Math.PI - 0.25);
        ctx.stroke();

        // Strained jagged mouth
        ctx.beginPath();
        ctx.moveTo(cx - 4, cy + 7);
        ctx.lineTo(cx - 1, cy + 5.5);
        ctx.lineTo(cx + 2, cy + 7.5);
        ctx.lineTo(cx + 4, cy + 6.5);
        ctx.stroke();
        ctx.shadowBlur = 0;

        // Animated pulsing cyan sweat drop on temple
        const swPhase = (t * 3.5) % 2;
        const swY = cy - 5 + swPhase * 4.5;
        const swA = Math.sin(swPhase * Math.PI / 2);
        ctx.fillStyle = `rgba(56, 189, 248, ${0.4 + swA * 0.6})`;
        ctx.beginPath();
        ctx.arc(cx + 17, swY, 2.5, 0, Math.PI * 2);
        ctx.fill();
        break;
      }

      case 'panic': {
        const shkX = Math.sin(t * 45) * 2;
        const shkY = Math.cos(t * 52) * 1.5;
        ctx.strokeStyle = '#ef4444';
        ctx.shadowColor = '#ef4444';
        ctx.shadowBlur  = 10;
        ctx.lineWidth   = 2.8;
        ctx.lineCap     = 'round';

        // Red X eyes shaking rapidly
        [[cx - ES + shkX, cy + shkY], [cx + ES + shkX, cy + shkY]].forEach(([ex, ey]) => {
          ctx.beginPath();
          ctx.moveTo(ex - 4, ey - 4); ctx.lineTo(ex + 4, ey + 4);
          ctx.moveTo(ex + 4, ey - 4); ctx.lineTo(ex - 4, ey + 4);
          ctx.stroke();
        });

        // Open vibrating mouth
        ctx.beginPath();
        ctx.ellipse(cx + shkX, cy + 6 + shkY, 3, 4.5, 0, 0, Math.PI * 2);
        ctx.stroke();
        ctx.shadowBlur = 0;
        break;
      }

      case 'dead': {
        ctx.strokeStyle = '#c084fc';
        ctx.shadowColor = '#c084fc';
        ctx.shadowBlur  = 7;
        ctx.lineWidth   = 2.6;
        ctx.lineCap     = 'round';

        // Crossed out X eyes
        [[cx - ES, cy], [cx + ES, cy]].forEach(([ex, ey]) => {
          ctx.beginPath();
          ctx.moveTo(ex - 4.5, ey - 4.5); ctx.lineTo(ex + 4.5, ey + 4.5);
          ctx.moveTo(ex + 4.5, ey - 4.5); ctx.lineTo(ex - 4.5, ey + 4.5);
          ctx.stroke();
        });
        ctx.shadowBlur = 0;

        // Animated cyan drool drip
        const droolProg = (t * 2) % 2;
        const droolH = 3 + droolProg * 5;
        ctx.strokeStyle = 'rgba(56, 189, 248, 0.85)';
        ctx.lineWidth = 2.2;
        ctx.beginPath();
        ctx.moveTo(cx + 3, cy + 5);
        ctx.lineTo(cx + 3, cy + 5 + droolH);
        ctx.stroke();

        // Droplet bead at bottom of drool
        ctx.fillStyle = 'rgba(56, 189, 248, 0.95)';
        ctx.beginPath();
        ctx.arc(cx + 3, cy + 5 + droolH + 1, 1.8, 0, Math.PI * 2);
        ctx.fill();
        break;
      }
    }
  }

  function miniHeart(ctx, x, y, r) {
    ctx.beginPath();
    ctx.moveTo(x, y + r * 0.35);
    ctx.bezierCurveTo(x, y - r * 0.25, x - r, y - r * 0.25, x - r, y + r * 0.3);
    ctx.bezierCurveTo(x - r, y + r * 0.9, x,     y + r * 1.1, x, y + r * 1.1);
    ctx.bezierCurveTo(x,     y + r * 1.1, x + r, y + r * 0.9, x + r, y + r * 0.3);
    ctx.bezierCurveTo(x + r, y - r * 0.25, x,    y - r * 0.25, x, y + r * 0.35);
    ctx.fill();
  }

  // ── Sparkline ─────────────────────────────────────────────────
  function drawSparkline() {
    const w = el.sparkCanvas.width, h = el.sparkCanvas.height;
    sparkCtx.clearRect(0, 0, w, h);
    const data = state.sparkline;
    const maxV = Math.max(...data, 10);
    sparkCtx.beginPath();
    sparkCtx.strokeStyle = state.cpu >= 86 ? '#f85149' : '#3fb950';
    sparkCtx.lineWidth = 1.6;
    sparkCtx.lineJoin = 'round';
    const sx = w / (data.length - 1);
    data.forEach((v, i) => {
      const x = i * sx;
      const y = h - 2 - (Math.min(1, v/maxV)) * (h - 4);
      i === 0 ? sparkCtx.moveTo(x, y) : sparkCtx.lineTo(x, y);
    });
    sparkCtx.stroke();
  }

  // ── Events ────────────────────────────────────────────────────
  el.perfCards.forEach(card => {
    card.addEventListener('click', () => {
      state.targetCpu  = parseFloat(card.dataset.cpu);
      state.targetDown = parseFloat(card.dataset.down) * 1024;
      state.targetUp   = parseFloat(card.dataset.up)   * 1024;
      el.sliderDown.value = parseFloat(card.dataset.down);
      const mb = parseFloat(card.dataset.down);
      const kb = mb * 1024;
      el.sliderDownTxt.textContent = kb < 1024 ? Math.round(kb) + ' KB/s' : mb.toFixed(1) + ' MB/s';
      el.sliderCpu.value     = parseFloat(card.dataset.cpu);
      el.sliderCpuTxt.textContent  = parseFloat(card.dataset.cpu) + '%';
      el.perfCards.forEach(c => c.classList.remove('active'));
      card.classList.add('active');
    });
  });

  el.sliderDown.addEventListener('input', e => {
    const mb = parseFloat(e.target.value);
    const kb = mb * 1024;
    el.sliderDownTxt.textContent = kb < 1024 ? Math.round(kb) + ' KB/s' : mb.toFixed(1) + ' MB/s';
    state.targetDown = kb;
    state.targetUp   = kb * 0.15;
    if      (mb >= 10)  state.targetCpu = 99;
    else if (mb >= 5)   state.targetCpu = 88;
    else if (mb >= 1)   state.targetCpu = 68;
    else if (mb >= 0.5) state.targetCpu = 45;
    else if (mb >= 0.01)state.targetCpu = 22;
    else                state.targetCpu = 6;
  });

  el.sliderCpu.addEventListener('input', e => {
    const val = parseInt(e.target.value, 10);
    el.sliderCpuTxt.textContent = val + '%';
    state.targetCpu = val;
  });

  // Ghost click → pet
  el.ghostBay.addEventListener('click', e => {
    e.stopPropagation();
    state.isPettedUntil = performance.now() + 1600;
    spawnHeart();
  });

  function spawnHeart() {
    // Spawn 2 sparkling particles per pet for richer delight
    for (let i = 0; i < 2; i++) {
      const h = document.createElement('div');
      h.className = 'floating-heart';
      const emojis = ['💖', '✨', '👻', '🌸', '💫', '⭐', '🌟'];
      h.textContent = emojis[Math.floor(Math.random() * emojis.length)];
      // Fan outward to the left & right sides, and drift downward into clear screen space
      const side = (i === 0 ? (Math.random() > 0.5 ? 1 : -1) : (Math.random() > 0.5 ? -1 : 1));
      const dx = side * (24 + Math.random() * 36); // ±24px to ±60px left/right
      const dy = 10 + Math.random() * 28;          // +10px to +38px downward
      h.style.setProperty('--dx', `${dx}px`);
      h.style.setProperty('--dy', `${dy}px`);
      el.heartEmitter.appendChild(h);
      setTimeout(() => h.remove(), 1150);
    }
  }

  // Bar click → toggle HUD
  el.notchBar.addEventListener('click', e => {
    e.stopPropagation();
    state.hudExpanded = !state.hudExpanded;
    el.notchContainer.classList.toggle('expanded', state.hudExpanded);
  });

  document.addEventListener('click', e => {
    if (state.hudExpanded && !el.notchContainer.contains(e.target)) {
      state.hudExpanded = false;
      el.notchContainer.classList.remove('expanded');
    }
  });

  el.ipChip.addEventListener('click', () => {
    navigator.clipboard.writeText(el.hudIp.textContent).catch(() => {});
    const badge = el.ipChip.querySelector('.chip-badge');
    badge.textContent = 'copied!'; badge.style.color = '#3fb950';
    setTimeout(() => { badge.textContent = 'copy'; badge.style.color = ''; }, 1500);
  });

  el.btnPetTreat.addEventListener('click', () => {
    state.isPettedUntil = performance.now() + 2400;
    spawnHeart(); spawnHeart();
    el.hudStatusDesc.textContent = 'Nom nom! Glitch loves the treat 👻';
  });

  el.btnQuickTest.addEventListener('click', () => {
    el.btnQuickTest.disabled = true;
    el.btnQuickTest.innerHTML = '<span>⏳</span> Testing...';
    let step = 0;
    const tid = setInterval(() => {
      step++;
      state.targetDown = Math.min(95 * 1024, 25 * 1024 * step);
      state.targetCpu  = Math.min(92, 20 + step * 16);
      if (step >= 5) {
        clearInterval(tid);
        setTimeout(() => {
          el.btnQuickTest.disabled = false;
          el.btnQuickTest.innerHTML = '<span>⚡</span> Run Speedtest';
          state.targetDown = 4.5 * 1024;
          state.targetCpu  = 14;
        }, 1200);
      }
    }, 500);
  });

  // ── Reaction Speed Limit Settings Handlers ───────────────────
  function syncThresholdInputs() {
    const th = state.thresholds;
    if (el.cfgHappy)  el.cfgHappy.value  = th.happy;
    if (el.cfgFocus)  el.cfgFocus.value  = th.focus;
    if (el.cfgStrain) el.cfgStrain.value = (th.strain / 1024).toFixed(1);
    if (el.cfgPanic)  el.cfgPanic.value  = (th.panic / 1024).toFixed(1);
    if (el.cfgDead)   el.cfgDead.value   = (th.dead / 1024).toFixed(1);

    if (el.stHappy)   el.stHappy.value   = th.happy;
    if (el.stFocus)   el.stFocus.value   = th.focus;
    if (el.stStrain)  el.stStrain.value  = (th.strain / 1024).toFixed(1);
    if (el.stPanic)   el.stPanic.value   = (th.panic / 1024).toFixed(1);
    if (el.stDead)    el.stDead.value    = (th.dead / 1024).toFixed(1);
  }

  function saveAndApplyThresholds(newTh) {
    state.thresholds = { ...newTh };
    try {
      localStorage.setItem('glitch_ghost_thresholds', JSON.stringify(state.thresholds));
    } catch(e) {}
    syncThresholdInputs();
    updateCardLabels();
  }

  function updateCardLabels() {
    const th = state.thresholds;
    const fmt = kb => kb >= 1024 ? (kb / 1024).toFixed(kb % 1024 === 0 ? 0 : 1) + ' MB/s' : Math.round(kb) + ' KB/s';
    const cardData = [
      { tier: 'idle',     label: `Idle (< ${fmt(th.happy)})` },
      { tier: 'low',      label: `${fmt(th.happy)}–${fmt(th.focus)}` },
      { tier: 'moderate', label: `${fmt(th.focus)}–${fmt(th.strain)}` },
      { tier: 'high',     label: `${fmt(th.strain)}–${fmt(th.panic)}` },
      { tier: 'critical', label: `${fmt(th.panic)}–${fmt(th.dead)}` },
      { tier: 'peak',     label: `> ${fmt(th.dead)}` }
    ];
    el.perfCards.forEach(c => {
      const match = cardData.find(d => d.tier === c.dataset.state);
      if (match) {
        const h4 = c.querySelector('h4');
        if (h4) h4.textContent = match.label;
      }
    });
  }

  // Toggle HUD settings panel
  if (el.btnToggleSettings) {
    el.btnToggleSettings.addEventListener('click', e => {
      e.stopPropagation();
      el.hudSettingsPanel.classList.toggle('open');
    });
  }
  if (el.btnCloseSettings) {
    el.btnCloseSettings.addEventListener('click', e => {
      e.stopPropagation();
      el.hudSettingsPanel.classList.remove('open');
    });
  }

  // Save Limits in HUD
  if (el.btnSaveLimits) {
    el.btnSaveLimits.addEventListener('click', e => {
      e.stopPropagation();
      const newTh = {
        happy:  parseFloat(el.cfgHappy.value) || DEFAULT_THRESHOLDS.happy,
        focus:  parseFloat(el.cfgFocus.value) || DEFAULT_THRESHOLDS.focus,
        strain: (parseFloat(el.cfgStrain.value) || 1) * 1024,
        panic:  (parseFloat(el.cfgPanic.value) || 5) * 1024,
        dead:   (parseFloat(el.cfgDead.value) || 10) * 1024
      };
      saveAndApplyThresholds(newTh);
      el.hudSettingsPanel.classList.remove('open');
    });
  }
  if (el.btnResetLimits) {
    el.btnResetLimits.addEventListener('click', e => {
      e.stopPropagation();
      saveAndApplyThresholds(DEFAULT_THRESHOLDS);
    });
  }

  // Save Limits in Studio Card
  if (el.btnStApply) {
    el.btnStApply.addEventListener('click', () => {
      const newTh = {
        happy:  parseFloat(el.stHappy.value) || DEFAULT_THRESHOLDS.happy,
        focus:  parseFloat(el.stFocus.value) || DEFAULT_THRESHOLDS.focus,
        strain: (parseFloat(el.stStrain.value) || 1) * 1024,
        panic:  (parseFloat(el.stPanic.value) || 5) * 1024,
        dead:   (parseFloat(el.stDead.value) || 10) * 1024
      };
      saveAndApplyThresholds(newTh);
      el.btnStApply.innerHTML = '<span>✓</span> Applied!';
      setTimeout(() => { el.btnStApply.innerHTML = '<span>✓</span> Apply &amp; Save Limits'; }, 1500);
    });
  }
  if (el.btnStReset) {
    el.btnStReset.addEventListener('click', () => {
      saveAndApplyThresholds(DEFAULT_THRESHOLDS);
      el.btnStReset.innerHTML = '<span>↺</span> Reset Done';
      setTimeout(() => { el.btnStReset.innerHTML = '<span>↺</span> Reset Defaults'; }, 1500);
    });
  }

  function updateUnitUI() {
    if (el.cfgUnitMode) el.cfgUnitMode.value = state.unitMode;
    if (el.btnUnitLabel) {
      el.btnUnitLabel.textContent = state.unitMode === 'bytes' ? 'Switch to Mbps' : 'Switch to MB/s';
    }
  }

  if (el.btnToggleUnit) {
    el.btnToggleUnit.addEventListener('click', () => {
      state.unitMode = state.unitMode === 'bytes' ? 'bits' : 'bytes';
      try { localStorage.setItem('notch_buddy_unit', state.unitMode); } catch(e) {}
      updateUnitUI();
    });
  }

  if (el.cfgUnitMode) {
    el.cfgUnitMode.addEventListener('change', e => {
      state.unitMode = e.target.value;
      try { localStorage.setItem('notch_buddy_unit', state.unitMode); } catch(e) {}
      updateUnitUI();
    });
  }

  if (el.cfgRefresh) {
    el.cfgRefresh.value = state.refreshInterval;
    el.cfgRefresh.addEventListener('change', e => {
      state.refreshInterval = parseInt(e.target.value, 10);
      try { localStorage.setItem('notch_buddy_refresh', state.refreshInterval); } catch(e) {}
    });
  }

  // Initialize UI with current thresholds & units
  syncThresholdInputs();
  updateCardLabels();
  updateUnitUI();

  // ── Main Loop ────────────────────────────────────────────────
  let lastSpark = 0;

  function loop(ts) {
    const t = ts * 0.001;

    // Smooth state
    state.downSpeed   += (state.targetDown - state.downSpeed) * 0.12;
    state.upSpeed     += (state.targetUp   - state.upSpeed)   * 0.12;
    state.cpu         += (state.targetCpu  - state.cpu)       * 0.08;
    state.sessionBytes += state.downSpeed * 1024 / 60;

    // Blink timer
    if (ts - state.lastBlinkTime > 3500 + Math.sin(t) * 1500) {
      state.blinkProgress = 1;
      state.lastBlinkTime = ts;
    } else {
      state.blinkProgress = Math.max(0, state.blinkProgress - 0.14);
    }

    // Eye tracking (vector from ghost center → cursor, clamped)
    const gr   = el.ghostCanvas.getBoundingClientRect();
    const gcx  = gr.left + gr.width  / 2;
    const gcy  = gr.top  + gr.height / 2;
    const dx   = state.cursorX - gcx;
    const dy   = state.cursorY - gcy;
    const dist = Math.hypot(dx, dy);
    const MO   = 2.8;
    const gazeX = Math.max(-MO, Math.min(MO, (dx / (dist + 1)) * MO));
    const gazeY = Math.max(-MO, Math.min(MO, (dy / (dist + 1)) * MO));

    const tier = calcTier();
    state.currentTier = tier;

    const downFmt = formatSpeed(state.downSpeed);
    const upFmt   = formatSpeed(state.upSpeed);

    // Update notch bar
    el.downVal.textContent  = downFmt.val;
    el.downUnit.textContent = downFmt.unit;
    el.upVal.textContent    = upFmt.val;
    el.upUnit.textContent   = upFmt.unit;
    el.cpuVal.textContent   = Math.round(state.cpu) + '%';

    // Update HUD
    el.hudDownNum.textContent  = downFmt.val;
    el.hudDownUnit.textContent = downFmt.unit;
    el.hudUpNum.textContent    = upFmt.val;
    el.hudUpUnit.textContent   = upFmt.unit;
    el.hudStatusDesc.textContent = getTierDesc(tier);
    el.hudSessionBytes.textContent = formatBytes(state.sessionBytes);

    // Top apps breakdown
    if (el.appUsageChrome) {
      const chrFmt = formatSpeed(state.downSpeed * 0.58 + state.upSpeed * 0.25);
      el.appUsageChrome.textContent = `${chrFmt.val} ${chrFmt.unit}`;
    }
    if (el.appUsageSteam) {
      const stmFmt = formatSpeed(state.downSpeed * 0.32);
      el.appUsageSteam.textContent = `${stmFmt.val} ${stmFmt.unit}`;
    }
    if (el.appUsageSlack) {
      const slkFmt = formatSpeed(state.downSpeed * 0.10 + state.upSpeed * 0.65);
      el.appUsageSlack.textContent = `${slkFmt.val} ${slkFmt.unit}`;
    }

    // Monthly data quota tracker
    if (el.quotaText && el.quotaFill) {
      const totalUsed = state.baseMonthlyBytes + state.sessionBytes;
      const usedGB = (totalUsed / (1024 * 1024 * 1024)).toFixed(1);
      const capGB = (state.quotaLimitBytes / (1024 * 1024 * 1024)).toFixed(0);
      const pct = Math.min(100, Math.round((totalUsed / state.quotaLimitBytes) * 100));
      el.quotaText.textContent = `${usedGB} / ${capGB} GB (${pct}%)`;
      el.quotaFill.style.width = `${pct}%`;
      if (pct >= 90)      el.quotaFill.style.background = 'linear-gradient(90deg, #f59e0b, #ef4444)';
      else if (pct >= 75) el.quotaFill.style.background = 'linear-gradient(90deg, #38bdf8, #f59e0b)';
      else                el.quotaFill.style.background = 'linear-gradient(90deg, var(--blue), var(--green))';
    }


    // Ping
    if (tier === 'critical' || tier === 'peak') {
      el.pingDot.className = 'ping-dot crit';
      el.hudPingText.textContent = '148 ms · High Jitter';
    } else if (tier === 'high') {
      el.pingDot.className = 'ping-dot warn';
      el.hudPingText.textContent = '42 ms · Moderate';
    } else {
      el.pingDot.className = 'ping-dot';
      el.hudPingText.textContent = '16 ms · Ultra Low';
    }

    // Highlight active perf card
    el.perfCards.forEach(c => c.classList.toggle('active', c.dataset.state === tier));

    // Sparkline update
    if (ts - lastSpark > 250) {
      state.sparkline.shift();
      state.sparkline.push(state.downSpeed);
      drawSparkline();
      lastSpark = ts;
    }

    // ── Render ghost on notch ──
    const isPetted = ts < state.isPettedUntil;
    drawGhost(ghostCtx,
      el.ghostCanvas.width, el.ghostCanvas.height,
      tier, t, gazeX, gazeY, state.blinkProgress, isPetted);

    // ── Render ghost on HUD mini pic ──
    drawGhost(hudMiniCtx,
      el.hudMiniCanvas.width, el.hudMiniCanvas.height,
      tier, t, gazeX * 0.5, gazeY * 0.5, state.blinkProgress, isPetted);

    // ── Render ghosts on studio cards ──
    el.cardCanvases.forEach(cc => {
      const cCtx = cc.getContext('2d');
      drawGhost(cCtx, cc.width, cc.height,
        cc.dataset.tier, t, gazeX * 0.4, gazeY * 0.4, state.blinkProgress, false);
    });

    requestAnimationFrame(loop);
  }

  requestAnimationFrame(loop);
})();
