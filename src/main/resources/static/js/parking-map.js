/* Presentation only. SlotMapService owns availability and reservation validation. */
(() => {
  'use strict';
  const labels = {AVAILABLE:'Available', OCCUPIED:'Occupied', RESERVED:'Reserved', MAINTENANCE:'Maintenance', OUT_OF_SERVICE:'Out of service', ONLINE_ONLY:'Online only', WALK_IN_ONLY:'Walk-in only'};
  const vehicles = ['sedan', 'suv', 'compact', 'hatchback'];
  const money = value => 'LKR ' + Number(value || 0).toLocaleString('en-LK', {minimumFractionDigits:2, maximumFractionDigits:2});
  const humanize = value => value ? String(value).toLowerCase().replaceAll('_', ' ').replace(/^./, c => c.toUpperCase()) : '—';
  const compare = (a,b) => String(a ?? '').localeCompare(String(b ?? ''), undefined, {numeric:true});
  const ordered = list => [...list].sort((a,b) => compare(a.floor,b.floor) || compare(a.zone,b.zone) || compare(a.spaceNumber,b.spaceNumber) || compare(a.id,b.id));
  const identity = bay => String(bay.id);
  const hash = value => [...String(value)].reduce((n,c) => (Math.imul(n,31) + c.charCodeAt(0)) >>> 0,0);
  function svg(markup) {
    const element = document.createElementNS('http://www.w3.org/2000/svg','svg');
    element.setAttribute('viewBox','0 0 64 64'); element.setAttribute('aria-hidden','true');
    element.innerHTML = markup; // Constant artwork only; backend strings always use textContent.
    return element;
  }
  function init(root) {
    const q = selector => root.querySelector(selector), booking = root.dataset.booking === 'true', walkIn = root.dataset.walkInMap === 'true', allFloors = root.dataset.allFloors === 'true';
    const canSelect = booking || walkIn;
    const select = booking ? document.getElementById('parkingSpaceSelect') : walkIn ? document.getElementById('spaceSelect') : null;
    const scroll = q('.garage-scroll'), canvas = q('[data-map-bays]');
    let bays = [], selected = select?.value || '', inspected = selected, fresh = false;
    let controller, request = 0, hoverTimer, hoverTarget, debounce, lastFacility, lastSnapshot = '', lastCapacity = 0;
    const text = (selector,value) => { const element = q(selector); if(element) element.textContent = value; };
    const byId = id => bays.find(bay => identity(bay) === id);
    const floor = () => q('[data-map-floor]').value;
    const visible = () => bays.filter(bay => ((allFloors && floor() === '') || String(bay.floor ?? '') === floor())
      && (!q('[data-map-zone]').value || bay.zone === q('[data-map-zone]').value)
      && (!q('[data-map-ev]').checked || bay.hasEVCharging)
      && (!q('[data-map-accessible]').checked || bay.isAccessible));
    const hours = () => {
      if(walkIn) return Number(document.getElementById('durationHoursInput')?.value || 2);
      const arrival = document.getElementById('arrivalTime')?.value, departure = document.getElementById('departureTime')?.value;
      const duration = arrival && departure ? (new Date(departure)-new Date(arrival))/3600000 : 0;
      return duration > 0 ? duration : 0;
    };
    const release = bay => {
      if(!bay.occupied) return 'Not occupied now';
      if(!bay.estimatedDeparture) return 'Time unavailable';
      const date = new Date(bay.estimatedDeparture);
      return date <= new Date() ? 'Overdue · awaiting checkout' : date.toLocaleString('en-LK',{month:'short',day:'numeric',hour:'numeric',minute:'2-digit'});
    };
    // Reused for the detail preview. Reservation status alone never adds a vehicle.
    function surface(bay) {
      const element = document.createElement('span'); element.className = 'garage-surface';
      if(bay.isAccessible) {
        const mark = svg('<circle cx="30" cy="12" r="5" fill="currentColor"/><path d="M28 22v19h16l8 14 6-3M28 29h16M21 32a17 17 0 1 0 19 23" fill="none" stroke="currentColor" stroke-width="5"/>');
        mark.classList.add('garage-floor-symbol','garage-accessible-mark'); element.append(mark);
      }
      if(bay.hasEVCharging) {
        const mark = svg('<path d="M36 4 15 36h16l-4 24 23-36H34Z" fill="currentColor"/>');
        mark.classList.add('garage-floor-symbol','garage-ev-mark'); element.append(mark);
        const charger = document.createElement('span'); charger.className = 'garage-charger'; element.append(charger);
      }
      if(['MAINTENANCE','OUT_OF_SERVICE'].includes(bay.status)) {
        const mark = document.createElement('span'); mark.className = 'garage-maintenance-mark'; mark.textContent = '×'; element.append(mark);
      }
      if(bay.occupied === true) {
        const car = document.createElement('img'); car.className = 'garage-car'; car.alt = ''; car.setAttribute('aria-hidden','true');
        car.src = '/images/parking/vehicles/' + vehicles[hash(bay.id) % vehicles.length] + '-top.svg'; element.append(car);
      }
      return element;
    }
    function detail() {
      const bay = byId(inspected), button = q('[data-map-select]'), edit = q('[data-map-edit]'), preview = q('[data-map-preview]');
      edit.hidden = !(root.dataset.adminMap === 'true' && bay);
      if(!edit.hidden) edit.href = '/admin/parking-space/edit/' + encodeURIComponent(bay.id);
      preview.replaceChildren(); preview.hidden = !bay;
      q('[data-map-previous]').disabled = q('[data-map-next]').disabled = visible().length < 2;
      if(!bay) {
        text('[data-map-name]','Inspect a space'); text('[data-map-state]','Hover for 2 seconds, click, or press Enter on a bay.');
        ['rate','charge','location','ev-detail','accessible-detail','type','allocation','estimate'].forEach(key => text('[data-map-'+key+']','—'));
        text('[data-map-status]','—'); q('[data-map-status]').className = 'garage-detail-status';
        text('[data-map-note]','Inspection does not select a space.');
        button.disabled = true; button.textContent = canSelect ? 'Select this Space' : 'Viewing availability'; return;
      }
      const isSelected = identity(bay) === selected;
      text('[data-map-name]',bay.spaceNumber);
      const status = q('[data-map-status]'); status.textContent = labels[bay.status] || humanize(bay.status);
      status.className = 'garage-detail-status ' + String(bay.status).toLowerCase();
      text('[data-map-state]',isSelected ? 'Selected for your visit' : !canSelect ? 'Live space information' : bay.selectable ? 'Available for your chosen time window.' : 'Unavailable for your chosen time window.');
      const sample = document.createElement('div'); sample.className = 'garage-bay garage-preview-bay ' + String(bay.status).toLowerCase();
      sample.dataset.ev = bay.hasEVCharging; sample.dataset.accessible = bay.isAccessible; sample.append(surface(bay)); preview.append(sample);
      const rate = Number(bay.hourlyRate || 0) * (walkIn ? 1.10 : 1);
      text('[data-map-rate]',money(rate)+' / hour');
      text('[data-map-charge]',canSelect && hours() ? money(rate*hours()) : 'Choose a time window');
      text('[data-map-location]',`Level ${bay.floor ?? '—'}${bay.zone ? ' · '+bay.zone : ''}`);
      text('[data-map-ev-detail]',bay.hasEVCharging ? 'Yes' : 'No'); text('[data-map-accessible-detail]',bay.isAccessible ? 'Yes' : 'No');
      text('[data-map-type]',humanize(bay.parkingType)); text('[data-map-allocation]',humanize(bay.allocation)); text('[data-map-estimate]',release(bay));
      text('[data-map-note]',walkIn ? 'Estimated walk-in charge includes the 10% gate premium. Availability is checked again when issuing the pass.'
        : 'Parking estimate excludes extras and discounts. Availability is verified at confirmation.'+(bay.occupied ? ' Release remains subject to checkout.' : ''));
      button.disabled = !canSelect || !fresh || !bay.selectable || isSelected;
      button.textContent = !canSelect ? 'Viewing availability' : isSelected ? 'Already selected' : !bay.selectable ? 'Unavailable for this visit' : 'Select this Space';
    }
    function updateInspection() {
      canvas.querySelectorAll('[data-bay]').forEach(button => {
        const bay = byId(button.dataset.bay), isSelected = button.dataset.bay === selected, isInspected = button.dataset.bay === inspected;
        if(!bay) return; // A new snapshot may remove bays before the layout is rebuilt.
        button.classList.toggle('is-selected',isSelected); button.classList.toggle('is-inspected',isInspected);
        button.setAttribute('aria-pressed',String(isSelected)); button.setAttribute('aria-current',String(isInspected));
        button.setAttribute('aria-label',[`Space ${bay.spaceNumber}`,labels[bay.status] || humanize(bay.status),`Level ${bay.floor}`,bay.zone,
          bay.occupied === true && 'Physically occupied',bay.hasEVCharging && 'EV charging',bay.isAccessible && 'Wheelchair accessible',isSelected && 'Selected',isInspected && 'Inspected'].filter(Boolean).join('. '));
      }); detail();
    }
    function stopHover() { clearTimeout(hoverTimer); hoverTarget?.classList.remove('is-hovering'); hoverTarget = null; }
    function inspect(id) { stopHover(); inspected = String(id); updateInspection(); }
    function choose() {
      const bay = byId(inspected); if(!canSelect || !fresh || !bay?.selectable || !select) return;
      select.value = identity(bay); select.dispatchEvent(new Event('change',{bubbles:true}));
    }
    function bayButton(bay) {
      const button = document.createElement('button'); button.type = 'button'; button.dataset.bay = bay.id;
      button.dataset.ev = bay.hasEVCharging; button.dataset.accessible = bay.isAccessible; button.dataset.occupied = bay.occupied === true;
      button.className = 'garage-bay ' + String(bay.status).toLowerCase(); button.append(surface(bay));
      const label = document.createElement('strong'); label.textContent = bay.spaceNumber;
      const marker = document.createElement('span'); marker.className = 'garage-status-dot'; marker.setAttribute('aria-hidden','true');
      const progress = document.createElement('span'); progress.className = 'garage-hover-progress'; button.append(marker,label,progress);
      button.addEventListener('click',() => { inspect(bay.id); if(walkIn) choose(); });
      button.addEventListener('pointerenter',event => {
        if(event.pointerType === 'touch') return;
        stopHover(); hoverTarget = button; button.classList.add('is-hovering'); hoverTimer = setTimeout(() => inspect(bay.id),700);
      });
      button.addEventListener('pointerleave',stopHover); button.addEventListener('blur',stopHover); return button;
    }
    function capacity() {
      const style = getComputedStyle(canvas), width = parseFloat(style.getPropertyValue('--bay-width'));
      const side = parseFloat(style.paddingLeft)+parseFloat(style.paddingRight);
      return Math.max(1,Math.floor((Math.max(scroll.clientWidth,300)-side-2)/width));
    }
    function aisle() {
      const element = document.createElement('div'); element.className = 'garage-aisle'; element.setAttribute('aria-hidden','true');
      element.innerHTML = '<span class="garage-arrow"></span><span class="garage-arrow"></span>'; return element;
    }
    function row(list,facing) {
      const element = document.createElement('div'); element.className = 'garage-row garage-row-facing-'+facing;
      list.forEach(bay => element.append(bayButton(bay))); return element;
    }
    function render() {
      stopHover();
      const active = document.activeElement?.dataset.bay, list = visible(), perRow = capacity(); lastCapacity = perRow;
      if(inspected && bays.length && !list.some(bay => identity(bay) === inspected)) inspected = '';
      canvas.replaceChildren();
      if(!list.length) {
        const empty = document.createElement('p'); empty.className = 'garage-empty';
        empty.textContent = bays.length ? 'No spaces match these filters.' : (allFloors || walkIn)
          ? 'No parking spaces are configured for this facility.' : 'Choose a facility and time window to view live spaces.'; canvas.append(empty);
      }
      // The map heading reflects the selected filters; bays remain sorted by level and zone.
      if(list.length) {
        const zone = q('[data-map-zone]').value || 'All Zones';
        const level = allFloors && floor() === '' ? 'All levels' : (floor() ? 'Level ' + floor() : 'Unassigned level');
        const section = document.createElement('section'); section.className = 'garage-zone';
        const heading = document.createElement('h4'); heading.className = 'garage-zone-label'; heading.textContent = level + ' · ' + zone; section.append(heading);
        for(let offset=0; offset<list.length; offset+=perRow*2) {
          const block = document.createElement('div'); block.className = 'garage-block';
          const pair = list.slice(offset,offset+perRow*2);
          const split = pair.length > perRow ? Math.ceil(pair.length/2) : pair.length;
          block.append(row(pair.slice(0,split),'down'),aisle());
          if(split < pair.length) block.append(row(pair.slice(split),'up'));
          section.append(block);
        }
        canvas.append(section);
      }
      text('[data-map-floor-count]',new Set(bays.map(bay => bay.floor)).size);
      text('[data-map-floor-slots-label]',allFloors && floor() === '' ? 'Slots on all levels' : 'Slots on this floor');
      text('[data-map-floor-slots]',allFloors && floor() === '' ? bays.length : bays.filter(bay => String(bay.floor ?? '') === floor()).length); text('[data-map-total-slots]',bays.length);
      text('[data-map-totals]',`${list.length} shown · ${list.filter(bay => bay.occupied === true).length} occupied · ${list.filter(bay => bay.selectable).length} selectable`);
      updateInspection(); if(active) canvas.querySelector(`[data-bay="${CSS.escape(active)}"]`)?.focus({preventScroll:true});
    }
    function zones() {
      const control = q('[data-map-zone]'), before = control.value;
      const values = [...new Set(bays.filter(bay => (allFloors && floor() === '') || String(bay.floor ?? '') === floor()).map(bay => bay.zone).filter(Boolean))].sort(compare);
      control.replaceChildren(new Option('All zones','')); values.forEach(value => control.add(new Option(value,value))); control.value = values.includes(before) ? before : '';
    }
    function controls(initial) {
      const control = q('[data-map-floor]'), before = initial && byId(selected) ? String(byId(selected).floor) : control.value;
      const values = [...new Set(bays.map(bay => String(bay.floor ?? '')))].sort(compare);
      control.replaceChildren(); if(allFloors) control.add(new Option('All levels',''));
      values.forEach(value => control.add(new Option(value ? 'Level '+value : 'Unassigned level',value)));
      const keepSelection = (allFloors && before === '') || values.includes(before);
      control.value = keepSelection ? before : (allFloors ? '' : values[0] ?? ''); zones();
    }
    function navigate(delta) {
      const list = visible(); if(!list.length) return;
      const index = list.findIndex(bay => identity(bay) === inspected); inspect(list[(index+delta+list.length)%list.length].id);
    }
    function clearData() {
      bays = []; inspected = ''; lastSnapshot = ''; selected = '';
      if(select) { select.replaceChildren(new Option('Select a parking space','')); select.dispatchEvent(new Event('change',{bubbles:true})); }
      controls(false); render();
    }
    async function refresh() {
      const token = ++request; controller?.abort(); controller = new AbortController();
      const facility = booking ? document.getElementById('facilitySelect')?.value : root.dataset.facility;
      const arrival = booking ? document.getElementById('arrivalTime')?.value : null, departure = booking ? document.getElementById('departureTime')?.value : null;
      fresh = false; detail();
      if(lastFacility !== undefined && facility !== lastFacility) clearData(); lastFacility = facility;
      if(!facility || (booking && (!arrival || !departure))) {
        clearData(); text('[data-map-live]','Choose your visit'); text('[data-map-message]','Choose a facility, arrival time, and duration to explore spaces.'); return;
      }
      const params = new URLSearchParams({facilityId:facility});
      if(booking) { params.set('arrivalTime',arrival); params.set('departureTime',departure); }
      if(walkIn) params.set('durationHours',String(hours()));
      if(booking && root.dataset.exclude) params.set('excludeReservationId',root.dataset.exclude);
      try {
        const response = await fetch(root.dataset.endpoint+'?'+params,{signal:controller.signal,cache:'no-store',headers:{Accept:'application/json'}});
        if(!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw Error('Unavailable');
        const data = await response.json(); if(token !== request) return; if(!Array.isArray(data.bays)) throw Error('Invalid snapshot');
        const signature = JSON.stringify(data.bays), initial = !lastSnapshot, changed = signature !== lastSnapshot;
        bays = ordered(data.bays); fresh = true;
        const old = selected, oldRate = select?.selectedOptions[0]?.dataset.rate;
        if(select) {
          select.replaceChildren(new Option('Select a parking space',''));
          bays.filter(bay => bay.selectable).forEach(bay => {
            const option = new Option(`Space ${bay.spaceNumber} · Level ${bay.floor} · ${money(bay.hourlyRate)}/hr`,bay.id);
            option.dataset.rate = bay.hourlyRate; option.dataset.facilityId = facility; select.add(option);
          });
          if(!byId(selected)?.selectable) selected = '';
          select.value = selected; select.setCustomValidity('');
          // Polling must not reset inspection to the selected space.
          if(old !== selected || oldRate !== select.selectedOptions[0]?.dataset.rate) select.dispatchEvent(new Event('change',{bubbles:true}));
          const count = document.getElementById('bayCountText'); if(count) count.textContent = bays.filter(bay => bay.selectable).length+' spaces available';
        }
        if(inspected && !byId(inspected)) inspected = '';
        text('[data-map-message]',old && !selected ? 'Your selected space is no longer available. Please choose another.' : walkIn
          ? 'Live availability refreshes every 10 seconds. Click an available bay to assign it.'
          : booking ? 'Live availability refreshes every 10 seconds. Inspect a bay, then use Select this Space to choose it.'
          : 'Live space status refreshes every 10 seconds. Select a bay to inspect its details.');
        text('[data-map-live]','Updated '+new Date(data.updatedAt || Date.now()).toLocaleTimeString('en-LK',{hour:'2-digit',minute:'2-digit'}));
        q('[data-map-live]').classList.remove('is-stale');
        if(changed) { controls(initial); render(); } else updateInspection(); lastSnapshot = signature;
      } catch(error) {
        if(error.name === 'AbortError' || token !== request) return;
        fresh = false; detail(); text('[data-map-live]','Connection interrupted'); q('[data-map-live]').classList.add('is-stale');
        text('[data-map-message]','Availability could not be refreshed. Press Refresh before selecting a space.'); select?.setCustomValidity('Refresh parking availability before continuing.');
      }
    }
    root.refreshMap = () => { fresh = false; ++request; controller?.abort(); stopHover(); clearTimeout(debounce); detail(); debounce = setTimeout(refresh,100); };
    q('[data-map-floor]').addEventListener('change',() => { zones(); render(); });
    ['zone','ev','accessible'].forEach(key => q('[data-map-'+key+']').addEventListener('change',render));
    q('[data-map-refresh]').addEventListener('click',refresh); q('[data-map-select]').addEventListener('click',choose);
    q('[data-map-previous]').addEventListener('click',() => navigate(-1)); q('[data-map-next]').addEventListener('click',() => navigate(1));
    select?.addEventListener('change',() => { selected = select.value; updateInspection(); });
    select?.form?.addEventListener('submit',event => { if(!fresh) { event.preventDefault(); select.setCustomValidity('Refresh parking availability before continuing.'); select.reportValidity(); } });
    canvas.addEventListener('keydown',event => {
      if(!event.target.dataset.bay || !['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Home','End'].includes(event.key)) return;
      const buttons = [...canvas.querySelectorAll('[data-bay]')], index = buttons.indexOf(event.target);
      event.preventDefault();
      if(event.key === 'ArrowUp' || event.key === 'ArrowDown') {
        const rows = [...canvas.querySelectorAll('.garage-row')], current = event.target.closest('.garage-row');
        const target = rows[rows.indexOf(current)+(event.key === 'ArrowUp' ? -1 : 1)];
        const column = [...current.children].indexOf(event.target);
        target?.children[Math.min(column,target.children.length-1)]?.focus();
      } else {
        const next = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length-1 : index+(event.key === 'ArrowLeft' ? -1 : 1);
        buttons[Math.max(0,Math.min(buttons.length-1,next))]?.focus();
      }
    });
    const observer = new ResizeObserver(() => { if(capacity() !== lastCapacity) render(); }); observer.observe(scroll);
    refresh(); const poll = setInterval(() => { if(!document.hidden) refresh(); },10000);
    const visibility = () => { stopHover(); if(!document.hidden) root.refreshMap(); }; document.addEventListener('visibilitychange',visibility);
    window.addEventListener('pagehide',() => { clearInterval(poll); clearTimeout(debounce); stopHover(); controller?.abort(); observer.disconnect(); document.removeEventListener('visibilitychange',visibility); },{once:true});
  }
  document.addEventListener('DOMContentLoaded',() => document.querySelectorAll('[data-slot-map]').forEach(init));
})();
