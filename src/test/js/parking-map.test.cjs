/* Run with NODE_PATH pointing to jsdom (test-only; no frontend runtime dependency). */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {JSDOM} = require('jsdom');
const base = path.resolve(__dirname,'../../..');
const template = fs.readFileSync(path.join(base,'src/main/resources/templates/ui/parking-map.html'),'utf8');
const script = fs.readFileSync(path.join(base,'src/main/resources/static/js/parking-map.js'),'utf8');
const css = fs.readFileSync(path.join(base,'src/main/resources/static/css/redesign.css'),'utf8');
const bay = (id,extra={}) => ({id,spaceNumber:`TEST-${id}`,floor:1,zone:'TEST-ZONE',status:'AVAILABLE',occupied:false,selectable:true,hourlyRate:250,hasEVCharging:false,isAccessible:false,parkingType:'STANDARD',allocation:'GENERAL',...extra});
const flush = () => new Promise(resolve => setImmediate(resolve));
async function fixture(initial,{booking=true,width=940,selected=''}={}) {
  const fragment = template.match(/<section th:fragment[\s\S]*<\/section>/)[0];
  const dom = new JSDOM(`<form id="reservationForm"><select id="facilitySelect"><option value="8">Test facility</option><option value="9">Other facility</option></select><input id="arrivalTime" value="2026-10-07T08:00"><input id="departureTime" value="2026-10-07T10:00"><select id="parkingSpaceSelect" name="parkingSpaceId"><option value="">Choose</option>${selected ? `<option selected value="${selected}">Existing selection</option>` : ''}</select>${fragment}</form>`,{runScripts:'outside-only',url:'http://localhost/'});
  const w=dom.window,d=w.document;
  await new Promise(resolve => d.addEventListener('DOMContentLoaded',resolve,{once:true}));
  const root=d.querySelector('[data-slot-map]'); root.dataset.booking=String(booking); root.dataset.endpoint='/slot-map'; root.dataset.facility='8';
  const timers=new Map(); let next=0, now=0, data=initial, failed=false, pending=false, calls=[], resize;
  w.setTimeout=(fn,ms)=>{const id=++next;timers.set(id,{fn,at:now+ms});return id;};
  w.clearTimeout=id=>timers.delete(id); let poll;
  w.setInterval=fn=>{poll=fn;return 1;};w.clearInterval=()=>{};
  w.ResizeObserver=class {constructor(fn){resize=fn;} observe(){} disconnect(){}};
  w.CSS={escape:value=>String(value)};
  w.getComputedStyle=()=>({getPropertyValue:name=>name==='--bay-width'?css.match(/--bay-width:\s*(\d+px)/)[1]:'',paddingLeft:'24px',paddingRight:'24px'});
  Object.defineProperty(d.querySelector('.garage-scroll'),'clientWidth',{get:()=>width});
  w.fetch=async url=>{calls.push(url);if(pending) return new Promise(()=>{});if(failed) throw Error('offline');return {ok:true,headers:{get:()=> 'application/json'},json:async()=>({bays:data,updatedAt:'2026-10-06T10:00:00'})};};
  w.eval(script);d.dispatchEvent(new w.Event('DOMContentLoaded'));await flush();
  const q=selector=>d.querySelector(selector), all=selector=>[...d.querySelectorAll(selector)];
  return {w,d,q,all,root,calls,close:()=>w.close(),
    async refresh(nextData=data){data=nextData;q('[data-map-refresh]').click();await flush();},
    async poll(){poll();await flush();},
    fail(){failed=true;}, recover(){failed=false;}, pending(){pending=true;},
    resize(nextWidth){width=nextWidth;resize();},
    change(selector,value){q(selector).value=value;q(selector).dispatchEvent(new w.Event('change',{bubbles:true}));},
    check(selector){q(selector).checked=true;q(selector).dispatchEvent(new w.Event('change'));},
    async advance(ms){now+=ms;for(const [id,t] of [...timers])if(t.at<=now){timers.delete(id);t.fn();}await flush();}
  };
}
for(const count of [1,4,12,20,25,40,65]) test(`${count} backend spaces use bounded rows, every block has a drive aisle`,async()=>{
  const f=await fixture(Array.from({length:count},(_,i)=>bay(i+1)));
  assert.equal(f.all('[data-bay]').length,count);
  assert.equal(f.q('[data-map-floor-slots]').textContent,String(count));
  for(const row of f.all('.garage-row')) assert.ok(row.children.length<=10);
  for(const block of f.all('.garage-block')) assert.equal(block.querySelectorAll('.garage-aisle').length,1);
  assert.equal(f.all('.garage-row-facing-up').length,Math.floor(Math.ceil(count/10)/2));
  f.close();
});
test('floors, zones, counters and feature filters use the returned data',async()=>{
  const f=await fixture([bay(8,{floor:8,zone:'Solo EV',hasEVCharging:true}),bay(2,{zone:'Accessible',isAccessible:true}),bay(1)]);
  assert.deepEqual(f.all('[data-map-floor] option').map(o=>o.value),['1','8']);
  assert.equal(f.q('[data-map-floor-count]').textContent,'2');assert.equal(f.q('[data-map-total-slots]').textContent,'3');
  f.change('[data-map-floor]','8'); assert.equal(f.all('[data-bay]').length,1);
  assert.equal(f.q('[data-map-floor-slots]').textContent,'1'); f.change('[data-map-zone]','Solo EV');
  assert.equal(f.q('[data-bay]').dataset.bay,'8');assert.ok(f.q('.garage-ev-mark'));
  f.change('[data-map-floor]','1'); f.check('[data-map-accessible]');
  assert.equal(f.all('[data-bay]').length,1);assert.ok(f.q('.garage-accessible-mark'));
  assert.equal(f.q('[data-map-floor-slots]').textContent,'2');f.close();
});
test('physical occupancy alone controls cars; reserved empty and maintenance bays have no cars',async()=>{
  const f=await fixture([bay(1,{occupied:true,status:'OCCUPIED',selectable:false}),bay(2,{status:'RESERVED',selectable:false}),bay(3,{status:'MAINTENANCE',selectable:false}),bay(4,{status:'WALK_IN_ONLY',occupied:true,selectable:false})]);
  assert.deepEqual(f.all('[data-bay] .garage-car').map(e=>e.closest('[data-bay]').dataset.bay),['1','4']);
  assert.ok(f.q('[data-bay="3"] .garage-maintenance-mark'));
  const src=f.q('[data-bay="1"] img').src;await f.refresh();assert.equal(f.q('[data-bay="1"] img').src,src);
  f.q('[data-bay="2"]').click();assert.equal(f.q('[data-map-select]').disabled,true);assert.equal(f.q('[data-map-preview] img'),null);f.close();
});
test('click and short hover inspect only; selection requires the action button',async()=>{
  const f=await fixture([bay(1),bay(2)]), select=f.q('#parkingSpaceSelect');
  f.q('[data-bay="1"]').click();assert.equal(f.q('[data-map-name]').textContent,'TEST-1');assert.equal(select.value,'');
  assert.equal(f.q('[data-map-charge]').textContent,'LKR 500.00');
  f.q('[data-map-select]').click();assert.equal(select.value,'1');assert.equal(f.q('[data-map-select]').textContent,'✓ Already selected');
  const second=f.q('[data-bay="2"]');second.dispatchEvent(new f.w.Event('pointerenter'));
  await f.advance(699);assert.equal(f.q('[data-map-name]').textContent,'TEST-1');
  await f.advance(1);assert.equal(f.q('[data-map-name]').textContent,'TEST-2');assert.equal(select.value,'1');
  assert.equal(f.q('[data-map-ev-detail]').textContent,'No');assert.equal(f.q('[data-map-accessible-detail]').textContent,'No');
  assert.equal(f.q('[data-bay="2"]').getAttribute('aria-current'),'true');f.close();
});
test('polling retains separate inspection and selection; removed bays and lost availability are safe',async()=>{
  const f=await fixture([bay(1),bay(2)]);f.q('[data-bay="1"]').click();f.q('[data-map-select]').click();f.q('[data-bay="2"]').click();
  await f.poll();assert.equal(f.q('[data-map-name]').textContent,'TEST-2');assert.equal(f.q('#parkingSpaceSelect').value,'1');
  await f.refresh([bay(1,{hourlyRate:400}),bay(2)]);assert.equal(f.q('[data-map-name]').textContent,'TEST-2');
  await f.refresh([bay(2)]);assert.equal(f.q('#parkingSpaceSelect').value,'');assert.equal(f.q('[data-map-name]').textContent,'TEST-2');
  assert.match(f.q('[data-map-message]').textContent,/no longer available/);f.close();
});
test('initial edit selection opens its actual floor, filtering never changes the reservation value',async()=>{
  const f=await fixture([bay(1),bay(8,{floor:8})],{selected:'8'});
  assert.equal(f.q('[data-map-floor]').value,'8');assert.equal(f.q('[data-bay="8"]').getAttribute('aria-pressed'),'true');
  f.change('[data-map-floor]','1');assert.equal(f.q('#parkingSpaceSelect').value,'8');assert.equal(f.q('[data-map-name]').textContent,'Inspect a space');f.close();
});
test('keyboard moves focus and Enter remains native click inspection; touching does not start hover',async()=>{
  const f=await fixture([bay(1),bay(2)]);const first=f.q('[data-bay="1"]');first.focus();
  first.dispatchEvent(new f.w.KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}));assert.equal(f.d.activeElement.dataset.bay,'2');assert.equal(f.q('#parkingSpaceSelect').value,'');
  const touch=new f.w.Event('pointerenter');Object.defineProperty(touch,'pointerType',{value:'touch'});first.dispatchEvent(touch);await f.advance(2000);
  assert.equal(f.q('[data-map-name]').textContent,'Inspect a space');first.click();assert.equal(f.q('[data-map-name]').textContent,'TEST-1');f.close();
});
test('pending and failed refreshes prevent selection and form submission; recovery restores it',async()=>{
  const f=await fixture([bay(1)]);f.q('[data-bay="1"]').click();f.fail();await f.refresh();
  assert.equal(f.q('[data-map-select]').disabled,true);const submit=new f.w.Event('submit',{cancelable:true});f.q('#reservationForm').dispatchEvent(submit);assert.equal(submit.defaultPrevented,true);
  f.recover();await f.refresh();assert.equal(f.q('[data-map-select]').disabled,false);f.q('[data-map-select]').click();
  const validSubmit=new f.w.Event('submit',{cancelable:true});f.q('#reservationForm').dispatchEvent(validSubmit);assert.equal(validSubmit.defaultPrevented,false);
  assert.equal(new f.w.FormData(f.q('#reservationForm')).get('parkingSpaceId'),'1');f.close();
  const g=await fixture([bay(1)]);g.q('[data-bay="1"]').click();g.pending();g.root.refreshMap();
  assert.equal(g.q('[data-map-select]').disabled,true);g.q('[data-map-select]').click();assert.equal(g.q('#parkingSpaceSelect').value,'');g.close();
});
test('changing facility clears old bays during the request, and read-only maps use backend default dates',async()=>{
  const f=await fixture([bay(1)]);f.pending();f.change('#facilitySelect','9');f.root.refreshMap();await f.advance(100);assert.equal(f.all('[data-bay]').length,0);f.close();
  const g=await fixture([bay(1)],{booking:false});assert.equal(g.calls.length,1);assert.equal(new URL(g.calls[0],'http://localhost').searchParams.has('arrivalTime'),false);
  g.q('[data-bay="1"]').click();assert.equal(g.q('[data-map-select]').disabled,true);g.close();
});
test('container resizing rebuilds bounded rows while keeping selection and inspection',async()=>{
  const f=await fixture(Array.from({length:40},(_,i)=>bay(i+1)));f.q('[data-bay="1"]').click();f.q('[data-map-select]').click();
  f.resize(320);assert.ok(f.all('.garage-row').every(row=>row.children.length<=3));assert.equal(f.q('#parkingSpaceSelect').value,'1');assert.equal(f.q('[data-map-name]').textContent,'TEST-1');f.close();
});
