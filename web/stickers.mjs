// Return the topmost painted stroke touched by the eraser's swept circle.
function pointSegment(p,a,b){const dx=b[0]-a[0],dy=b[1]-a[1],d=dx*dx+dy*dy;const t=d?Math.max(0,Math.min(1,((p[0]-a[0])*dx+(p[1]-a[1])*dy)/d)):0;return Math.hypot(p[0]-a[0]-t*dx,p[1]-a[1]-t*dy);}
function segmentsTouch(a,b,c,d,r){
 const cross=(p,q,t)=>(q[0]-p[0])*(t[1]-p[1])-(q[1]-p[1])*(t[0]-p[0]);
 if(cross(a,b,c)*cross(a,b,d)<0&&cross(c,d,a)*cross(c,d,b)<0)return true;
 return Math.min(pointSegment(a,c,d),pointSegment(b,c,d),pointSegment(c,a,b),pointSegment(d,a,b))<=r;
}
export function hitPaintStroke(strokes,from,to,width){
 for(let i=strokes.length-1;i>=0;i--){const stroke=strokes[i];if(stroke.tool==='erase')continue;
  for(let j=0;j<stroke.points.length;j++)if(segmentsTouch(from,to,stroke.points[Math.max(0,j-1)],stroke.points[j],(width+stroke.width)/2))return i;
 }return -1;
}
// Sticker paint lives in local 600x400 coordinates, so moving/resizing carries the child's marks.
export function resizeSticker(box,corner,dx,dy) {
  const clamp=(n,min,max)=>Math.max(min,Math.min(max,n));
  const right=box.x+box.width,bottom=box.y+box.height;
  const x=corner.includes('w')?clamp(box.x+dx,0,right-20):box.x;
  const y=corner.includes('n')?clamp(box.y+dy,0,bottom-20):box.y;
  return {x,y,width:corner.includes('w')?right-x:clamp(box.width+dx,20,600-x),height:corner.includes('n')?bottom-y:clamp(box.height+dy,20,400-y)};
}
function stickerInstanceId(){
  if(typeof crypto.randomUUID==='function')return crypto.randomUUID();
  const bytes=crypto.getRandomValues(new Uint8Array(16));bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;
  const hex=Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('');
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}
export function createStickerEditor({root,getColor,getWidth,onChange,onSelect,canEdit,pagePointCount,getEraserMode=()=>'partial'}) {
  let items=[],catalog=[],selected=null,mode='paint',active=null,redo=[];
  const nodes=new Map(),histories=new Map();
  const count=()=>pagePointCount()+items.reduce((sum,i)=>sum+i.strokes.reduce((n,s)=>n+s.points.length,0),0);
  const choose=id=>{selected=id;for(const [key,node] of nodes)node.classList.toggle('selected',key===id);onSelect(items.find(i=>i.id===id)??null);};
  function layout(item,node){Object.assign(node.style,{left:`${item.x/6}%`,top:`${item.y/4}%`,width:`${item.width/6}%`,height:`${item.height/4}%`});}
  function renderPaint(item){
    const c=nodes.get(item.id)?.querySelector('canvas');if(!c)return;
    const ctx=c.getContext('2d');ctx.clearRect(0,0,600,400);
    for(const s of [...item.strokes,...(active?.kind==='paint'&&active.item.id===item.id?[active.stroke]:[])]){
      ctx.globalCompositeOperation=s.tool==='erase'?'destination-out':'source-over';ctx.strokeStyle=s.color;ctx.fillStyle=s.color;ctx.lineWidth=s.width;ctx.lineCap='round';ctx.lineJoin='round';
      ctx.beginPath();ctx.moveTo(...s.points[0]);for(const p of s.points.slice(1))ctx.lineTo(...p);
      if(s.points.length===1){ctx.arc(...s.points[0],s.width/2,0,Math.PI*2);ctx.fill();}else ctx.stroke();
    }
    ctx.globalCompositeOperation='source-over';
  }
  function local(event,node){const rect=node.getBoundingClientRect();return [Math.round(Math.max(0,Math.min(600,(event.clientX-rect.left)/rect.width*600))*10)/10,Math.round(Math.max(0,Math.min(400,(event.clientY-rect.top)/rect.height*400))*10)/10];}
  function eraseStroke(item,from,to){const i=hitPaintStroke(item.strokes,from,to,getWidth());if(i>=0){item.strokes.splice(i,1);renderPaint(item);}}
  function build(){
    root.replaceChildren();nodes.clear();
    for(const item of items){
      const asset=catalog.find(s=>s.id===item.stickerId);if(!asset)continue;
      const node=document.createElement('div');node.className='placed-sticker';node.dataset.instanceId=item.id;
      const base=document.createElement('img');base.src=asset.asset;base.alt='';base.draggable=false;
      const paint=document.createElement('canvas');paint.width=600;paint.height=400;paint.setAttribute('aria-label',`${asset.name} 색칠`);
      const outline=document.createElement('img');outline.src=asset.asset.replace('.svg','-outline.svg');outline.alt=asset.name;outline.draggable=false;
      node.append(base,paint,outline);root.append(node);nodes.set(item.id,node);layout(item,node);renderPaint(item);
      const remove=document.createElement('button');remove.type='button';remove.className='sticker-delete';remove.textContent='×';remove.setAttribute('aria-label',`${asset.name} 스티커 삭제`);
      remove.onpointerdown=event=>event.stopPropagation();
      remove.onclick=event=>{event.stopPropagation();if(mode!=='move'||selected!==item.id||!canEdit()||active)return;items=items.filter(i=>i.id!==item.id);selected=null;redo=[];build();onChange();};
      node.append(remove);
      for(const [corner,label] of [['nw','왼쪽 위'],['ne','오른쪽 위'],['sw','왼쪽 아래'],['se','오른쪽 아래']]){
        const handle=document.createElement('button');handle.className=`resize-handle ${corner}`;handle.dataset.corner=corner;
        handle.setAttribute('aria-label',`${asset.name} ${label} 크기 조절`);node.append(handle);
      }
      node.onpointerdown=event=>{
        if(!canEdit()||active||event.button!==0)return;choose(item.id);event.stopPropagation();event.preventDefault();
        const corner=event.target.closest('[data-corner]')?.dataset.corner;
        if(corner)active={kind:'resize',item,corner,box:{x:item.x,y:item.y,width:item.width,height:item.height},start:[event.clientX,event.clientY],pointerId:event.pointerId};
        else if(mode==='move')active={kind:'move',item,x:item.x,y:item.y,start:[event.clientX,event.clientY],pointerId:event.pointerId};
        else if(mode==='erase'&&getEraserMode()==='stroke'){
          active={kind:'strokeErase',item,before:structuredClone(item.strokes),last:local(event,node),pointerId:event.pointerId};
          eraseStroke(item,active.last,active.last);
        }else{
          if(item.strokes.length>=1000||count()>=20000){onSelect(item,'획이 너무 많아요. 일부 획을 취소해주세요.');return;}
          active={kind:'paint',item,before:structuredClone(item.strokes),stroke:{tool:mode==='erase'?'erase':'paint',color:getColor(),width:getWidth(),points:[local(event,node)]},pointerId:event.pointerId};
        }
        node.setPointerCapture(event.pointerId);renderPaint(item);
      };
      node.onpointermove=event=>{
        if(!active||active.pointerId!==event.pointerId)return;
        if(active.kind==='strokeErase'){const next=local(event,node);eraseStroke(item,active.last,next);active.last=next;
        }else if(active.kind==='resize'){
          const rect=root.getBoundingClientRect();Object.assign(item,resizeSticker(active.box,active.corner,(event.clientX-active.start[0])*600/rect.width,(event.clientY-active.start[1])*400/rect.height));layout(item,node);onSelect(item);
        }else if(active.kind==='move'){
          const rect=root.getBoundingClientRect();item.x=Math.round(Math.max(0,Math.min(600-item.width,active.x+(event.clientX-active.start[0])*600/rect.width))*10)/10;
          item.y=Math.round(Math.max(0,Math.min(400-item.height,active.y+(event.clientY-active.start[1])*400/rect.height))*10)/10;layout(item,node);onSelect(item);
        }else{
          const p=local(event,node),last=active.stroke.points.at(-1);
          if(active.stroke.points.length<2000&&count()+active.stroke.points.length<20000&&Math.hypot(p[0]-last[0],p[1]-last[1])>=1)active.stroke.points.push(p);
          renderPaint(item);
        }
      };
      const finish=event=>{
        if(!active||active.pointerId!==event.pointerId)return;
        if(active.kind==='paint')item.strokes.push(active.stroke);
        if(active.before&&JSON.stringify(active.before)!==JSON.stringify(item.strokes)){const history=histories.get(item.id)??[];history.push(active.before);if(history.length>30)history.shift();histories.set(item.id,history);}
        active=null;redo=[];renderPaint(item);onChange();onSelect(item);
      };
      node.onpointerup=finish;node.onpointercancel=finish;node.onlostpointercapture=finish;
    }
    root.dataset.mode=mode;choose(selected);
  }
  return {
    setCatalog(value){catalog=value;},
    setPlacements(value){histories.clear();active=null;items=structuredClone(value??[]);selected=null;redo=[];build();},
    placements(){return structuredClone(items);},
    pointCount(){return items.reduce((sum,i)=>sum+i.strokes.reduce((n,s)=>n+s.points.length,0),0);},
    isActive(){return !!active;},
    select:choose,
    mode(value){mode=value;root.dataset.mode=value;},
    add(id){
      if(!canEdit()||items.length>=100)return;
      const asset=catalog.find(s=>s.id===id&&s.unlocked);if(!asset)return;
      const item={id:stickerInstanceId(),stickerId:id,x:220,y:120,width:140,height:140,strokes:[]};
      items.push(item);selected=item.id;mode='move';build();onChange();
    },
    remove(){if(!selected||active)return;items=items.filter(i=>i.id!==selected);selected=null;build();onChange();},
    undo(){const item=items.find(i=>i.id===selected);if(!item)return false;const history=histories.get(item.id);if(history?.length){redo.push({id:item.id,strokes:structuredClone(item.strokes)});item.strokes=history.pop();renderPaint(item);onChange();}else if(item.strokes.length){redo.push({id:item.id,strokes:structuredClone(item.strokes)});item.strokes.pop();renderPaint(item);onChange();}return true;},
    redo(){const item=items.find(i=>i.id===selected);if(!item)return false;const last=redo.at(-1);if(last?.id===item.id){const history=histories.get(item.id)??[];history.push(structuredClone(item.strokes));histories.set(item.id,history);item.strokes=redo.pop().strokes;renderPaint(item);onChange();}return true;},
  };
}
