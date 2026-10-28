import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import {randomUUID,randomBytes,timingSafeEqual} from 'node:crypto';
import {fileURLToPath,pathToFileURL} from 'node:url';

const root=path.dirname(fileURLToPath(import.meta.url));
const escape=value=>String(value??'').replace(/[&<>"']/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const money=cents=>new Intl.NumberFormat('es-PE',{style:'currency',currency:'PEN'}).format(cents/100);
const normalize=value=>String(value).normalize('NFD').replace(/\p{M}/gu,'').toLowerCase().replace(/(\d+)\s+(ml|kg|g|l)\b/g,'$1$2').replace(/[^a-z0-9]+/g,' ').trim();
const keywords=value=>normalize(value).split(/\s+/).filter(word=>word.length>1&&!['de','del','con','sin','por','las','los','una','un'].includes(word));
const json=value=>JSON.stringify(value).replace(/</g,'\\u003c');

export function parsePrice(value){
 const text=String(value??'').trim().replace(',','.');
 if(!/^\d{1,7}(?:\.\d{1,2})?$/.test(text))throw new Error('Ingresa un precio válido, con hasta dos decimales.');
 const [whole,fraction='']=text.split('.');const cents=Number(whole)*100+Number(fraction.padEnd(2,'0'));
 if(cents<=0)throw new Error('El precio debe ser mayor que cero.');return cents;
}
function productFrom(form,id){
 const field=(name,label,max=160)=>{const value=String(form.get(name)??'').trim();if(!value||value.length>max)throw new Error(`Completa ${label} (máximo ${max} caracteres).`);return value;};
 return {id,name:field('name','el nombre'),brand:field('brand','la marca',80),presentation:field('presentation','la presentación',80),capacity:field('capacity','la capacidad',80),priceCents:parsePrice(form.get('price')),updatedAt:new Date().toISOString()};
}
function seed(){return [{id:randomUUID(),name:'Agua CIELO Sin Gas Bidón 7L',brand:'CIELO',presentation:'Bidón',capacity:'7 L',priceCents:490,updatedAt:new Date().toISOString()}];}
function readProducts(file){
 if(!fs.existsSync(file))return null;const value=JSON.parse(fs.readFileSync(file,'utf8'));
 if(!Array.isArray(value)||value.some(p=>!p.id||!p.name||!Number.isSafeInteger(p.priceCents)||p.priceCents<=0))throw new Error('El archivo de productos está dañado. Se conservó sin sobrescribir.');return value;
}
function saveProducts(file,products){fs.mkdirSync(path.dirname(file),{recursive:true});const temp=file+'.tmp';fs.writeFileSync(temp,JSON.stringify(products,null,2),'utf8');fs.renameSync(temp,file);}
function layout(title,content,admin=false){return `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escape(title)} · Tienda de pruebas</title><link rel="stylesheet" href="/style.css"></head><body><header><a class="brand" href="/">mi<span>tienda</span><small>Catálogo de pruebas</small></a><nav><a ${admin?'':'aria-current="page"'} href="/">Tienda</a><a ${admin?'aria-current="page"':''} href="/admin">Administrar productos</a></nav><span class="badge">Solo pruebas locales</span></header><main>${content}</main><footer>Productos y precios controlados para probar el monitoreo. Sin pagos ni pedidos.</footer></body></html>`;}
function banner(url){const message=url.searchParams.get('ok');return ['creado','actualizado','eliminado'].includes(message)?`<p class="notice" role="status">Producto ${message} correctamente.</p>`:'';}
function illustration(product){return `<div class="illustration" aria-hidden="true"><span>${escape(product.brand)}</span><strong>${escape(product.capacity)}</strong><small>${escape(product.presentation)}</small></div>`;}
function card(product){return `<article class="product-card"><a class="product-picture" href="/producto/${product.id}">${illustration(product)}</a><div class="product-info"><p class="eyebrow">${escape(product.brand)}</p><h2><a href="/producto/${product.id}">${escape(product.name)}</a></h2><p class="muted">${escape(product.presentation)} · ${escape(product.capacity)}</p><strong class="price">${money(product.priceCents)}</strong><a class="button secondary" href="/producto/${product.id}">Ver producto</a></div></article>`;}
function storefront(products,url){
 const query=url.searchParams.get('q')??'';const words=keywords(query);const ranked=products.map(product=>({product,score:words.filter(word=>(' '+normalize(product.name+' '+product.brand+' '+product.presentation+' '+product.capacity)+' ').includes(' '+word+' ')).length})).filter(item=>!words.length||item.score>=Math.min(2,words.length)).sort((a,b)=>b.score-a.score);return layout('Productos',`<section class="hero"><div><p class="eyebrow">CATÁLOGO LOCAL</p><h1>Tu tienda para probar precios</h1><p>Crea un producto, cambia su precio y comprueba la alerta en tu monitor.</p></div><a class="button primary" href="/admin/productos/nuevo">Crear producto</a></section><form class="search" action="/buscar" method="get"><label for="search">Buscar productos</label><div><input id="search" type="search" name="q" value="${escape(query)}" placeholder="Nombre, marca o presentación"><button type="submit">Buscar</button></div></form><div class="section-heading"><h2>${query?'Resultados de búsqueda':'Productos disponibles'}</h2><span>${ranked.length} producto${ranked.length===1?'':'s'}</span></div>${ranked.length?`<section class="products">${ranked.map(({product})=>card(product)).join('')}</section>`:'<section class="empty"><h2>No hay productos para mostrar</h2><p>Crea un producto o prueba otra búsqueda.</p><a class="button secondary" href="/admin/productos/nuevo">Crear producto</a></section>'}`);}
function detail(product,origin){const url=`${origin}/producto/${product.id}`;const ld={'@context':'https://schema.org','@type':'Product',name:product.name,sku:product.id,brand:{'@type':'Brand',name:product.brand},description:`${product.presentation} · ${product.capacity}`,offers:{'@type':'Offer',price:(product.priceCents/100).toFixed(2),priceCurrency:'PEN',availability:'https://schema.org/InStock',url}};return layout(product.name,`<a class="back" href="/">← Volver al catálogo</a><section class="product-detail"><div class="detail-picture">${illustration(product)}</div><div><p class="eyebrow">${escape(product.brand)}</p><h1>${escape(product.name)}</h1><p class="muted">${escape(product.presentation)} · ${escape(product.capacity)}</p><strong class="price large">${money(product.priceCents)}</strong><p class="stock">Disponible para pruebas</p><dl><dt>Marca</dt><dd>${escape(product.brand)}</dd><dt>Presentación</dt><dd>${escape(product.presentation)}</dd><dt>Capacidad</dt><dd>${escape(product.capacity)}</dd><dt>Moneda</dt><dd>PEN · soles</dd></dl><a class="button secondary" href="/admin/productos/${product.id}/editar">Editar producto</a></div></section><script type="application/ld+json">${json(ld)}</script>`);}
function adminList(products,url){return layout('Administrar productos',`${banner(url)}<div class="section-heading"><div><p class="eyebrow">ADMINISTRACIÓN</p><h1>Productos</h1><p class="muted">Cambia precios para probar los comparativos y las alertas.</p></div><a class="button primary" href="/admin/productos/nuevo">Crear producto</a></div>${products.length?`<div class="table-wrap"><table><thead><tr><th>Producto</th><th>Presentación</th><th>Precio</th><th>Acciones</th></tr></thead><tbody>${products.map(p=>`<tr><td><a href="/producto/${p.id}">${escape(p.name)}</a><small>${escape(p.brand)}</small></td><td>${escape(p.presentation)} · ${escape(p.capacity)}</td><td class="price">${money(p.priceCents)}</td><td><div class="actions"><a class="button secondary" href="/admin/productos/${p.id}/editar">Editar</a><a class="button danger" href="/admin/productos/${p.id}/eliminar">Eliminar</a></div></td></tr>`).join('')}</tbody></table></div>`:'<section class="empty"><h2>Tu catálogo está vacío</h2><p>Usa Crear producto para comenzar.</p></section>'}`,true);}
function productForm(product,token,error='',values){const editing=Boolean(product);const value=key=>escape(values?.get(key)??(key==='price'?product?(product.priceCents/100).toFixed(2):'':product?.[key]??''));const action=editing?`/admin/productos/${product.id}/editar`:'/admin/productos/nuevo';return layout(editing?'Editar producto':'Crear producto',`<a class="back" href="/admin">← Volver a productos</a><section class="editor"><p class="eyebrow">CATÁLOGO</p><h1>${editing?'Editar':'Crear'} producto</h1><p class="muted">Usa el mismo nombre, marca y capacidad que el producto de Plaza Vea para revisar su equivalencia.</p>${error?`<p class="error" role="alert">${escape(error)}</p>`:''}<form action="${action}" method="post"><input type="hidden" name="_csrf" value="${token}"><label>Nombre del producto<input name="name" required maxlength="160" value="${value('name')}" placeholder="Agua CIELO Sin Gas Bidón 7L"></label><div class="form-grid"><label>Marca<input name="brand" required maxlength="80" value="${value('brand')}" placeholder="CIELO"></label><label>Presentación<input name="presentation" required maxlength="80" value="${value('presentation')}" placeholder="Bidón"></label><label>Capacidad<input name="capacity" required maxlength="80" value="${value('capacity')}" placeholder="7 L"></label><label>Precio en soles<input name="price" type="text" inputmode="decimal" required maxlength="11" value="${value('price')}" placeholder="4.90"></label></div><div class="actions"><button class="primary" type="submit">Guardar producto</button><a class="button secondary" href="/admin">Cancelar</a></div></form></section>`,true);}
function deleteForm(product,token){return layout('Eliminar producto',`<section class="editor"><p class="eyebrow">ELIMINAR</p><h1>Eliminar producto</h1><p>Se eliminará <strong>${escape(product.name)}</strong> del catálogo de esta tienda.</p><p class="muted">Las capturas que ya guardaste en el monitor se conservan en su historial.</p><form method="post" action="/admin/productos/${product.id}/eliminar"><input type="hidden" name="_csrf" value="${token}"><div class="actions"><button class="danger" type="submit">Eliminar producto</button><a class="button secondary" href="/admin">Cancelar</a></div></form></section>`,true);}
async function formBody(request){let raw='';for await(const chunk of request){raw+=chunk;if(Buffer.byteLength(raw)>32768){const error=new Error('El formulario es demasiado grande.');error.status=413;throw error;}}return new URLSearchParams(raw);}

export function createStoreServer({dataFile=path.join(root,'data','productos.json'),withExample=true}={}){
 let initial=readProducts(dataFile);if(initial===null)saveProducts(dataFile,withExample?seed():[]);
 const token=randomBytes(24).toString('hex');const stylesheet=fs.readFileSync(path.join(root,'style.css'),'utf8');
 const server=http.createServer(async(request,response)=>{
  const port=server.address().port;const hosts=new Set([`127.0.0.1:${port}`,`localhost:${port}`]);
  const send=(status,body,type='text/html; charset=utf-8')=>{response.writeHead(status,{'Content-Type':type,'X-Content-Type-Options':'nosniff','Cache-Control':'no-store','Content-Security-Policy':"default-src 'self'; style-src 'self'; script-src 'none'; img-src 'self' data:; form-action 'self'; base-uri 'none'; frame-ancestors 'none'"});response.end(body);};
  const redirect=location=>{response.writeHead(303,{Location:location,'Cache-Control':'no-store'});response.end();};
  try{
   if(!hosts.has(request.headers.host)){send(400,'Host no permitido.','text/plain; charset=utf-8');return;}
   const origin=`http://${request.headers.host}`;const url=new URL(request.url,origin);const pathname=url.pathname;
   if(!['GET','POST'].includes(request.method)){send(405,'Método no permitido.','text/plain; charset=utf-8');return;}
   if(pathname==='/style.css'&&request.method==='GET'){send(200,stylesheet,'text/css; charset=utf-8');return;}
   const products=readProducts(dataFile);if(products===null)throw new Error('No se encontró el archivo de productos.');
   if(request.method==='GET'){
    if(pathname==='/'||pathname==='/buscar'){send(200,storefront(products,url));return;}
    if(pathname==='/admin'){send(200,adminList(products,url));return;}
    if(pathname==='/admin/productos/nuevo'){send(200,productForm(null,token));return;}
    const match=pathname.match(/^\/(?:producto\/([a-f0-9-]+)|admin\/productos\/([a-f0-9-]+)\/(editar|eliminar))$/);
    if(match){const product=products.find(p=>p.id===(match[1]??match[2]));if(product){send(200,match[1]?detail(product,origin):match[3]==='editar'?productForm(product,token):deleteForm(product,token));return;}}
   }else{
    if(!request.headers['content-type']?.startsWith('application/x-www-form-urlencoded')){send(415,'Formato de formulario no permitido.','text/plain; charset=utf-8');return;}
    if(request.headers.origin&&request.headers.origin!==origin){send(403,'Origen no permitido.','text/plain; charset=utf-8');return;}
    const form=await formBody(request);const supplied=Buffer.from(form.get('_csrf')??'');const expected=Buffer.from(token);
    if(supplied.length!==expected.length||!timingSafeEqual(supplied,expected)){send(403,'Recarga el formulario e inténtalo otra vez.','text/plain; charset=utf-8');return;}
    if(pathname==='/admin/productos/nuevo'){
     try{const product=productFrom(form,randomUUID());products.push(product);saveProducts(dataFile,products);redirect('/admin?ok=creado');}catch(error){send(422,productForm(null,token,error.message,form));}return;
    }
    const match=pathname.match(/^\/admin\/productos\/([a-f0-9-]+)\/(editar|eliminar)$/);
    if(match){const index=products.findIndex(p=>p.id===match[1]);if(index>=0){
     if(match[2]==='eliminar'){products.splice(index,1);saveProducts(dataFile,products);redirect('/admin?ok=eliminado');return;}
     try{products[index]=productFrom(form,match[1]);saveProducts(dataFile,products);redirect('/admin?ok=actualizado');}catch(error){send(422,productForm(products[index],token,error.message,form));}return;
    }}
   }
   send(404,layout('No encontrado','<section class="empty"><h1>Producto o página no encontrada</h1><a class="button secondary" href="/">Volver al catálogo</a></section>'));
  }catch(error){console.error('Error de tienda:',error.name);if(!response.headersSent)send(error.status??500,layout('Error','<section class="empty"><h1>No se pudo completar la operación</h1><p>Los productos anteriores se conservaron. Revisa el archivo de datos y el registro local.</p></section>'));else response.end();}
 });
 return server;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href){
 const port=Number(process.env.TIENDA_PORT??8877);if(!Number.isInteger(port)||port<0||port>65535)throw new Error('TIENDA_PORT inválido.');
 const server=createStoreServer({dataFile:process.env.TIENDA_DATA_FILE??path.join(root,'data','productos.json'),withExample:process.env.TIENDA_EMPTY!=='true'});
 server.on('error',error=>{console.error(error.code==='EADDRINUSE'?'El puerto de la tienda ya está ocupado.':error.message);process.exitCode=1;});
 server.listen(port,'127.0.0.1',()=>console.log(`Tienda de pruebas: http://127.0.0.1:${server.address().port}`));
}
