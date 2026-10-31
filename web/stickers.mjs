// Sticker paint lives in local 600x400 coordinates, so moving/resizing carries the child's marks.
export function createStickerEditor({root,getColor,getWidth,onChange,onSelect,canEdit,pagePointCount}) {
  let items=[],catalog=[],selected=null,mode='paint',active=null,redo=[];
  const nodes=new Map();
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
  function build(){
    root.replaceChildren();nodes.clear();
    for(const item of items){
      const asset=catalog.find(s=>s.id===item.stickerId);if(!asset)continue;
      const node=document.createElement('div');node.className='placed-sticker';node.dataset.instanceId=item.id;
      const base=document.createElement('img');base.src=asset.asset;base.alt='';base.draggable=false;
      const paint=document.createElement('canvas');paint.width=600;paint.height=400;paint.setAttribute('aria-label',`${asset.name} 색칠`);
      const outline=document.createElement('img');outline.src=asset.asset.replace('.svg','-outline.svg');outline.alt=asset.name;outline.draggable=false;
      node.append(base,paint,outline);root.append(node);nodes.set(item.id,node);layout(item,node);renderPaint(item);
      node.onpointerdown=event=>{
        if(!canEdit()||active||event.button!==0)return;choose(item.id);event.stopPropagation();event.preventDefault();
        if(mode==='move')active={kind:'move',item,x:item.x,y:item.y,start:[event.clientX,event.clientY],pointerId:event.pointerId};
        else{
          if(item.strokes.length>=1000||count()>=20000){onSelect(item,'획이 너무 많아요. 일부 획을 취소해주세요.');return;}
          active={kind:'paint',item,stroke:{tool:mode==='erase'?'erase':'paint',color:getColor(),width:getWidth(),points:[local(event,node)]},pointerId:event.pointerId};
        }
        node.setPointerCapture(event.pointerId);renderPaint(item);
      };
      node.onpointermove=event=>{
        if(!active||active.pointerId!==event.pointerId)return;
        if(active.kind==='move'){
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
        active=null;redo=[];renderPaint(item);onChange();onSelect(item);
      };
      node.onpointerup=finish;node.onpointercancel=finish;node.onlostpointercapture=finish;
    }
    root.dataset.mode=mode;choose(selected);
  }
  return {
    setCatalog(value){catalog=value;},
    setPlacements(value){active=null;items=structuredClone(value??[]);selected=null;redo=[];build();},
    placements(){return structuredClone(items);},
    pointCount(){return items.reduce((sum,i)=>sum+i.strokes.reduce((n,s)=>n+s.points.length,0),0);},
    isActive(){return !!active;},
    select:choose,
    mode(value){mode=value;root.dataset.mode=value;},
    add(id){
      if(!canEdit()||items.length>=100)return;
      const asset=catalog.find(s=>s.id===id&&s.unlocked);if(!asset)return;
      const item={id:crypto.randomUUID(),stickerId:id,x:220,y:120,width:140,height:140,strokes:[]};
      items.push(item);selected=item.id;mode='move';build();onChange();
    },
    resize(width,height){
      const item=items.find(i=>i.id===selected);if(!item||active)return;
      item.width=Math.max(20,Math.min(360,width));item.height=Math.max(20,Math.min(360,height));
      item.x=Math.min(item.x,600-item.width);item.y=Math.min(item.y,400-item.height);layout(item,nodes.get(item.id));onChange();onSelect(item);
    },
    remove(){if(!selected||active)return;items=items.filter(i=>i.id!==selected);selected=null;build();onChange();},
    undo(){const item=items.find(i=>i.id===selected);if(!item)return false;if(item.strokes.length){redo.push({id:item.id,stroke:item.strokes.pop()});renderPaint(item);onChange();}return true;},
    redo(){const item=items.find(i=>i.id===selected);if(!item)return false;const last=redo.at(-1);if(last?.id===item.id){item.strokes.push(redo.pop().stroke);renderPaint(item);onChange();}return true;},
  };
}
