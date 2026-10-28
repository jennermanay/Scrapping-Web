import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createStoreServer,parsePrice} from './server.mjs';

test('HTTP CRUD persists products and serves searchable HTML with a single PEN offer',async()=>{
 const folder=fs.mkdtempSync(path.join(os.tmpdir(),'tienda-test-'));const file=path.join(folder,'productos.json');const server=createStoreServer({dataFile:file,withExample:false});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin=`http://127.0.0.1:${server.address().port}`;
 try{
  let response=await fetch(origin+'/admin/productos/nuevo');let html=await response.text();const token=html.match(/name="_csrf" value="([a-f0-9]+)"/)[1];
  const post=(url,values)=>fetch(origin+url,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({_csrf:token,...values}),redirect:'manual'});
  const product={name:'Agua CIELO Sin Gas Bidón 7L',brand:'CIELO',presentation:'Bidón',capacity:'7 L',price:'4.90'};
  response=await post('/admin/productos/nuevo',product);assert.equal(response.status,303);let saved=JSON.parse(fs.readFileSync(file));assert.equal(saved[0].priceCents,490);const id=saved[0].id;
  html=await (await fetch(origin+'/buscar?q=Agua+CIELO+7L')).text();assert.ok(html.includes('/producto/'+id));assert.ok(html.includes('method="get"'));assert.ok(!html.includes('application/ld+json'));
  html=await (await fetch(origin+'/producto/'+id)).text();let structured=JSON.parse(html.match(/<script type="application\/ld\+json">(.*?)<\/script>/s)[1]);assert.equal(structured.offers.price,'4.90');assert.equal(structured.offers.priceCurrency,'PEN');assert.equal(structured.name,product.name);
  response=await post(`/admin/productos/${id}/editar`,{...product,price:'6,90'});assert.equal(response.status,303);assert.equal(JSON.parse(fs.readFileSync(file))[0].priceCents,690);
  response=await post(`/admin/productos/${id}/editar`,{...product,price:'0'});assert.equal(response.status,422);assert.equal(JSON.parse(fs.readFileSync(file))[0].priceCents,690);
  response=await fetch(origin+`/admin/productos/${id}/eliminar`);assert.equal(response.status,200);assert.equal(JSON.parse(fs.readFileSync(file)).length,1);
  response=await post(`/admin/productos/${id}/eliminar`,{});assert.equal(response.status,303);assert.equal((await fetch(origin+'/producto/'+id)).status,404);assert.equal(JSON.parse(fs.readFileSync(file)).length,0);
  response=await post('/admin/productos/nuevo',{...product,name:'<script>alert(1)</script>',brand:'A </script> marca'});assert.equal(response.status,303);saved=JSON.parse(fs.readFileSync(file));html=await (await fetch(origin+'/producto/'+saved[0].id)).text();assert.ok(html.includes('&lt;script&gt;'));assert.ok(!html.includes('<script>alert(1)'));assert.equal(JSON.parse(html.match(/<script type="application\/ld\+json">(.*?)<\/script>/s)[1]).brand.name,'A </script> marca');
  const forged=await fetch(origin+'/admin/productos/nuevo',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(product)});assert.equal(forged.status,403);
  const crossOrigin=await fetch(origin+'/admin/productos/nuevo',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded',Origin:'https://example.com'},body:new URLSearchParams({_csrf:token,...product})});assert.equal(crossOrigin.status,403);
 }finally{await new Promise(resolve=>server.close(resolve));if(fs.existsSync(file))fs.unlinkSync(file);fs.rmdirSync(folder);}
});
test('prices reject negatives, exponents, zero and excess decimals',()=>{assert.equal(parsePrice('4,90'),490);for(const value of ['0','-1','NaN','1e2','4.999','S/ 5'])assert.throws(()=>parsePrice(value));});
test('corrupt files are preserved without reseeding',()=>{const folder=fs.mkdtempSync(path.join(os.tmpdir(),'tienda-test-'));const file=path.join(folder,'productos.json');fs.writeFileSync(file,'malformed');try{assert.throws(()=>createStoreServer({dataFile:file}));assert.equal(fs.readFileSync(file,'utf8'),'malformed');}finally{fs.unlinkSync(file);fs.rmdirSync(folder);}});
