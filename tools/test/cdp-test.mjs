// UI end-to-end test driven through Chrome DevTools Protocol (no extra deps).
// Usage: node tools/test/cdp-test.mjs
const DEBUG = 'http://127.0.0.1:9333';
const APP = 'http://localhost:8081/';
const XLSX = 'C:\\programing\\java project\\tools\\test\\test-students.xlsx';

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function findTarget() {
  for (let i = 0; i < 40; i++) {
    try {
      const list = await (await fetch(DEBUG + '/json/list')).json();
      const page = list.find(t => t.type === 'page' && t.url.startsWith('http://localhost:8081'));
      if (page) return page;
    } catch (e) { /* chrome not up yet */ }
    await sleep(500);
  }
  throw new Error('Chrome target not found - did Chrome start with --remote-debugging-port=9333?');
}

const target = await findTarget();
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });

let seq = 0;
const pending = new Map();
const pageErrors = [];
ws.onmessage = ev => {
  const msg = JSON.parse(ev.data);
  if (msg.id && pending.has(msg.id)) {
    const { res, rej } = pending.get(msg.id);
    pending.delete(msg.id);
    msg.error ? rej(new Error(JSON.stringify(msg.error))) : res(msg.result);
  } else if (msg.method === 'Runtime.exceptionThrown') {
    pageErrors.push('EXCEPTION: ' + (msg.params.exceptionDetails.exception?.description || msg.params.exceptionDetails.text));
  } else if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
    pageErrors.push('CONSOLE: ' + msg.params.args.map(a => a.value ?? a.description ?? '').join(' '));
  } else if (msg.method === 'Page.javascriptDialogOpening') {
    send('Page.handleJavaScriptDialog', { accept: true }).catch(() => {});
  }
};
function send(method, params = {}) {
  return new Promise((res, rej) => {
    const id = ++seq;
    pending.set(id, { res, rej });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (r.exceptionDetails) throw new Error('page eval failed: ' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text));
  return r.result.value;
}

const results = {};
let failed = 0;
function check(name, cond, detail = '') {
  const ok = !!cond;
  if (!ok) failed++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`);
}

try {
  await send('Page.enable');
  await send('Runtime.enable');

  // Reload to start from a clean state (previous run may have left a tab open).
  await send('Page.navigate', { url: APP });
  await sleep(3500);

  // ---------- dashboard ----------
  results.dashboard = await evaluate(`(async () => {
    const $ = id => document.getElementById(id);
    for (let i = 0; i < 50 && $('stat-students').textContent.trim() === '\\u2013'; i++) await new Promise(r => setTimeout(r, 100));
    return { students: $('stat-students').textContent, rooms: $('stat-rooms').textContent,
             exams: $('stat-exams').textContent, seats: $('stat-seats').textContent };
  })()`);
  check('dashboard stats load', results.dashboard.students > 0, JSON.stringify(results.dashboard));

  // ---------- students: tabs, Enter navigation, add, edit, delete ----------
  results.students = await evaluate(`(async () => {
    const $ = id => document.getElementById(id);
    const sleep = ms => new Promise(r => setTimeout(r, ms));
    const out = {};

    document.querySelector('.tab[data-section="students"]').click();
    await sleep(700);
    out.rows = document.querySelectorAll('#student-list tr').length;

    // Enter in first field moves to second field
    $('s-roll').focus();
    $('s-roll').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    out.enterMovesToName = document.activeElement.id === 's-name';

    // Fill form and press Enter in the last field -> adds the student
    $('s-roll').value = '555'; $('s-name').value = 'UI Test'; $('s-course').value = 'CSE'; $('s-sem').value = 'S3';
    $('s-sem').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    await sleep(1200);
    out.added = document.querySelector('#student-list').textContent.includes('555');
    out.formCleared = $('s-roll').value === '';

    // Edit flow
    const editBtn = [...document.querySelectorAll('#student-list button')].find(b => b.textContent === 'Edit');
    editBtn.click(); await sleep(250);
    const row = document.querySelector('#student-list tr[data-edit]');
    out.editRendered = !!row;
    if (row) {
      row.querySelector('[data-f="name"]').value = 'Edited Name';
      const inputs = [...row.querySelectorAll('input')];
      inputs[0].focus();
      inputs[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      out.enterMovesInEditRow = document.activeElement === inputs[1];
      row.querySelector('[data-act="save"]').click();
      await sleep(1200);
      out.saved = document.querySelector('#student-list').textContent.includes('Edited Name');
    }

    // Delete the student we created (confirm dialog auto-accepted by CDP)
    const delBtn = [...document.querySelectorAll('#student-list tr')].find(tr => tr.textContent.includes('555'))
      ?.querySelector('button[data-act="delete"]');
    if (delBtn) { delBtn.click(); await sleep(1200); }
    out.deleted = !document.querySelector('#student-list').textContent.includes('555');
    return out;
  })()`);
  check('students table renders', results.students.rows > 0, 'rows=' + results.students.rows);
  check('Enter moves to next field', results.students.enterMovesToName);
  check('Enter in last field adds student', results.students.added && results.students.formCleared);
  check('edit row renders with inputs', results.students.editRendered);
  check('Enter moves between edit inputs', results.students.enterMovesInEditRow);
  check('save updates student', results.students.saved);
  check('delete removes student', results.students.deleted);

  // ---------- rooms ----------
  results.rooms = await evaluate(`(async () => {
    document.querySelector('.tab[data-section="rooms"]').click();
    await new Promise(r => setTimeout(r, 800));
    return { rows: document.querySelectorAll('#room-list tr').length,
             hasGrid: document.querySelector('#room-list').textContent.includes('121') };
  })()`);
  check('rooms tab lists rooms', results.rooms.rows >= 2 && results.rooms.hasGrid, JSON.stringify(results.rooms));

  // ---------- exams: create with auto application id ----------
  results.exams = await evaluate(`(async () => {
    const $ = id => document.getElementById(id);
    const sleep = ms => new Promise(r => setTimeout(r, ms));
    const out = {};
    document.querySelector('.tab[data-section="exams"]').click();
    await sleep(700);
    out.rows = document.querySelectorAll('#exam-list tr').length;

    $('e-subject').value = 'Physics';
    $('e-sem').value = 'S5';
    $('e-date').value = '2026-12-01';
    $('e-time').value = '09:30';
    [...document.querySelectorAll('#exam-form button')].find(b => b.textContent.includes('Add Exam')).click();
    await sleep(1200);
    out.rowsAfter = document.querySelectorAll('#exam-list tr').length;
    const text = document.querySelector('#exam-list').textContent;
    out.created = text.includes('Physics') && text.includes('S5');
    out.appIdAuto = /APP-S5-20261201-\\d{3}/.test(text);
    return out;
  })()`);
  check('exams tab lists exams', results.exams.rows >= 1, 'rows=' + results.exams.rows);
  check('exam created via form', results.exams.created);
  check('application ID auto-generated', results.exams.appIdAuto);

  // ---------- seating chart ----------
  results.chart = await evaluate(`(async () => {
    const $ = id => document.getElementById(id);
    const sleep = ms => new Promise(r => setTimeout(r, ms));
    const out = {};
    document.querySelector('.tab[data-section="allocation"]').click();
    await sleep(900);
    out.examOptions = $('alloc-exam').options.length;
    $('alloc-exam').value = '1';
    $('alloc-exam').dispatchEvent(new Event('change'));
    await sleep(800);
    document.getElementById('btn-generate').click();
    await sleep(1500);
    out.message = $('allocation-message').textContent;
    out.messageOk = $('allocation-message').className.includes('ok');
    out.seatCells = document.querySelectorAll('#chart-container .seat').length;
    out.vacantCells = document.querySelectorAll('#chart-container .seat.vacant').length;
    out.legend = $('chart-legend').textContent.trim();
    out.unallocShown = $('unallocated-block').style.display !== 'none';
    out.statsVisible = $('alloc-stats').style.display !== 'none';
    out.pdfReachable = await fetch('/api/chart/pdf?examId=1').then(r => r.status).catch(() => 0);
    return out;
  })()`);
  check('exam dropdown filled', results.chart.examOptions >= 2, 'options=' + results.chart.examOptions);
  check('allocation generates chart', results.chart.messageOk && results.chart.seatCells >= 15,
        results.chart.message);
  check('grid has vacant cells', results.chart.vacantCells > 0, 'vacant=' + results.chart.vacantCells);
  check('course legend rendered', results.chart.legend.includes('CSE'), results.chart.legend);
  check('stats row visible', results.chart.statsVisible);
  check('chart PDF reachable from browser', results.chart.pdfReachable === 200, 'status=' + results.chart.pdfReachable);

  // ---------- import ----------
  results.import = await evaluate(`(async () => {
    const sleep = ms => new Promise(r => setTimeout(r, ms));
    document.querySelector('.tab[data-section="import"]').click();
    await sleep(500);
    return true;
  })()`);
  const doc = await send('DOM.getDocument', { depth: 0 });
  const fileNode = await send('DOM.querySelector', { nodeId: doc.root.nodeId, selector: '#import-file' });
  await send('DOM.setFileInputFiles', { files: [XLSX], nodeId: fileNode.nodeId });
  results.importResult = await evaluate(`(async () => {
    const $ = id => document.getElementById(id);
    [...document.querySelectorAll('#import-section button')].find(b => b.textContent.includes('Upload')).click();
    for (let i = 0; i < 40 && $('import-result').style.display === 'none'; i++) await new Promise(r => setTimeout(r, 250));
    return {
      visible: $('import-result').style.display !== 'none',
      counts: $('import-counts').textContent.replace(/\\s+/g, ' ').trim(),
      notes: $('import-notes').textContent.replace(/\\s+/g, ' ').trim(),
      previewRows: document.querySelectorAll('#import-preview tr').length
    };
  })()`);
  check('import UI shows result', results.importResult.visible && results.importResult.previewRows > 0,
        JSON.stringify(results.importResult));

} catch (e) {
  failed++;
  console.log('FAIL  uncaught: ' + e.message);
}

console.log('\nPage errors: ' + (pageErrors.length ? '\n  ' + pageErrors.join('\n  ') : 'none'));
if (pageErrors.length) failed++;
console.log(failed === 0 ? '\nALL UI TESTS PASSED' : `\n${failed} CHECK(S) FAILED`);
process.exit(failed === 0 ? 0 : 1);
